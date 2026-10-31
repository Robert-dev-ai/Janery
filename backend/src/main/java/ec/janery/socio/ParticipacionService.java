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
import ec.janery.cuenta.CuentaPrincipal;
import ec.janery.cuenta.CuentaService;
import ec.janery.cuenta.RestriccionCierre;
import ec.janery.parametros.ParametroService;
import ec.janery.persona.PersonaService;
import ec.janery.socio.Entidades.AporteCajaChica;
import ec.janery.socio.Entidades.AporteSocio;
import ec.janery.socio.Entidades.CierreAnual;
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
    }

    interface AporteCajaChicaRepository extends JpaRepository<AporteCajaChica, Long> {
        boolean existsBySocioIdAndPeriodo(Long socioId, String periodo);

        List<AporteCajaChica> findBySocioIdOrderByIdDesc(Long socioId);

        @Query("select coalesce(sum(a.importe), 0) from AporteCajaChica a where a.socioId = :socioId")
        BigDecimal total(@Param("socioId") Long socioId);
    }

    interface ResultadoRepository extends JpaRepository<Resultado, Long> {
        @Query("""
                select coalesce(sum(case when r.tipo = 'GANANCIA' then r.importe else -r.importe end), 0)
                from Resultado r where r.fecha >= :desde and r.fecha <= :hasta
                """)
        BigDecimal neto(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
    }

    interface CierreRepository extends JpaRepository<CierreAnual, Long> {
        boolean existsByAnio(Integer anio);
    }

    interface VariacionRepository extends JpaRepository<VariacionParticipacion, Long> {
        List<VariacionParticipacion> findBySocioIdOrderByFechaAscIdAsc(Long socioId);

        @Query("select coalesce(sum(v.importe), 0) from VariacionParticipacion v where v.socioId = :socioId")
        BigDecimal valor(@Param("socioId") Long socioId);
    }

    public record AporteVista(Long id, String tipo, BigDecimal importe, String periodo, Instant fecha) {
    }

    public record ResumenSocio(Long socioId, String nombre, Instant desde, BigDecimal totalObligatorio,
            BigDecimal totalPersonal, BigDecimal totalCajaChica, BigDecimal valorActual, BigDecimal porcentaje,
            BigDecimal aporteObligatorio, BigDecimal aporteCajaChica, String periodoActual,
            boolean obligatorioDelMesPagado, boolean cajaChicaDelMesPagado) {
    }

    public record VariacionVista(String tipo, BigDecimal importe, LocalDate fecha) {
    }

    public record PeriodoVista(String periodo, BigDecimal valorInicial, List<VariacionVista> variaciones,
            BigDecimal valorFinal) {
    }

    public record ResultadoVista(Long id, String tipo, BigDecimal importe, String concepto, LocalDate fecha) {
    }

    public record CierreVista(Integer anio, BigDecimal resultadoNeto, BigDecimal porcentajeReserva, BigDecimal reserva,
            BigDecimal distribuido, Instant fecha) {
    }

    private static final String OBLIGATORIO = "OBLIGATORIO";
    private static final String PERSONAL = "PERSONAL";
    private static final String GANANCIA = "GANANCIA";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";
    private static final String MODULO = "SOCIO";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final AporteRepository aportes;
    private final AporteCajaChicaRepository aportesCajaChica;
    private final ResultadoRepository resultados;
    private final CierreRepository cierres;
    private final VariacionRepository variaciones;
    private final SocioService socios;
    private final CuentaService cuentas;
    private final CajaService cajas;
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
        return resumen(socios.deUsuario(usuarioId), valorTotal());
    }

    @Transactional(readOnly = true)
    public List<ResumenSocio> listarSocios() {
        BigDecimal total = valorTotal();
        return socios.activos().stream().map(s -> resumen(s, total)).toList();
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
            periodos.add(0, new PeriodoVista(mes.toString(), inicial, delMes, acumulado));
        }
        return periodos;
    }

    // --- Resultados

    /** Se reparte entre los socios según su valor actual, y se refleja en su participación desde ese momento. */
    @Transactional
    public void registrarResultado(Long actorId, String tipo, BigDecimal importe, String concepto, LocalDate fechaPedida) {
        LocalDate fecha = fechaPedida == null ? hoy() : fechaPedida;
        if (fecha.isAfter(hoy())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FECHA_FUTURA", "La fecha no puede ser futura");
        }
        if (cierres.existsByAnio(fecha.getYear())) {
            throw NegocioException.conflicto("ANIO_CERRADO", "El año " + fecha.getYear() + " ya está cerrado");
        }
        Map<Long, BigDecimal> valores = valoresPositivos();
        BigDecimal total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw NegocioException.conflicto("SIN_CAPITAL", "No hay capital de socios sobre el que repartir el resultado");
        }
        boolean ganancia = GANANCIA.equals(tipo);
        if (!ganancia && importe.compareTo(total) > 0) {
            throw NegocioException.conflicto("PERDIDA_EXCEDE_CAPITAL", "La pérdida supera el capital de los socios");
        }
        Instant ahora = Instant.now(reloj);
        Resultado resultado = resultados.save(new Resultado(null, tipo, importe, concepto, fecha, actorId, ahora));
        RepartoProporcional.repartir(importe, valores).forEach((socioId, parte) -> {
            if (parte.signum() > 0) {
                variaciones.save(new VariacionParticipacion(null, socioId, tipo, ganancia ? parte : parte.negate(), fecha,
                        null, resultado.getId(), null, ahora));
            }
        });
        auditoria.registrar(actorId, "RESULTADO_" + tipo, MODULO, "resultado", resultado.getId(), null,
                importe.toPlainString(), concepto);
    }

    @Transactional(readOnly = true)
    public Page<ResultadoVista> listarResultados(int pagina, int tamano) {
        return resultados.findAll(PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fecha", "id")))
                .map(r -> new ResultadoVista(r.getId(), r.getTipo(), r.getImporte(), r.getConcepto(), r.getFecha()));
    }

    // --- Cierre anual

    /**
     * Con resultado neto positivo, separa la reserva y paga el resto a la cuenta principal de cada socio, en proporción
     * a su valor actual. Ambos importes salen de su participación. Un año solo se cierra una vez.
     */
    @Transactional
    public CierreVista cerrarAnio(Long actorId, int anio) {
        if (anio >= hoy().getYear()) {
            throw NegocioException.conflicto("ANIO_EN_CURSO", "Solo pueden cerrarse años ya terminados");
        }
        if (cierres.existsByAnio(anio)) {
            throw NegocioException.conflicto("ANIO_CERRADO", "El año " + anio + " ya está cerrado");
        }
        BigDecimal neto = resultados.neto(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31));
        BigDecimal porcentaje = parametros.valorVigente("FONDO_RESERVA");
        BigDecimal reserva = neto.signum() > 0
                ? neto.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal distribuible = neto.signum() > 0 ? neto.subtract(reserva) : BigDecimal.ZERO.setScale(2);
        Instant ahora = Instant.now(reloj);
        CierreAnual cierre = cierres.save(new CierreAnual(null, anio, neto, porcentaje, reserva, distribuible, actorId, ahora));

        if (neto.signum() > 0) {
            Map<Long, BigDecimal> valores = valoresPositivos();
            if (valores.isEmpty()) {
                throw NegocioException.conflicto("SIN_CAPITAL", "No hay capital de socios sobre el que repartir");
            }
            if (reserva.signum() > 0) {
                Long op = cuentas.crearOperacionInterna("RESERVA", reserva, null, actorId);
                cajas.registrar(CajaService.PRINCIPAL, op, SALIDA, reserva, "TRASPASO_A_RESERVA", String.valueOf(anio));
                cajas.registrar(CajaService.FONDO_RESERVA, op, ENTRADA, reserva, "RESERVA_ANUAL", String.valueOf(anio));
                registrarParte(RepartoProporcional.repartir(reserva, valores), "RESERVA", cierre.getId(), ahora);
            }
            if (distribuible.signum() > 0) {
                Long op = cuentas.crearOperacionInterna("DISTRIBUCION", distribuible, null, actorId);
                Map<Long, BigDecimal> partes = RepartoProporcional.repartir(distribuible, valores);
                registrarParte(partes, "DISTRIBUCION", cierre.getId(), ahora);
                partes.entrySet().stream().filter(p -> p.getValue().signum() > 0).sorted(Map.Entry.comparingByKey())
                        .forEach(p -> cuentas.mover(cuentas.cuentaActivaBloqueada(socios.personaDe(p.getKey())), op, ENTRADA,
                                p.getValue(), "DISTRIBUCION_GANANCIAS"));
            }
        }
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

    private void registrarParte(Map<Long, BigDecimal> partes, String tipo, Long cierreId, Instant ahora) {
        partes.forEach((socioId, parte) -> {
            if (parte.signum() > 0) {
                variaciones.save(new VariacionParticipacion(null, socioId, tipo, parte.negate(), hoy(), null, null,
                        cierreId, ahora));
            }
        });
    }

    // --- Apoyo

    private ResumenSocio resumen(SocioService.Socio s, BigDecimal total) {
        BigDecimal valor = variaciones.valor(s.getId());
        BigDecimal porcentaje = total.signum() > 0 && valor.signum() > 0
                ? valor.multiply(CIEN).divide(total, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String periodo = YearMonth.now(reloj).toString();
        return new ResumenSocio(s.getId(), personas.nombreCompleto(s.getPersonaId()), s.getDesde(),
                aportes.total(s.getId(), OBLIGATORIO), aportes.total(s.getId(), PERSONAL),
                aportesCajaChica.total(s.getId()), valor, porcentaje,
                parametros.valorVigente("APORTE_OBLIGATORIO"), parametros.valorVigente("APORTE_CAJA_CHICA"), periodo,
                aportes.existsBySocioIdAndTipoAndPeriodo(s.getId(), OBLIGATORIO, periodo),
                aportesCajaChica.existsBySocioIdAndPeriodo(s.getId(), periodo));
    }

    private BigDecimal valorTotal() {
        return valoresPositivos().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, BigDecimal> valoresPositivos() {
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        socios.activos().forEach(s -> {
            BigDecimal valor = variaciones.valor(s.getId());
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
            if (mes.isAfter(actual)) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODO_FUTURO", "No se puede aportar a un mes futuro");
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

    /** Sin regla aún para retirar el capital: un socio activo no puede cerrar su cuenta. */
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
