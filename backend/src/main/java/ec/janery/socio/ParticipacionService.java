package ec.janery.socio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.caja.CajaService;
import ec.janery.caja.MovimientoCaja;
import ec.janery.common.NegocioException;
import ec.janery.credito.CreditoService;
import ec.janery.cuenta.CuentaPrincipal;
import ec.janery.cuenta.CuentaService;
import ec.janery.cuenta.RestriccionCierre;
import ec.janery.parametros.ParametroService;
import ec.janery.persona.PersonaService;
import ec.janery.socio.Entidades.AporteCajaChica;
import ec.janery.socio.Entidades.AporteSocio;
import ec.janery.socio.Entidades.AutorizacionRetiro;
import ec.janery.socio.Entidades.CierreAnual;
import ec.janery.socio.Entidades.CompraventaCapital;
import ec.janery.socio.Entidades.Resultado;
import ec.janery.socio.Entidades.VariacionParticipacion;
import lombok.RequiredArgsConstructor;

/**
 * Aportes, participación, resultados y cierre anual. Las fórmulas están en docs/reglas-negocio.md, sección 11.
 * La caja principal solo cambia si el dinero cambia de fondo: un aporte al capital sigue en ella.
 */
@Service
@RequiredArgsConstructor
public class ParticipacionService {

    interface AporteRepository extends JpaRepository<AporteSocio, Long> {
        boolean existsBySocioIdAndTipoAndPeriodo(Long socioId, String tipo, String periodo);

        List<AporteSocio> findBySocioIdOrderByIdDesc(Long socioId);

        @Query("select coalesce(sum(a.importe), 0) from AporteSocio a where a.socioId = :socioId and a.tipo = :tipo")
        BigDecimal total(@Param("socioId") Long socioId, @Param("tipo") String tipo);

        @Query("select a.periodo from AporteSocio a where a.socioId = :socioId and a.tipo = 'OBLIGATORIO'")
        Set<String> periodosObligatorios(@Param("socioId") Long socioId);

        List<AporteSocio> findBySocioIdIn(java.util.Collection<Long> socioIds);
    }

    interface CompraventaRepository extends JpaRepository<CompraventaCapital, Long> {
    }

    interface AutorizacionRepository extends JpaRepository<AutorizacionRetiro, Long> {
        Optional<AutorizacionRetiro> findBySocioIdAndAnio(Long socioId, Integer anio);

        @Query("select a.socioId from AutorizacionRetiro a where a.anio = :anio and a.socioId in :socioIds")
        Set<Long> autorizados(@Param("socioIds") java.util.Collection<Long> socioIds, @Param("anio") Integer anio);
    }

    interface AporteCajaChicaRepository extends JpaRepository<AporteCajaChica, Long> {
        boolean existsBySocioIdAndPeriodo(Long socioId, String periodo);

        List<AporteCajaChica> findBySocioIdOrderByIdDesc(Long socioId);

        @Query("select coalesce(sum(a.importe), 0) from AporteCajaChica a where a.socioId = :socioId")
        BigDecimal total(@Param("socioId") Long socioId);

        @Query("select a.periodo from AporteCajaChica a where a.socioId = :socioId")
        Set<String> periodos(@Param("socioId") Long socioId);

        List<AporteCajaChica> findBySocioIdIn(java.util.Collection<Long> socioIds);
    }

    interface ResultadoRepository extends JpaRepository<Resultado, Long> {
        @Query("""
                select coalesce(sum(case when r.tipo = 'GANANCIA' then r.importe else -r.importe end), 0)
                from Resultado r where r.fecha >= :desde and r.fecha <= :hasta
                """)
        BigDecimal neto(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

        @Query("select coalesce(sum(r.reserva), 0) from Resultado r where r.fecha >= :desde and r.fecha <= :hasta")
        BigDecimal reserva(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

        /** Lo registrado de ese tipo, sin la reserva, menos lo que ya llegó a los fondos de los socios. */
        @Query(value = """
                SELECT (SELECT COALESCE(SUM(importe - reserva), 0) FROM resultados WHERE tipo = :tipo)
                     - (SELECT COALESCE(ABS(SUM(v.importe)), 0) FROM variaciones_participacion v
                        JOIN resultados r ON r.id = v.resultado_id WHERE r.tipo = :tipo)
                """, nativeQuery = true)
        BigDecimal enEspera(@Param("tipo") String tipo);
    }

    interface CierreRepository extends JpaRepository<CierreAnual, Long> {
        boolean existsByAnio(Integer anio);
    }

    interface VariacionRepository extends JpaRepository<VariacionParticipacion, Long> {
        List<VariacionParticipacion> findBySocioIdOrderByFechaAscIdAsc(Long socioId);

        @Query("select coalesce(sum(v.importe), 0) from VariacionParticipacion v where v.socioId = :socioId")
        BigDecimal valor(@Param("socioId") Long socioId);

        @Query("select v.socioId, coalesce(sum(v.importe), 0) from VariacionParticipacion v group by v.socioId")
        List<Object[]> valores();

        /**
         * Para cada socio, en este orden: valor, compraventas, resultados, ganancias liberadas, resultados del mes y base
         * de reparto. Son las mismas sumas que valor(), compraventas(), resultados(), gananciasLiberadas(),
         * resultadosDelMes() y baseDeReparto(), de todos los socios en una consulta.
         */
        @Query(value = """
                SELECT socio_id,
                       COALESCE(SUM(importe), 0),
                       COALESCE(SUM(importe) FILTER (WHERE tipo IN ('APORTE_COMPRA', 'APORTE_VENTA')), 0),
                       COALESCE(SUM(importe) FILTER (WHERE tipo NOT LIKE 'APORTE%'), 0),
                       COALESCE(SUM(importe) FILTER (WHERE tipo NOT LIKE 'APORTE%'
                           AND (tipo IN ('RETIRO_GANANCIA', 'DESCUENTO_OBLIGACION') OR fecha < :inicioDeAnio)), 0),
                       COALESCE(SUM(importe) FILTER (WHERE tipo IN ('GANANCIA', 'PERDIDA') AND fecha >= :inicioDeMes), 0),
                       COALESCE(SUM(importe) FILTER (WHERE tipo NOT IN ('GANANCIA', 'PERDIDA') OR fecha < :inicioDeMes), 0)
                FROM variaciones_participacion WHERE socio_id IN (:socioIds) GROUP BY socio_id
                """, nativeQuery = true)
        List<Object[]> cifras(@Param("socioIds") java.util.Collection<Long> socioIds,
                @Param("inicioDeAnio") LocalDate inicioDeAnio, @Param("inicioDeMes") LocalDate inicioDeMes);

        /** Por socio: todo menos las ganancias y pérdidas del mes en curso, que aún no se han cerrado. */
        @Query("""
                select v.socioId, coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.tipo not in ('GANANCIA', 'PERDIDA') or v.fecha < :inicioDeMes group by v.socioId
                """)
        List<Object[]> basesDeReparto(@Param("inicioDeMes") LocalDate inicioDeMes);

        @Query("""
                select coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.socioId = :socioId and v.tipo not in ('GANANCIA', 'PERDIDA') or v.socioId = :socioId and v.fecha < :inicioDeMes
                """)
        BigDecimal baseDeReparto(@Param("socioId") Long socioId, @Param("inicioDeMes") LocalDate inicioDeMes);

        @Query("""
                select coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.socioId = :socioId and v.tipo in ('GANANCIA', 'PERDIDA') and v.fecha >= :inicioDeMes
                """)
        BigDecimal resultadosDelMes(@Param("socioId") Long socioId, @Param("inicioDeMes") LocalDate inicioDeMes);

        /** Lo que no es aporte: ganancias menos pérdidas. */
        @Query("""
                select coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.socioId = :socioId and v.tipo not like 'APORTE%'
                """)
        BigDecimal resultados(@Param("socioId") Long socioId);

        /** Ganancias de años anteriores, menos todo lo ya retirado o descontado. */
        @Query("""
                select coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.socioId = :socioId and v.tipo not like 'APORTE%'
                and (v.tipo in ('RETIRO_GANANCIA', 'DESCUENTO_OBLIGACION') or v.fecha < :inicioDeAnio)
                """)
        BigDecimal gananciasLiberadas(@Param("socioId") Long socioId, @Param("inicioDeAnio") LocalDate inicioDeAnio);

        /** Lo que representa capital: aportes, más compras, menos ventas. */
        @Query("""
                select coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.socioId = :socioId and v.tipo like 'APORTE%'
                """)
        BigDecimal capital(@Param("socioId") Long socioId);

        @Query("""
                select coalesce(sum(v.importe), 0) from VariacionParticipacion v
                where v.socioId = :socioId and v.tipo in ('APORTE_COMPRA', 'APORTE_VENTA')
                """)
        BigDecimal compraventas(@Param("socioId") Long socioId);
    }

    /** Una línea del menú de obligaciones. cuotaId solo en cuotas; periodo solo en aportes. */
    public record Obligacion(String tipo, String periodo, Long cuotaId, String descripcion, BigDecimal importe) {
    }

    public record MenuObligaciones(Long socioId, String nombre, BigDecimal gananciasDisponibles, boolean retiroAutorizado,
            List<Obligacion> obligaciones, BigDecimal total) {
    }

    /** Lo que el presidente elige descontar. El importe solo aplica a cuotas: vacío, la cuota completa. */
    public record Descuento(String tipo, String periodo, Long cuotaId, BigDecimal importe) {
    }

    public record AporteVista(Long id, String tipo, BigDecimal importe, String periodo, Instant fecha) {
    }

    /** valorActual es el fondo personal: la suma de los tres aportes y las ganancias netas. Caja chica va aparte. */
    public record ResumenSocio(Long socioId, String nombre, Instant desde, BigDecimal totalInicial,
            BigDecimal totalObligatorio, BigDecimal totalPersonal, BigDecimal compraventas, BigDecimal ganancias,
            BigDecimal gananciasDisponibles, BigDecimal obligacionesPendientes, boolean retiroAutorizado,
            BigDecimal totalCajaChica,
            BigDecimal valorActual, BigDecimal porcentaje,
            BigDecimal aporteObligatorio, BigDecimal aporteCajaChica, String periodoActual,
            boolean obligatorioDelMesPagado, boolean cajaChicaDelMesPagado, BigDecimal rendimientoMes, BigDecimal variacionMes) {
    }

    public record VariacionVista(String tipo, BigDecimal importe, LocalDate fecha) {
    }

    public record PeriodoVista(String periodo, BigDecimal valorInicial, List<VariacionVista> variaciones,
            BigDecimal valorFinal, BigDecimal rendimiento) {
    }

    public record ResultadoVista(Long id, String tipo, BigDecimal importe, BigDecimal reserva, String concepto,
            LocalDate fecha) {
    }

    public record EnEsperaVista(BigDecimal ganancia, BigDecimal perdida) {
    }

    public record CierreVista(Integer anio, BigDecimal resultadoNeto, BigDecimal porcentajeReserva, BigDecimal reserva,
            BigDecimal distribuido, Instant fecha) {
    }

    private static final String INICIAL = "INICIAL";
    private static final String OBLIGATORIO = "OBLIGATORIO";
    private static final String PERSONAL = "PERSONAL";
    private static final String GANANCIA = "GANANCIA";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";
    private static final String MODULO = "SOCIO";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Solo las pruebas lo apagan, para calcular sus cifras sin depender de los demás socios del esquema. */
    @org.springframework.beans.factory.annotation.Value("${janery.reparto-automatico:true}")
    private boolean repartoAutomatico;

    private final AporteRepository aportes;
    private final AporteCajaChicaRepository aportesCajaChica;
    private final ResultadoRepository resultados;
    private final CierreRepository cierres;
    private final VariacionRepository variaciones;
    private final SocioService socios;
    private final CuentaService cuentas;
    private final CajaService cajas;
    private final CreditoService creditos;
    private final CompraventaRepository compraventas;
    private final AutorizacionRepository autorizaciones;
    private final PersonaService personas;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    // --- Aportes

    /** Obligatorio: importe del parámetro, una vez por mes. Personal: importe libre. Se paga desde la cuenta principal. */
    @Transactional
    public Long aportar(Long usuarioId, String tipo, BigDecimal importeSolicitado, String periodoTexto, String clave) {
        SocioService.Socio socio = socios.deUsuario(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(socio.getPersonaId());
        Optional<Long> previa = cuentas.operacionPrevia(clave, "APORTE_CAPITAL", null, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        String periodo = periodo(periodoTexto);
        BigDecimal importe;
        if (OBLIGATORIO.equals(tipo)) {
            if (aportes.existsBySocioIdAndTipoAndPeriodo(socio.getId(), OBLIGATORIO, periodo)) {
                throw NegocioException.conflicto("APORTE_YA_REALIZADO", "El aporte obligatorio de " + periodo + " ya está pagado");
            }
            importe = dinero(parametros.valorVigente("APORTE_OBLIGATORIO"));
        } else {
            if (importeSolicitado == null) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "IMPORTE_REQUERIDO", "Indique el importe del aporte");
            }
            importe = importeSolicitado;
        }
        Instant ahora = Instant.now(reloj);
        Long operacion = cuentas.crearOperacionInterna("APORTE_CAPITAL", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "APORTE_" + tipo);
        AporteSocio aporte = aportes.save(new AporteSocio(null, socio.getId(), tipo, importe, periodo, operacion, ahora));
        variaciones.save(new VariacionParticipacion(null, socio.getId(), "APORTE_" + tipo, importe, hoy(), aporte.getId(),
                null, null, ahora));
        auditoria.registrar(usuarioId, "APORTE_" + tipo, MODULO, "socio", socio.getId(), null, importe.toPlainString(), periodo);
        return operacion;
    }

    /** Capital con el que entra el socio. El dinero se recibe fuera del sistema y entra a la caja principal. */
    @Transactional
    public Long registrarAporteInicial(Long actorId, Long socioId, BigDecimal importe, String metodo, String referencia,
            String clave) {
        socios.personaDe(socioId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "APORTE_INICIAL", importe, actorId);
        if (previa.isPresent()) {
            return previa.get();
        }
        Instant ahora = Instant.now(reloj);
        Long operacion = cuentas.crearOperacionExterna("APORTE_INICIAL", importe, metodo, referencia, clave, actorId);
        cajas.registrar(CajaService.PRINCIPAL, operacion, ENTRADA, importe, "APORTE_INICIAL", null);
        AporteSocio aporte = aportes.save(new AporteSocio(null, socioId, INICIAL, importe, YearMonth.now(reloj).toString(),
                operacion, ahora));
        variaciones.save(new VariacionParticipacion(null, socioId, "APORTE_" + INICIAL, importe, hoy(), aporte.getId(),
                null, null, ahora));
        auditoria.registrar(actorId, "APORTE_INICIAL", MODULO, "socio", socioId, null, importe.toPlainString(), null);
        return operacion;
    }

    /** El dinero pasa de la caja principal a caja chica. No aumenta la participación. */
    @Transactional
    public Long aportarCajaChica(Long usuarioId, String periodoTexto, String clave) {
        SocioService.Socio socio = socios.deUsuario(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(socio.getPersonaId());
        Optional<Long> previa = cuentas.operacionPrevia(clave, "APORTE_CAJA_CHICA", null, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        String periodo = periodo(periodoTexto);
        if (aportesCajaChica.existsBySocioIdAndPeriodo(socio.getId(), periodo)) {
            throw NegocioException.conflicto("APORTE_YA_REALIZADO", "El aporte a caja chica de " + periodo + " ya está pagado");
        }
        BigDecimal importe = dinero(parametros.valorVigente("APORTE_CAJA_CHICA"));
        Long operacion = cuentas.crearOperacionInterna("APORTE_CAJA_CHICA", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "APORTE_CAJA_CHICA");
        cajas.registrar(CajaService.PRINCIPAL, operacion, SALIDA, importe, "TRASPASO_A_CAJA_CHICA", null);
        cajas.registrar(CajaService.CHICA, operacion, ENTRADA, importe, "APORTE_SOCIO", periodo);
        aportesCajaChica.save(new AporteCajaChica(null, socio.getId(), importe, periodo, operacion, Instant.now(reloj)));
        auditoria.registrar(usuarioId, "APORTE_CAJA_CHICA", MODULO, "socio", socio.getId(), null, importe.toPlainString(), periodo);
        return operacion;
    }

    /** Paga varios meses de una vez, todos o ninguno. Cada mes usa su propia clave, derivada de la del pago. */
    @Transactional
    public List<Long> pagarPeriodos(Long usuarioId, String tipo, List<String> periodos, String clave) {
        List<String> meses = validarPeriodos(periodos);
        return meses.stream().map(p -> OBLIGATORIO.equals(tipo)
                ? aportar(usuarioId, OBLIGATORIO, null, p, clave + ":" + p)
                : aportarCajaChica(usuarioId, p, clave + ":" + p)).toList();
    }

    private List<String> validarPeriodos(List<String> periodos) {
        List<String> meses = periodos == null ? List.of() : periodos.stream().map(this::periodo).distinct().sorted().toList();
        if (meses.isEmpty() || meses.size() > 12) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODOS_INVALIDOS", "Elija entre 1 y 12 meses");
        }
        return meses;
    }

    public record MesAporte(String periodo, boolean obligatorioPagado, boolean cajaChicaPagado) {
    }

    /** Los doce meses del año con lo ya pagado: así se eligen los que faltan. */
    @Transactional(readOnly = true)
    public List<MesAporte> calendario(Long socioId) {
        return calendario(aportes.periodosObligatorios(socioId), aportesCajaChica.periodos(socioId));
    }

    private List<MesAporte> calendario(Set<String> obligatorios, Set<String> caja) {
        int anio = YearMonth.now(reloj).getYear();
        return java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(m -> YearMonth.of(anio, m).toString())
                .map(p -> new MesAporte(p, obligatorios.contains(p), caja.contains(p))).toList();
    }

    @Transactional(readOnly = true)
    public List<MesAporte> miCalendario(Long usuarioId) {
        return calendario(socios.deUsuario(usuarioId).getId());
    }

    public record SocioCajaChica(Long socioId, String nombre, List<MesAporte> meses) {
    }

    @Transactional(readOnly = true)
    public List<SocioCajaChica> sociosCajaChica() {
        List<SocioService.Socio> activos = socios.activos();
        if (activos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = activos.stream().map(SocioService.Socio::getId).toList();
        Map<Long, Set<String>> obligatorios = aportes.findBySocioIdIn(ids).stream().filter(a -> OBLIGATORIO.equals(a.getTipo()))
                .collect(Collectors.groupingBy(AporteSocio::getSocioId, Collectors.mapping(AporteSocio::getPeriodo, Collectors.toSet())));
        Map<Long, Set<String>> caja = aportesCajaChica.findBySocioIdIn(ids).stream()
                .collect(Collectors.groupingBy(AporteCajaChica::getSocioId, Collectors.mapping(AporteCajaChica::getPeriodo, Collectors.toSet())));
        personas.precargar(activos.stream().map(SocioService.Socio::getPersonaId).toList());
        return activos.stream().map(s -> new SocioCajaChica(s.getId(), personas.nombreCompleto(s.getPersonaId()),
                calendario(obligatorios.getOrDefault(s.getId(), Set.of()), caja.getOrDefault(s.getId(), Set.of())))).toList();
    }

    /**
     * Quien administra caja chica recibe en persona el aporte de un socio: el dinero entra directo a caja chica, sin pasar
     * por la cuenta del socio ni por la caja principal.
     */
    @Transactional
    public List<Long> recibirAporteCajaChica(Long actorId, Long socioId, List<String> periodos, String clave) {
        SocioService.Socio socio = socios.porId(socioId);
        if (!"ACTIVO".equals(socio.getEstado())) {
            throw NegocioException.conflicto("SOCIO_INACTIVO", "Ese socio ya no está activo");
        }
        String nombre = personas.nombreCompleto(socio.getPersonaId());
        BigDecimal importe = dinero(parametros.valorVigente("APORTE_CAJA_CHICA"));
        List<Long> operaciones = new java.util.ArrayList<>();
        for (String periodo : validarPeriodos(periodos)) {
            String claveMes = clave + ":" + periodo;
            Optional<Long> previa = cuentas.operacionPrevia(claveMes, "APORTE_CAJA_CHICA", importe, actorId);
            if (previa.isPresent()) {
                operaciones.add(previa.get());
                continue;
            }
            if (aportesCajaChica.existsBySocioIdAndPeriodo(socioId, periodo)) {
                throw NegocioException.conflicto("APORTE_YA_REALIZADO", nombre + " ya pagó caja chica de " + periodo);
            }
            Long operacion = cuentas.crearOperacionExterna("APORTE_CAJA_CHICA", importe, "EFECTIVO", null, claveMes, actorId);
            cajas.registrar(CajaService.CHICA, operacion, ENTRADA, importe, "APORTE_SOCIO", periodo + " · " + nombre);
            aportesCajaChica.save(new AporteCajaChica(null, socioId, importe, periodo, operacion, Instant.now(reloj)));
            auditoria.registrar(actorId, "APORTE_CAJA_CHICA_RECIBIDO", MODULO, "socio", socioId, null, importe.toPlainString(), periodo);
            operaciones.add(operacion);
        }
        return operaciones;
    }

    public record Condiciones(BigDecimal aporteInicial, BigDecimal aporteObligatorio, BigDecimal aporteCajaChica,
            BigDecimal cupoEmergente) {
    }

    @Transactional(readOnly = true)
    public Condiciones condiciones() {
        Map<String, BigDecimal> v = parametros.valoresVigentes(List.of("APORTE_INICIAL_MINIMO", "APORTE_OBLIGATORIO",
                "APORTE_CAJA_CHICA", "EMERGENTE_SOCIO_CUPO"));
        return new Condiciones(dinero(v.get("APORTE_INICIAL_MINIMO")), dinero(v.get("APORTE_OBLIGATORIO")),
                dinero(v.get("APORTE_CAJA_CHICA")), dinero(v.get("EMERGENTE_SOCIO_CUPO")));
    }

    /**
     * Un cliente se hace socio pagando el aporte inicial desde su cuenta principal. Ese capital no se retira: solo se
     * vende a otro socio.
     */
    @Transactional
    public Long hacerseSocio(Long usuarioId, String clave) {
        Long personaId = personas.dePorUsuario(usuarioId)
                .orElseThrow(() -> NegocioException.conflicto("PERFIL_INCOMPLETO", "Complete sus datos personales")).getId();
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "APORTE_INICIAL", null, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        if (socios.esSocio(personaId)) {
            throw NegocioException.conflicto("SOCIO_EXISTENTE", "Usted ya es socio");
        }
        BigDecimal importe = dinero(parametros.valorVigente("APORTE_INICIAL_MINIMO"));
        Long operacion = cuentas.crearOperacionInterna("APORTE_INICIAL", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "APORTE_INICIAL");
        socios.registrar(usuarioId, usuarioId);
        SocioService.Socio socio = socios.deUsuario(usuarioId);
        Instant ahora = Instant.now(reloj);
        AporteSocio aporte = aportes.save(new AporteSocio(null, socio.getId(), INICIAL, importe, YearMonth.now(reloj).toString(),
                operacion, ahora));
        variaciones.save(new VariacionParticipacion(null, socio.getId(), "APORTE_" + INICIAL, importe, hoy(), aporte.getId(),
                null, null, ahora));
        auditoria.registrar(usuarioId, "APORTE_INICIAL", MODULO, "socio", socio.getId(), null, importe.toPlainString(), null);
        return operacion;
    }

    @Transactional
    public Long registrarGastoCajaChica(Long actorId, BigDecimal importe, String detalle, String clave) {
        Optional<Long> previa = cuentas.operacionPrevia(clave, "GASTO_CAJA_CHICA", importe, actorId);
        if (previa.isPresent()) {
            return previa.get();
        }
        if (importe.compareTo(cajas.saldoBloqueado(CajaService.CHICA)) > 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "Caja chica no tiene saldo suficiente");
        }
        Long operacion = cuentas.crearOperacionInterna("GASTO_CAJA_CHICA", importe, clave, actorId);
        cajas.registrar(CajaService.CHICA, operacion, SALIDA, importe, "GASTO", detalle);
        auditoria.registrar(actorId, "GASTO_CAJA_CHICA", "CAJA", "caja", CajaService.CHICA, null, importe.toPlainString(), detalle);
        return operacion;
    }

    @Transactional(readOnly = true)
    public BigDecimal saldoCajaChica() {
        return cajas.saldo(CajaService.CHICA);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoCaja> movimientosCajaChica(int pagina, int tamano) {
        return cajas.movimientos(CajaService.CHICA, pagina, tamano);
    }

    // --- Consultas del socio

    @Transactional(readOnly = true)
    public ResumenSocio miResumen(Long usuarioId) {
        return resumenes(List.of(socios.deUsuario(usuarioId))).get(0);
    }

    @Transactional(readOnly = true)
    public List<ResumenSocio> listarSocios() {
        return resumenes(socios.activos());
    }

    @Transactional(readOnly = true)
    public List<AporteVista> misAportes(Long usuarioId) {
        Long socioId = socios.deUsuario(usuarioId).getId();
        List<AporteVista> lista = new ArrayList<>();
        aportes.findBySocioIdOrderByIdDesc(socioId).forEach(a -> lista.add(
                new AporteVista(a.getId(), a.getTipo(), a.getImporte(), a.getPeriodo(), a.getCreadoEn())));
        aportesCajaChica.findBySocioIdOrderByIdDesc(socioId).forEach(a -> lista.add(
                new AporteVista(a.getId(), "CAJA_CHICA", a.getImporte(), a.getPeriodo(), a.getCreadoEn())));
        lista.sort(Comparator.comparing(AporteVista::fecha).reversed());
        return lista;
    }

    /** Un periodo por mes, desde el primer movimiento hasta el mes actual, aunque no tenga variaciones. Reciente primero. */
    @Transactional(readOnly = true)
    public List<PeriodoVista> miEvolucion(Long usuarioId) {
        List<VariacionParticipacion> todas =
                variaciones.findBySocioIdOrderByFechaAscIdAsc(socios.deUsuario(usuarioId).getId());
        YearMonth actual = YearMonth.now(reloj);
        YearMonth primero = todas.isEmpty() ? actual : YearMonth.from(todas.get(0).getFecha());
        List<PeriodoVista> periodos = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO.setScale(2);
        for (YearMonth mes = primero; !mes.isAfter(actual); mes = mes.plusMonths(1)) {
            YearMonth esteMes = mes;
            List<VariacionVista> delMes = todas.stream().filter(v -> YearMonth.from(v.getFecha()).equals(esteMes))
                    .map(v -> new VariacionVista(v.getTipo(), v.getImporte(), v.getFecha())).toList();
            BigDecimal inicial = acumulado;
            acumulado = delMes.stream().map(VariacionVista::importe).reduce(inicial, BigDecimal::add);
            BigDecimal resultado = delMes.stream().filter(v -> v.tipo().equals("GANANCIA") || v.tipo().equals("PERDIDA"))
                    .map(VariacionVista::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
            periodos.add(0, new PeriodoVista(mes.toString(), inicial, delMes, acumulado,
                    rendimiento(resultado, acumulado.subtract(resultado))));
        }
        return periodos;
    }

    // --- Resultados

    /**
     * Registro a mano de lo extraordinario. Una ganancia separa al momento su reserva y reparte el resto entre los fondos
     * personales. Una pérdida se reparte igual, sin tocar la reserva.
     */
    @Transactional
    public void registrarResultado(Long actorId, String tipo, BigDecimal importe, String concepto, LocalDate fechaPedida) {
        LocalDate fecha = fechaPedida == null ? hoy() : fechaPedida;
        if (fecha.isAfter(hoy())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FECHA_FUTURA", "La fecha no puede ser futura");
        }
        if (cierres.existsByAnio(fecha.getYear())) {
            throw NegocioException.conflicto("ANIO_CERRADO", "El año " + fecha.getYear() + " ya está cerrado");
        }
        Map<Long, BigDecimal> valores = baseDeReparto();
        BigDecimal total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw NegocioException.conflicto("SIN_CAPITAL", "No hay capital de socios sobre el que repartir el resultado");
        }
        boolean ganancia = GANANCIA.equals(tipo);
        if (!ganancia && importe.compareTo(total) > 0) {
            throw NegocioException.conflicto("PERDIDA_EXCEDE_CAPITAL", "La pérdida supera el capital de los socios");
        }
        repartir(tipo, importe, concepto, fecha, actorId, valores, ganancia);
    }

    /**
     * Definido por el propietario: cada ingreso real separa su reserva y reparte el resto al momento; cada gasto baja los
     * fondos. Nunca bloquea la operación que lo origina: sin capital de socios el importe queda en el balance sin repartir.
     * Devuelve lo separado a la reserva.
     */
    @Transactional
    public BigDecimal repartirAutomatico(String tipo, BigDecimal importe, String concepto, Long actorId) {
        if (!repartoAutomatico || importe.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        Map<Long, BigDecimal> valores = baseDeReparto();
        BigDecimal total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        boolean ganancia = GANANCIA.equals(tipo);
        BigDecimal aplicable = ganancia ? importe : importe.min(total);
        return repartir(tipo, aplicable, concepto, hoy(), actorId, valores, ganancia);
    }

    /** El reverso de un ingreso: devuelve a la caja la reserva que separó y quita a los fondos el resto. */
    @Transactional
    public void revertirGanancia(BigDecimal importe, BigDecimal reserva, String concepto, Long actorId) {
        if (!repartoAutomatico) {
            return;
        }
        if (reserva.signum() > 0) {
            Long op = cuentas.crearOperacionInterna("RESERVA", reserva, null, actorId);
            cajas.registrar(CajaService.FONDO_RESERVA, op, SALIDA, reserva, "REVERSO_DE_RESERVA", concepto);
            cajas.registrar(CajaService.PRINCIPAL, op, ENTRADA, reserva, "REVERSO_DE_RESERVA", concepto);
        }
        repartirAutomatico("PERDIDA", importe.subtract(reserva), concepto, actorId);
    }

    /** El reverso de un gasto: devuelve a los fondos lo que les quitó, sin separar reserva. */
    @Transactional
    public void revertirGasto(BigDecimal importe, String concepto, Long actorId) {
        if (!repartoAutomatico || importe.signum() <= 0) {
            return;
        }
        Map<Long, BigDecimal> valores = baseDeReparto();
        if (valores.values().stream().anyMatch(v -> v.signum() > 0)) {
            repartir(GANANCIA, importe, concepto, hoy(), actorId, valores, false);
        }
    }

    /** Corre dentro de la transacción de quien lo origina: si la operación se deshace, el reparto también. */
    @org.springframework.context.event.EventListener
    public void alGenerarResultado(ec.janery.common.ResultadoGenerado evento) {
        repartirAutomatico(evento.tipo(), evento.importe(), evento.concepto(), evento.actorId());
    }

    private BigDecimal repartir(String tipo, BigDecimal importe, String concepto, LocalDate fecha, Long actorId,
            Map<Long, BigDecimal> valores, boolean separarReserva) {
        boolean ganancia = GANANCIA.equals(tipo);
        BigDecimal porcentaje = separarReserva ? parametros.valorVigente("FONDO_RESERVA") : BigDecimal.ZERO;
        BigDecimal reserva = importe.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
        // Un reparto a la vez: dos simultáneos repartirían los mismos centavos en espera.
        cajas.saldoBloqueado(CajaService.FONDO_RESERVA);
        BigDecimal aRepartir = importe.subtract(reserva).add(resultados.enEspera(tipo));
        if (!ganancia) {
            aRepartir = aRepartir.min(valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        Instant ahora = Instant.now(reloj);
        Resultado resultado = resultados.save(new Resultado(null, tipo, importe, concepto, fecha, reserva, porcentaje,
                actorId, ahora));
        if (reserva.signum() > 0) {
            Long op = cuentas.crearOperacionInterna("RESERVA", reserva, null, actorId);
            cajas.registrar(CajaService.PRINCIPAL, op, SALIDA, reserva, "TRASPASO_A_RESERVA", concepto);
            cajas.registrar(CajaService.FONDO_RESERVA, op, ENTRADA, reserva, "RESERVA_DE_GANANCIA", concepto);
        }
        RepartoProporcional.repartir(aRepartir, valores).forEach((socioId, parte) -> {
            if (parte.signum() > 0) {
                variaciones.save(new VariacionParticipacion(null, socioId, tipo, ganancia ? parte : parte.negate(), fecha,
                        null, resultado.getId(), null, ahora));
            }
        });
        auditoria.registrar(actorId, "RESULTADO_" + tipo, MODULO, "resultado", resultado.getId(), null,
                importe.toPlainString(), concepto);
        return reserva;
    }

    /** Centavos de resultados registrados que aún no se pudieron repartir entre los socios. */
    @Transactional(readOnly = true)
    public EnEsperaVista enEspera() {
        return new EnEsperaVista(resultados.enEspera(GANANCIA), resultados.enEspera("PERDIDA"));
    }

    @Transactional(readOnly = true)
    public Page<ResultadoVista> listarResultados(int pagina, int tamano) {
        return resultados.findAll(PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fecha", "id")))
                .map(r -> new ResultadoVista(r.getId(), r.getTipo(), r.getImporte(), r.getReserva(), r.getConcepto(),
                        r.getFecha()));
    }

    // --- Retiro de ganancias

    /**
     * Las ganancias de un año se liberan el 1 de enero siguiente. Solo puede retirar el socio que el presidente haya
     * autorizado ese año y que esté al día. Los aportes nunca se retiran.
     */
    @Transactional
    public Long retirarGanancias(Long usuarioId, BigDecimal importe, String clave) {
        SocioService.Socio socio = socios.deUsuario(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(socio.getPersonaId());
        Optional<Long> previa = cuentas.operacionPrevia(clave, "RETIRO_GANANCIA", importe, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        socios.bloquear(socio.getId());
        if (!retiroAutorizado(socio.getId())) {
            throw NegocioException.conflicto("RETIRO_NO_AUTORIZADO", "El presidente aún no ha autorizado su retiro de este año");
        }
        if (obligacionesPendientes(socio).signum() > 0) {
            throw NegocioException.conflicto("OBLIGACIONES_PENDIENTES", "Tiene obligaciones pendientes; póngase al día para retirar");
        }
        if (importe.compareTo(gananciasDisponibles(socio.getId())) > 0) {
            throw NegocioException.conflicto("GANANCIA_NO_DISPONIBLE", "El importe supera sus ganancias disponibles para retirar");
        }
        Long operacion = cuentas.crearOperacionInterna("RETIRO_GANANCIA", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, ENTRADA, importe, "RETIRO_GANANCIA");
        variacion(socio.getId(), "RETIRO_GANANCIA", importe.negate(), null);
        auditoria.registrar(usuarioId, "RETIRO_GANANCIA", MODULO, "socio", socio.getId(), null, importe.toPlainString(), null);
        return operacion;
    }

    /** Lo que el presidente ve para decidir: cuánto tiene el socio liberado y qué debe, línea por línea. */
    @Transactional(readOnly = true)
    public MenuObligaciones menuObligaciones(Long socioId) {
        SocioService.Socio socio = socios.porId(socioId);
        List<Obligacion> lista = obligaciones(socio);
        return new MenuObligaciones(socioId, personas.nombreCompleto(socio.getPersonaId()), gananciasDisponibles(socioId),
                retiroAutorizado(socioId), lista,
                lista.stream().map(Obligacion::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** El presidente autoriza el retiro de este año. Exige que el socio esté al día. */
    @Transactional
    public void autorizarRetiro(Long actorId, Long socioId) {
        SocioService.Socio socio = socios.bloquear(socioId);
        int anio = hoy().getYear();
        if (autorizaciones.findBySocioIdAndAnio(socioId, anio).isPresent()) {
            return;
        }
        if (obligacionesPendientes(socio).signum() > 0) {
            throw NegocioException.conflicto("OBLIGACIONES_PENDIENTES", "El socio debe estar al día para autorizar su retiro");
        }
        autorizaciones.save(new AutorizacionRetiro(null, socioId, anio, actorId, Instant.now(reloj)));
        auditoria.registrar(actorId, "RETIRO_AUTORIZADO", MODULO, "socio", socioId, null, String.valueOf(anio), null);
    }

    @Transactional
    public void revocarRetiro(Long actorId, Long socioId) {
        int anio = hoy().getYear();
        socios.bloquear(socioId);
        autorizaciones.findBySocioIdAndAnio(socioId, anio).ifPresent(a -> {
            autorizaciones.delete(a);
            auditoria.registrar(actorId, "RETIRO_REVOCADO", MODULO, "socio", socioId, String.valueOf(anio), null, null);
        });
    }

    /** Descuenta de las ganancias liberadas lo que el presidente eligió, todo o nada. La clave evita repetirlo. */
    @Transactional
    public void descontar(Long actorId, Long socioId, List<Descuento> elegidos, String clave) {
        if (cuentas.claveUsada(clave)) {
            return;
        }
        Long personaId = socios.personaDe(socioId);
        CuentaPrincipal cuenta = cuentas.bloquearSiActiva(personaId).orElse(null);
        SocioService.Socio socio = socios.bloquear(socioId);
        String claveLibre = clave;
        for (Descuento d : elegidos) {
            switch (d.tipo()) {
                case "APORTE_MENSUAL" -> descontarAporte(actorId, socio, d.periodo(), claveLibre);
                case "CAJA_CHICA" -> descontarCajaChica(actorId, socio, d.periodo(), claveLibre);
                case "CUOTA" -> descontarCuota(actorId, socio, cuenta, d, claveLibre);
                default -> throw new NegocioException(HttpStatus.BAD_REQUEST, "TIPO_INVALIDO", "Tipo de obligación no válido");
            }
            claveLibre = null;
        }
    }

    private void descontarAporte(Long actorId, SocioService.Socio socio, String periodo, String clave) {
        Long socioId = socio.getId();
        exigirVencido(mesesVencidos(socio, aportes.periodosObligatorios(socioId)), periodo);
        BigDecimal importe = dinero(parametros.valorVigente("APORTE_OBLIGATORIO"));
        exigirGanancias(socioId, importe);
        Long operacion = cuentas.crearOperacionInterna("APORTE_CAPITAL", importe, clave, actorId);
        AporteSocio aporte = aportes.save(new AporteSocio(null, socioId, OBLIGATORIO, importe, periodo, operacion, Instant.now(reloj)));
        variacion(socioId, "APORTE_OBLIGATORIO", importe, aporte.getId());
        variacion(socioId, "DESCUENTO_OBLIGACION", importe.negate(), null);
        auditoria.registrar(actorId, "DESCUENTO_APORTE_MENSUAL", MODULO, "socio", socioId, null, importe.toPlainString(), periodo);
    }

    private void descontarCajaChica(Long actorId, SocioService.Socio socio, String periodo, String clave) {
        Long socioId = socio.getId();
        exigirVencido(mesesVencidos(socio, aportesCajaChica.periodos(socioId)), periodo);
        BigDecimal importe = dinero(parametros.valorVigente("APORTE_CAJA_CHICA"));
        exigirGanancias(socioId, importe);
        Long operacion = cuentas.crearOperacionInterna("APORTE_CAJA_CHICA", importe, clave, actorId);
        cajas.registrar(CajaService.PRINCIPAL, operacion, SALIDA, importe, "TRASPASO_A_CAJA_CHICA", null);
        cajas.registrar(CajaService.CHICA, operacion, ENTRADA, importe, "APORTE_SOCIO", periodo);
        aportesCajaChica.save(new AporteCajaChica(null, socioId, importe, periodo, operacion, Instant.now(reloj)));
        variacion(socioId, "DESCUENTO_OBLIGACION", importe.negate(), null);
        auditoria.registrar(actorId, "DESCUENTO_CAJA_CHICA", MODULO, "socio", socioId, null, importe.toPlainString(), periodo);
    }

    /** Admite importe parcial. Pasa por la cuenta principal para que el crédito aplique su pago normal. */
    private void descontarCuota(Long actorId, SocioService.Socio socio, CuentaPrincipal cuenta, Descuento d, String clave) {
        Long socioId = socio.getId();
        List<CreditoService.CuotaVencida> vencidas = creditos.cuotasVencidas(socio.getPersonaId());
        CreditoService.CuotaVencida cuota = vencidas.stream().filter(q -> q.cuotaId().equals(d.cuotaId())).findFirst()
                .orElseThrow(() -> NegocioException.conflicto("OBLIGACION_INEXISTENTE", "Esa cuota no está vencida"));
        boolean hayAnterior = vencidas.stream().anyMatch(q -> q.creditoId().equals(cuota.creditoId())
                && q.numero() < cuota.numero());
        if (hayAnterior) {
            throw NegocioException.conflicto("CUOTA_ANTERIOR_PENDIENTE", "Primero debe cubrirse la cuota más antigua de ese crédito");
        }
        BigDecimal importe = d.importe() == null ? cuota.total() : d.importe();
        if (importe.signum() <= 0 || importe.compareTo(cuota.total()) > 0) {
            throw NegocioException.conflicto("PAGO_EXCEDIDO", "El importe supera lo que falta de esa cuota");
        }
        if (cuenta == null) {
            throw NegocioException.conflicto("CUENTA_INACTIVA", "El socio necesita una cuenta principal activa");
        }
        exigirGanancias(socioId, importe);
        Long operacion = cuentas.crearOperacionInterna("RETIRO_GANANCIA", importe, clave, actorId);
        cuentas.mover(cuenta, operacion, ENTRADA, importe, "DESCUENTO_DE_GANANCIAS");
        variacion(socioId, "DESCUENTO_OBLIGACION", importe.negate(), null);
        creditos.pagar(personas.usuarioDe(socio.getPersonaId()), cuota.creditoId(), importe, false, false,
                "descuento-" + java.util.UUID.randomUUID());
        auditoria.registrar(actorId, "DESCUENTO_CUOTA", MODULO, "socio", socioId, null, importe.toPlainString(), cuota.cuotaId().toString());
    }

    private void exigirGanancias(Long socioId, BigDecimal importe) {
        if (importe.compareTo(gananciasDisponibles(socioId)) > 0) {
            throw NegocioException.conflicto("GANANCIAS_INSUFICIENTES", "Las ganancias liberadas no alcanzan para lo elegido");
        }
    }

    private static void exigirVencido(List<String> vencidos, String periodo) {
        if (!vencidos.contains(periodo)) {
            throw NegocioException.conflicto("OBLIGACION_INEXISTENTE", "Ese mes no está pendiente");
        }
    }

    private List<Obligacion> obligaciones(SocioService.Socio socio) {
        Map<String, BigDecimal> aportesDelMes = parametros.valoresVigentes(List.of("APORTE_OBLIGATORIO", "APORTE_CAJA_CHICA"));
        return obligaciones(socio, dinero(aportesDelMes.get("APORTE_OBLIGATORIO")), dinero(aportesDelMes.get("APORTE_CAJA_CHICA")),
                aportes.periodosObligatorios(socio.getId()), aportesCajaChica.periodos(socio.getId()),
                creditos.cuotasVencidas(socio.getPersonaId()));
    }

    private List<Obligacion> obligaciones(SocioService.Socio socio, BigDecimal mensual, BigDecimal cajaChica,
            Set<String> obligatoriosPagados, Set<String> cajaChicaPagados, List<CreditoService.CuotaVencida> cuotasVencidas) {
        List<Obligacion> lista = new ArrayList<>();
        mesesVencidos(socio, obligatoriosPagados).forEach(p ->
                lista.add(new Obligacion("APORTE_MENSUAL", p, null, "Aporte mensual de " + p, mensual)));
        mesesVencidos(socio, cajaChicaPagados).forEach(p ->
                lista.add(new Obligacion("CAJA_CHICA", p, null, "Caja chica de " + p, cajaChica)));
        cuotasVencidas.forEach(q -> lista.add(new Obligacion("CUOTA", null, q.cuotaId(),
                "Letra " + q.numero() + " de " + (q.modalidad() == null ? q.familia() : q.modalidad()) + ", venció el " + q.vencimiento(),
                q.total())));
        return lista;
    }

    private boolean retiroAutorizado(Long socioId) {
        return autorizaciones.findBySocioIdAndAnio(socioId, hoy().getYear()).isPresent();
    }

    private BigDecimal gananciasDisponibles(Long socioId) {
        LocalDate inicioDeAnio = hoy().withDayOfYear(1);
        // Una pérdida de este año también limita lo que se puede sacar.
        return variaciones.gananciasLiberadas(socioId, inicioDeAnio).min(variaciones.resultados(socioId)).max(BigDecimal.ZERO);
    }

    private BigDecimal obligacionesPendientes(SocioService.Socio socio) {
        return obligaciones(socio).stream().map(Obligacion::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Meses sin pagar desde que entró el socio hasta el mes pasado. El mes en curso aún no está vencido. */
    private List<String> mesesVencidos(SocioService.Socio socio, Set<String> pagados) {
        List<String> vencidos = new ArrayList<>();
        YearMonth actual = YearMonth.now(reloj);
        for (YearMonth mes = YearMonth.from(socio.getDesde().atZone(reloj.getZone())); mes.isBefore(actual); mes = mes.plusMonths(1)) {
            if (!pagados.contains(mes.toString())) {
                vencidos.add(mes.toString());
            }
        }
        return vencidos;
    }

    private void variacion(Long socioId, String tipo, BigDecimal importe, Long aporteId) {
        variaciones.save(new VariacionParticipacion(null, socioId, tipo, importe, hoy(), aporteId, null, null, Instant.now(reloj)));
    }

// --- Compraventa interna

    /**
     * Un socio vende capital a otro al precio que acuerden. El comprador paga desde su cuenta principal al vendedor;
     * Janery no recibe ni entrega dinero. Solo se vende capital: las ganancias no cambian de manos.
     */
    @Transactional
    public Long registrarCompraventa(Long actorId, Long vendedorId, Long compradorId, BigDecimal capital, BigDecimal precio,
            String clave) {
        if (vendedorId.equals(compradorId)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "MISMO_SOCIO", "Vendedor y comprador deben ser distintos");
        }
        Long personaVendedor = socios.personaDe(vendedorId);
        Long personaComprador = socios.personaDe(compradorId);
        // Siempre en el mismo orden, para que dos compraventas cruzadas no se bloqueen entre sí.
        boolean vendedorPrimero = personaVendedor < personaComprador;
        CuentaPrincipal primera = cuentas.cuentaActivaBloqueada(vendedorPrimero ? personaVendedor : personaComprador);
        CuentaPrincipal segunda = cuentas.cuentaActivaBloqueada(vendedorPrimero ? personaComprador : personaVendedor);
        CuentaPrincipal cuentaVendedor = vendedorPrimero ? primera : segunda;
        CuentaPrincipal cuentaComprador = vendedorPrimero ? segunda : primera;
        Optional<Long> previa = cuentas.operacionPrevia(clave, "COMPRAVENTA_CAPITAL", precio, actorId);
        if (previa.isPresent()) {
            return previa.get();
        }
        if (capital.compareTo(variaciones.capital(vendedorId)) > 0) {
            throw NegocioException.conflicto("CAPITAL_INSUFICIENTE", "El vendedor no tiene ese capital");
        }
        Long operacion = cuentas.crearOperacionInterna("COMPRAVENTA_CAPITAL", precio, clave, actorId);
        cuentas.mover(cuentaComprador, operacion, SALIDA, precio, "COMPRA_DE_CAPITAL");
        cuentas.mover(cuentaVendedor, operacion, ENTRADA, precio, "VENTA_DE_CAPITAL");
        CompraventaCapital venta = compraventas.save(new CompraventaCapital(null, vendedorId, compradorId, capital, precio,
                operacion, actorId, Instant.now(reloj)));
        variacion(vendedorId, "APORTE_VENTA", capital.negate(), null);
        variacion(compradorId, "APORTE_COMPRA", capital, null);
        auditoria.registrar(actorId, "COMPRAVENTA_CAPITAL", MODULO, "compraventa", venta.getId(), null,
                capital.toPlainString() + " por " + precio.toPlainString(), null);
        return operacion;
    }

    // --- Cierre anual

    /**
     * Totaliza el año y lo bloquea. No mueve dinero: reserva y reparto ya ocurrieron con cada resultado.
     * El campo distribuido es lo que llegó a los fondos personales: ganancias sin reserva, menos pérdidas.
     */
    @Transactional
    public CierreVista cerrarAnio(Long actorId, int anio) {
        if (anio >= hoy().getYear()) {
            throw NegocioException.conflicto("ANIO_EN_CURSO", "Solo pueden cerrarse años ya terminados");
        }
        if (cierres.existsByAnio(anio)) {
            throw NegocioException.conflicto("ANIO_CERRADO", "El año " + anio + " ya está cerrado");
        }
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);
        BigDecimal neto = resultados.neto(desde, hasta);
        BigDecimal reserva = resultados.reserva(desde, hasta);
        CierreAnual cierre = cierres.save(new CierreAnual(null, anio, neto, parametros.valorVigente("FONDO_RESERVA"), reserva,
                neto.subtract(reserva), actorId, Instant.now(reloj)));
        auditoria.registrar(actorId, "CIERRE_ANUAL", MODULO, "cierre", cierre.getId(), null, neto.toPlainString(), String.valueOf(anio));
        return vista(cierre);
    }

    @Transactional(readOnly = true)
    public List<CierreVista> listarCierres() {
        return cierres.findAll(Sort.by(Sort.Direction.DESC, "anio")).stream().map(ParticipacionService::vista).toList();
    }

    private static CierreVista vista(CierreAnual c) {
        return new CierreVista(c.getAnio(), c.getResultadoNeto(), c.getPorcentajeReserva(), c.getReserva(),
                c.getDistribuido(), c.getCreadoEn());
    }

    // --- Apoyo

    /** Sumas de las variaciones de un socio; ver VariacionRepository.cifras. */
    private record Cifras(BigDecimal valor, BigDecimal compraventas, BigDecimal resultados, BigDecimal liberadas,
            BigDecimal delMes, BigDecimal baseDelMes) {
        static final Cifras CERO = new Cifras(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Resumen de varios socios con un número fijo de consultas, sin importar cuántos sean: antes eran unas veinte por
     * socio, y cada una es un viaje a la base de datos. Las cifras son las mismas que dan las consultas de un solo socio.
     */
    private List<ResumenSocio> resumenes(List<SocioService.Socio> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lista.stream().map(SocioService.Socio::getId).toList();
        YearMonth mes = YearMonth.now(reloj);
        String periodo = mes.toString();
        Map<Long, Cifras> cifras = new java.util.HashMap<>();
        variaciones.cifras(ids, hoy().withDayOfYear(1), mes.atDay(1)).forEach(f -> cifras.put(((Number) f[0]).longValue(),
                new Cifras((BigDecimal) f[1], (BigDecimal) f[2], (BigDecimal) f[3], (BigDecimal) f[4], (BigDecimal) f[5],
                        (BigDecimal) f[6])));
        Map<Long, List<AporteSocio>> aportesDe = aportes.findBySocioIdIn(ids).stream()
                .collect(Collectors.groupingBy(AporteSocio::getSocioId));
        Map<Long, List<AporteCajaChica>> cajaChicaDe = aportesCajaChica.findBySocioIdIn(ids).stream()
                .collect(Collectors.groupingBy(AporteCajaChica::getSocioId));
        Set<Long> autorizados = autorizaciones.autorizados(ids, hoy().getYear());
        Map<String, BigDecimal> aportesDelMes = parametros.valoresVigentes(List.of("APORTE_OBLIGATORIO", "APORTE_CAJA_CHICA"));
        BigDecimal mensual = aportesDelMes.get("APORTE_OBLIGATORIO");
        BigDecimal cajaChica = aportesDelMes.get("APORTE_CAJA_CHICA");
        BigDecimal total = valorTotal();
        List<Long> personaIds = lista.stream().map(SocioService.Socio::getPersonaId).toList();
        Map<Long, List<CreditoService.CuotaVencida>> vencidas = creditos.cuotasVencidas(personaIds);
        personas.precargar(personaIds);
        return lista.stream().map(s -> {
            Cifras c = cifras.getOrDefault(s.getId(), Cifras.CERO);
            List<AporteSocio> propios = aportesDe.getOrDefault(s.getId(), List.of());
            List<AporteCajaChica> caja = cajaChicaDe.getOrDefault(s.getId(), List.of());
            Set<String> obligatoriosPagados = propios.stream().filter(a -> OBLIGATORIO.equals(a.getTipo()))
                    .map(AporteSocio::getPeriodo).collect(Collectors.toSet());
            Set<String> cajaChicaPagados = caja.stream().map(AporteCajaChica::getPeriodo).collect(Collectors.toSet());
            BigDecimal porcentaje = total.signum() > 0 && c.valor().signum() > 0
                    ? c.valor().multiply(CIEN).divide(total, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal pendientes = obligaciones(s, dinero(mensual), dinero(cajaChica), obligatoriosPagados, cajaChicaPagados,
                    vencidas.getOrDefault(s.getPersonaId(), List.of()))
                    .stream().map(Obligacion::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ResumenSocio(s.getId(), personas.nombreCompleto(s.getPersonaId()), s.getDesde(),
                    suma(propios, INICIAL), suma(propios, OBLIGATORIO), suma(propios, PERSONAL), c.compraventas(), c.resultados(),
                    c.liberadas().min(c.resultados()).max(BigDecimal.ZERO), pendientes, autorizados.contains(s.getId()),
                    caja.stream().map(AporteCajaChica::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add), c.valor(), porcentaje,
                    mensual, cajaChica, periodo, obligatoriosPagados.contains(periodo), cajaChicaPagados.contains(periodo),
                    rendimiento(c.delMes(), c.baseDelMes()), c.delMes());
        }).toList();
    }

    private static BigDecimal suma(List<AporteSocio> lista, String tipo) {
        return lista.stream().filter(a -> tipo.equals(a.getTipo())).map(AporteSocio::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valorTotal() {
        return valoresPositivos().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Definido por el propietario: el fondo personal completo, con cierre mensual. Las ganancias y pérdidas de este mes
     * se ven al momento, pero solo cuentan para repartir desde el mes siguiente.
     */
    private Map<Long, BigDecimal> baseDeReparto() {
        Map<Long, BigDecimal> sumas = new java.util.HashMap<>();
        variaciones.basesDeReparto(YearMonth.now(reloj).atDay(1)).forEach(f -> sumas.put((Long) f[0], (BigDecimal) f[1]));
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        socios.activos().forEach(s -> {
            BigDecimal valor = sumas.getOrDefault(s.getId(), BigDecimal.ZERO);
            if (valor.signum() > 0) {
                valores.put(s.getId(), valor);
            }
        });
        return valores;
    }

    /** Ganancias menos pérdidas del periodo sobre el fondo con que lo empezó, más sus aportes. Null si no hay base. */
    private static BigDecimal rendimiento(BigDecimal resultado, BigDecimal base) {
        return base.signum() <= 0 ? null : resultado.multiply(CIEN).divide(base, 2, RoundingMode.HALF_UP);
    }

    private Map<Long, BigDecimal> valoresPositivos() {
        Map<Long, BigDecimal> sumas = new java.util.HashMap<>();
        variaciones.valores().forEach(f -> sumas.put((Long) f[0], (BigDecimal) f[1]));
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        socios.activos().forEach(s -> {
            BigDecimal valor = sumas.getOrDefault(s.getId(), BigDecimal.ZERO);
            if (valor.signum() > 0) {
                valores.put(s.getId(), valor);
            }
        });
        return valores;
    }

    /** Mes indicado o el actual. Admite meses pasados, para ponerse al día, pero no futuros. */
    private String periodo(String texto) {
        YearMonth actual = YearMonth.now(reloj);
        if (texto == null || texto.isBlank()) {
            return actual.toString();
        }
        try {
            YearMonth mes = YearMonth.parse(texto);
            // Decisión del propietario: se puede adelantar el pago de los meses que faltan del año.
            if (mes.getYear() > actual.getYear()) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODO_FUTURO", "Solo se pueden adelantar los meses de este año");
            }
            return mes.toString();
        } catch (DateTimeParseException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODO_INVALIDO", "El periodo debe tener el formato AAAA-MM");
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** El capital no se retira, solo se vende a otro socio: un socio activo no puede cerrar su cuenta. */
    @Component
    @RequiredArgsConstructor
    static class SociosCierre implements RestriccionCierre {

        private final SocioService.SocioRepository repository;

        @Override
        public void verificar(Long personaId) {
            if (repository.existsByPersonaIdAndEstado(personaId, "ACTIVO")) {
                throw NegocioException.conflicto("SOCIO_ACTIVO", "Un socio activo no puede cerrar su cuenta principal");
            }
        }
    }
}
