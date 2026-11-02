package ec.janery.credito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.ahorro.AhorroService;
import ec.janery.auditoria.AuditoriaService;
import ec.janery.caja.CajaService;
import ec.janery.common.NegocioException;
import ec.janery.credito.CalculadoraCredito.Letra;
import ec.janery.credito.Entidades.AhorroLetrasCredito;
import ec.janery.credito.Entidades.Credito;
import ec.janery.credito.Entidades.CuotaCredito;
import ec.janery.credito.Entidades.DesembolsoCredito;
import ec.janery.credito.Entidades.DetallePago;
import ec.janery.credito.Entidades.EncajeCredito;
import ec.janery.credito.Entidades.MovimientoAhorroLetras;
import ec.janery.credito.Entidades.PagoCredito;
import ec.janery.credito.Entidades.SolicitudCupo;
import ec.janery.cuenta.CuentaPrincipal;
import ec.janery.cuenta.CuentaService;
import ec.janery.parametros.ParametroService;
import ec.janery.persona.PersonaService;
import ec.janery.socio.SocioService;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Créditos a letras y emergentes. Los movimientos son internos a la cuenta principal: la caja principal solo cambia
 * cuando el dinero entra o sale de verdad, con un depósito o un retiro.
 */
@Service
@RequiredArgsConstructor
public class CreditoService {

    interface CreditoRepository extends JpaRepository<Credito, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select c from Credito c where c.id = :id")
        Optional<Credito> bloquear(@Param("id") Long id);

        List<Credito> findByPersonaIdOrderByIdDesc(Long personaId);

        Page<Credito> findByEstado(String estado, Pageable pageable);

        Optional<Credito> findByPersonaIdAndFamilia(Long personaId, String familia);

        Optional<Credito> findByPersonaIdAndFamiliaAndModalidadAndAnioPromocion(Long personaId, String familia,
                String modalidad, Integer anio);

        @Query("""
                select coalesce(sum(c.capitalPendiente), 0) from Credito c
                where c.personaId = :personaId and c.familia in ('EMERGENTE_SOCIO', 'EMERGENTE_ESTACIONAL')
                """)
        BigDecimal cupoUtilizado(@Param("personaId") Long personaId);

        @Query("""
                select count(c) from Credito c where c.personaId = :personaId and c.familia = 'EMERGENTE_CLIENTE'
                and c.fechaInicio >= :desde and c.fechaInicio <= :hasta
                """)
        long emergentesClienteEnPeriodo(@Param("personaId") Long personaId, @Param("desde") LocalDate desde,
                @Param("hasta") LocalDate hasta);

        @Query("""
                select count(c) > 0 from Credito c where c.personaId = :personaId and (c.estado = 'SOLICITADO'
                or (c.estado = 'ACTIVO' and (c.familia in ('LETRAS', 'EMERGENTE_CLIENTE') or c.capitalPendiente > 0
                or c.interesAcumulado > 0 or c.desgravamenAcumulado > 0)))
                """)
        boolean tieneObligaciones(@Param("personaId") Long personaId);

        @Query("""
                select count(c) > 0 from Credito c
                where c.personaId = :personaId and c.familia = 'EMERGENTE_CLIENTE' and c.estado in ('ACTIVO', 'SOLICITADO')
                """)
        boolean tieneEmergenteClienteAbierto(@Param("personaId") Long personaId);

        Optional<Credito> findByClaveSolicitud(String clave);
    }

    interface SolicitudCupoRepository extends JpaRepository<SolicitudCupo, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from SolicitudCupo s where s.id = :id")
        Optional<SolicitudCupo> bloquear(@Param("id") Long id);

        Optional<SolicitudCupo> findByClave(String clave);

        List<SolicitudCupo> findByPersonaIdOrderByIdDesc(Long personaId);

        List<SolicitudCupo> findByEstadoOrderById(String estado);

        boolean existsByPersonaIdAndEstado(Long personaId, String estado);

        @Query("select coalesce(sum(s.importe), 0) from SolicitudCupo s where s.personaId = :personaId and s.estado = 'PENDIENTE'")
        BigDecimal pendiente(@Param("personaId") Long personaId);
    }

    interface CuotaRepository extends JpaRepository<CuotaCredito, Long> {
        List<CuotaCredito> findByCreditoIdOrderByNumero(Long creditoId);

        List<CuotaCredito> findByCreditoIdInOrderByNumero(java.util.Collection<Long> creditoIds);

        @Query("""
                select q from CuotaCredito q, Credito c where c.id = q.creditoId and c.personaId = :personaId
                and c.estado = 'ACTIVO' and q.pagadaEn is null and q.vencimiento < :hoy
                order by q.vencimiento, q.numero
                """)
        List<CuotaCredito> vencidas(@Param("personaId") Long personaId, @Param("hoy") LocalDate hoy);
    }

    interface DesembolsoRepository extends JpaRepository<DesembolsoCredito, Long> {
        @Query("select coalesce(sum(d.importe), 0) from DesembolsoCredito d where d.creditoId = :creditoId")
        BigDecimal totalDe(@Param("creditoId") Long creditoId);
    }

    interface PagoRepository extends JpaRepository<PagoCredito, Long> {
    }

    interface DetalleRepository extends JpaRepository<DetallePago, Long> {
    }

    interface EncajeRepository extends JpaRepository<EncajeCredito, Long> {
        Optional<EncajeCredito> findByCreditoId(Long creditoId);

        List<EncajeCredito> findByCreditoIdIn(java.util.Collection<Long> creditoIds);
    }

    interface AhorroLetrasRepository extends JpaRepository<AhorroLetrasCredito, Long> {
        Optional<AhorroLetrasCredito> findByCreditoId(Long creditoId);

        List<AhorroLetrasCredito> findByCreditoIdIn(java.util.Collection<Long> creditoIds);
    }

    interface MovimientoAhorroLetrasRepository extends JpaRepository<MovimientoAhorroLetras, Long> {
    }

    /** Estado del crédito con cargos devengados hasta hoy, sin guardar. */
    public record CreditoVista(Long id, String familia, String modalidad, String perfil, String estado,
            BigDecimal importe, Integer plazoMeses, BigDecimal tasaInteres, BigDecimal tasaDesgravamen,
            String unidadTasa, BigDecimal capitalPendiente, BigDecimal interes, BigDecimal desgravamen,
            BigDecimal totalPendiente, BigDecimal encaje, boolean encajeDevuelto, BigDecimal ahorroLetras,
            String titular, String motivoRechazo, List<CuotaVista> cuotas, List<Integer> cuotasPagadas,
            int letrasParaPrecancelar, boolean precancelable, Instant precanceladoEn, Instant creadoEn, Instant finalizadoEn,
            String origenEncaje, BigDecimal tasaMora, BigDecimal moraPendiente, int letrasAtrasadas) {
    }

    public record CuotaVista(int numero, LocalDate vencimiento, BigDecimal capital, BigDecimal ahorroLetras,
            BigDecimal fondoDesgravamen, BigDecimal interes, BigDecimal total, BigDecimal pagado, long diasAtraso,
            BigDecimal mora) {
    }

    public record SolicitudCupoVista(Long id, String titular, BigDecimal importe, String promocion, String estado,
            String motivo, Instant creadoEn) {
    }

    /** total es lo que falta por pagar de la cuota, tras los pagos parciales. */
    public record CuotaVencida(Long creditoId, Long cuotaId, int numero, java.time.LocalDate vencimiento, String familia,
            String modalidad, BigDecimal total) {
    }

    public record ProductoLetras(String modalidad, BigDecimal tasaInteres, BigDecimal porcentajeEncaje) {
    }

    public record Productos(String perfil, List<ProductoLetras> letras, BigDecimal tasaDesgravamen,
            BigDecimal importeMinimo, BigDecimal importeMaximo, int plazoMinimo, int plazoMaximo,
            BigDecimal emergenteLimite, BigDecimal cupoUtilizado, BigDecimal cupoDisponible,
            BigDecimal emergenteInteres, BigDecimal emergenteDesgravamen, String emergenteUnidad,
            String promocionAbierta, BigDecimal promocionLimite, BigDecimal promocionTasa) {
    }

    public record Simulacion(BigDecimal importe, int plazoMeses, BigDecimal tasaInteres, BigDecimal tasaDesgravamen,
            BigDecimal encaje, List<Letra> cuotas) {
    }

    private static final String LETRAS = "LETRAS";
    private static final String EMERGENTE_CLIENTE = "EMERGENTE_CLIENTE";
    private static final String EMERGENTE_SOCIO = "EMERGENTE_SOCIO";
    private static final String EMERGENTE_ESTACIONAL = "EMERGENTE_ESTACIONAL";
    private static final String SOLICITADO = "SOLICITADO";
    private static final String ACTIVO = "ACTIVO";
    private static final String FINALIZADO = "FINALIZADO";
    private static final String SOCIO = "SOCIO";
    private static final String CLIENTE = "CLIENTE";
    private static final String PCT_MENSUAL = "PCT_MENSUAL";
    private static final String PCT_DIARIO = "PCT_DIARIO";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";
    private static final String MODULO = "CREDITO";
    private static final String PENDIENTE = "PENDIENTE";
    private static final List<String> ORIGENES_ENCAJE = List.of("EFECTIVO", "CUENTA", "AHORROS", "CREDITO");
    private static final List<String> MODALIDADES = List.of("NORMAL", "INVERSION", "ESTUDIO", "PERSONALIZADO");

    private final CreditoRepository creditos;
    private final CuotaRepository cuotas;
    private final DesembolsoRepository desembolsos;
    private final PagoRepository pagos;
    private final DetalleRepository detalles;
    private final EncajeRepository encajes;
    private final AhorroLetrasRepository ahorros;
    private final MovimientoAhorroLetrasRepository movimientosAhorro;
    private final SolicitudCupoRepository solicitudesCupo;
    private final AhorroService ahorrosPersona;
    private final CajaService caja;
    private final CuentaService cuentas;
    private final PersonaService personas;
    private final SocioService socios;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    // --- Consulta de productos

    @Transactional(readOnly = true)
    public Productos productos(Long usuarioId) {
        Long personaId = personaDe(usuarioId);
        String perfil = perfil(personaId);
        boolean socio = SOCIO.equals(perfil);
        String cupo = socio ? "EMERGENTE_SOCIO_CUPO" : "EMERGENTE_CLIENTE_LIMITE";
        String interes = socio ? "EMERGENTE_SOCIO_INTERES" : "EMERGENTE_CLIENTE_INTERES";
        String desgravamen = socio ? "EMERGENTE_SOCIO_DESGRAVAMEN" : "EMERGENTE_CLIENTE_DESGRAVAMEN";
        List<String> codigos = new java.util.ArrayList<>(List.of(cupo, interes, desgravamen, "LETRAS_DESGRAVAMEN", "LETRAS_IMPORTE_MIN",
                "LETRAS_IMPORTE_MAX", "LETRAS_PLAZO_MIN", "LETRAS_PLAZO_MAX", "ESTACIONAL_LIMITE", "ESTACIONAL_TASA"));
        MODALIDADES.forEach(m -> {
            codigos.add("LETRAS_INTERES_%s_%s".formatted(m, perfil));
            codigos.add("LETRAS_ENCAJE_%s_%s".formatted(m, perfil));
        });
        Map<String, BigDecimal> v = parametros.valoresVigentes(codigos);
        List<ProductoLetras> letras = MODALIDADES.stream().map(m -> new ProductoLetras(m,
                v.get("LETRAS_INTERES_%s_%s".formatted(m, perfil)), v.get("LETRAS_ENCAJE_%s_%s".formatted(m, perfil)))).toList();
        BigDecimal limite = v.get(cupo);
        BigDecimal utilizado = socio ? creditos.cupoUtilizado(personaId) : BigDecimal.ZERO;
        return new Productos(perfil, letras, v.get("LETRAS_DESGRAVAMEN"), v.get("LETRAS_IMPORTE_MIN"), v.get("LETRAS_IMPORTE_MAX"),
                v.get("LETRAS_PLAZO_MIN").intValue(), v.get("LETRAS_PLAZO_MAX").intValue(),
                limite, utilizado, limite.subtract(utilizado).max(BigDecimal.ZERO), v.get(interes), v.get(desgravamen),
                socio ? PCT_DIARIO : PCT_MENSUAL, socio ? promocionAbierta() : null, v.get("ESTACIONAL_LIMITE"), v.get("ESTACIONAL_TASA"));
    }

    // --- Crédito a letras

    @Transactional(readOnly = true)
    public Simulacion simular(Long usuarioId, String modalidad, BigDecimal importe, int plazo) {
        String perfil = perfil(personaDe(usuarioId));
        validarLetras(importe, plazo);
        BigDecimal interes = parametros.valorVigente("LETRAS_INTERES_%s_%s".formatted(modalidad, perfil));
        BigDecimal desgravamen = parametros.valorVigente("LETRAS_DESGRAVAMEN");
        BigDecimal encaje = parametros.valorVigente("LETRAS_ENCAJE_%s_%s".formatted(modalidad, perfil));
        return new Simulacion(importe, plazo, interes, desgravamen, CalculadoraCredito.porcentaje(importe, encaje),
                CalculadoraCredito.letras(importe, plazo, interes, desgravamen, hoy()));
    }

    /** Las tasas y el origen del encaje quedan fijados al solicitar; las fechas de las letras, al aprobar. */
    @Transactional
    public Long solicitarLetras(Long usuarioId, String modalidad, BigDecimal importe, int plazo, String origenEncaje, Long ahorroEncajeId) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        String perfil = perfil(personaId);
        validarLetras(importe, plazo);
        if (!personas.datosCreditoCompletos(personaId)) {
            throw NegocioException.conflicto("DATOS_CREDITO_INCOMPLETOS", "Complete la dirección y los datos para crédito antes de solicitarlo");
        }
        if (origenEncaje == null || !ORIGENES_ENCAJE.contains(origenEncaje)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "ORIGEN_ENCAJE_REQUERIDO", "Indique de dónde pagará el encaje");
        }
        if ("AHORROS".equals(origenEncaje)) {
            ahorrosPersona.exigirAhorroNormal(personaId, ahorroEncajeId);
        }
        Credito c = nuevo(personaId, LETRAS, modalidad, perfil, SOLICITADO, PCT_MENSUAL,
                parametros.valorVigente("LETRAS_INTERES_%s_%s".formatted(modalidad, perfil)),
                parametros.valorVigente("LETRAS_DESGRAVAMEN"));
        c.setImporte(importe);
        c.setPlazoMeses(plazo);
        c.setPorcentajeEncaje(parametros.valorVigente("LETRAS_ENCAJE_%s_%s".formatted(modalidad, perfil)));
        c.setOrigenEncaje(origenEncaje);
        c.setAhorroEncajeId("AHORROS".equals(origenEncaje) ? ahorroEncajeId : null);
        creditos.save(c);
        auditoria.registrar(usuarioId, "CREDITO_SOLICITADO", MODULO, "credito", c.getId(), null, importe.toPlainString(), null);
        return c.getId();
    }

    @Transactional
    public void aprobar(Long actorId, Long creditoId) {
        Long personaId = creditos.findById(creditoId).map(Credito::getPersonaId)
                .orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Credito c = bloquear(creditoId);
        exigirSolicitado(c);
        if (EMERGENTE_CLIENTE.equals(c.getFamilia())) {
            aprobarEmergente(actorId, cuenta, c);
            return;
        }
        Instant ahora = Instant.now(reloj);
        BigDecimal encaje = CalculadoraCredito.porcentaje(c.getImporte(), c.getPorcentajeEncaje());
        // Solo "CREDITO" paga con el propio desembolso: los demás deben tener el dinero antes de recibirlo.
        if ("CUENTA".equals(c.getOrigenEncaje()) && encaje.compareTo(cuenta.getSaldoDisponible()) > 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "La cuenta del solicitante no cubre el encaje");
        }

        Long desembolso = cuentas.crearOperacionInterna("DESEMBOLSO", c.getImporte(), null, actorId);
        cuentas.mover(cuenta, desembolso, ENTRADA, c.getImporte(), "DESEMBOLSO_CREDITO");
        desembolsos.save(new DesembolsoCredito(null, c.getId(), desembolso, c.getImporte(), ahora));

        cobrarEncaje(c, cuenta, desembolso, encaje);
        encajes.save(new EncajeCredito(null, c.getId(), desembolso, encaje, ahora, null));
        ahorros.save(new AhorroLetrasCredito(null, c.getId(), BigDecimal.ZERO.setScale(2), null));

        c.setFechaInicio(hoy());
        guardarCuotas(c.getId(), CalculadoraCredito.letras(c.getImporte(), c.getPlazoMeses(), c.getTasaInteres(),
                c.getTasaDesgravamen(), c.getFechaInicio()));
        c.setCapitalPendiente(c.getImporte());
        c.setTasaMora(tasaMora(c));
        c.setEstado(ACTIVO);
        auditoria.registrar(actorId, "CREDITO_APROBADO", MODULO, "credito", c.getId(), SOLICITADO, ACTIVO, c.getOrigenEncaje());
    }

    /** Definido por el propietario: el encaje se paga en efectivo, de la cuenta, de un ahorro o del propio crédito. */
    private void cobrarEncaje(Credito c, CuentaPrincipal cuenta, Long operacion, BigDecimal encaje) {
        if (encaje.signum() <= 0) {
            return;
        }
        switch (c.getOrigenEncaje() == null ? "CREDITO" : c.getOrigenEncaje()) {
            case "EFECTIVO" -> caja.registrarEnPrincipal(operacion, ENTRADA, encaje, "ENCAJE_EFECTIVO");
            case "AHORROS" -> ahorrosPersona.descontarEncaje(c.getPersonaId(), c.getAhorroEncajeId(), encaje, operacion);
            default -> cuentas.mover(cuenta, operacion, SALIDA, encaje, "ENCAJE_CREDITO");
        }
    }

    /** La mora solo aplica a clientes y queda fijada al aprobar, como las demás tasas del crédito. */
    private BigDecimal tasaMora(Credito c) {
        return CLIENTE.equals(c.getPerfil()) ? parametros.valorVigente("MORA_DIARIA") : BigDecimal.ZERO;
    }

    @Transactional
    public void rechazar(Long actorId, Long creditoId, String motivo) {
        Credito c = bloquear(creditoId);
        exigirSolicitado(c);
        c.setEstado("RECHAZADO");
        c.setMotivoRechazo(motivo);
        auditoria.registrar(actorId, "CREDITO_RECHAZADO", MODULO, "credito", c.getId(), SOLICITADO, "RECHAZADO", motivo);
    }

    // --- Emergentes

    /** Emergente de cliente: queda por aprobar, dentro del límite y de la frecuencia mensual. La clave evita duplicarlo. */
    @Transactional
    public Long solicitarEmergenteCliente(Long usuarioId, BigDecimal importe, String clave) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        Optional<Credito> previa = creditos.findByClaveSolicitud(clave);
        if (previa.isPresent()) {
            if (!previa.get().getPersonaId().equals(personaId) || previa.get().getImporte().compareTo(importe) != 0) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return previa.get().getId();
        }
        if (socios.esSocio(personaId)) {
            throw NegocioException.conflicto("PERFIL_INCORRECTO", "Los socios usan el cupo emergente");
        }
        if (importe.compareTo(parametros.valorVigente("EMERGENTE_CLIENTE_LIMITE")) > 0) {
            throw NegocioException.conflicto("LIMITE_EXCEDIDO", "El importe supera el límite del emergente");
        }
        if (creditos.tieneEmergenteClienteAbierto(personaId)) {
            throw NegocioException.conflicto("EMERGENTE_ABIERTO", "Debe pagar su emergente actual antes de pedir otro");
        }
        exigirFrecuenciaEmergente(personaId);
        Credito c = nuevo(personaId, EMERGENTE_CLIENTE, null, CLIENTE, SOLICITADO, PCT_MENSUAL,
                parametros.valorVigente("EMERGENTE_CLIENTE_INTERES"), parametros.valorVigente("EMERGENTE_CLIENTE_DESGRAVAMEN"));
        c.setImporte(importe);
        c.setPlazoMeses(1);
        c.setClaveSolicitud(clave);
        creditos.save(c);
        auditoria.registrar(usuarioId, "CREDITO_SOLICITADO", MODULO, "credito", c.getId(), null, importe.toPlainString(), null);
        return c.getId();
    }

    private void exigirFrecuenciaEmergente(Long personaId) {
        LocalDate hoy = hoy();
        long usados = creditos.emergentesClienteEnPeriodo(personaId, hoy.withDayOfMonth(1), hoy.withDayOfMonth(hoy.lengthOfMonth()));
        if (usados >= parametros.valorVigente("EMERGENTE_CLIENTE_FRECUENCIA").longValue()) {
            throw NegocioException.conflicto("FRECUENCIA_EXCEDIDA", "Ya utilizó el emergente este mes");
        }
    }

    /** La letra vence un mes después de aprobarse: el plazo corre desde que la persona recibe el dinero. */
    private void aprobarEmergente(Long actorId, CuentaPrincipal cuenta, Credito c) {
        exigirFrecuenciaEmergente(c.getPersonaId());
        LocalDate hoy = hoy();
        BigDecimal importe = c.getImporte();
        c.setCapitalPendiente(importe);
        c.setFechaInicio(hoy);
        c.setTasaMora(tasaMora(c));
        c.setEstado(ACTIVO);
        BigDecimal interes = CalculadoraCredito.porcentaje(importe, c.getTasaInteres());
        BigDecimal cargoDesgravamen = CalculadoraCredito.porcentaje(importe, c.getTasaDesgravamen());
        cuotas.save(new CuotaCredito(c.getId(), 1, hoy.plusMonths(1), importe, BigDecimal.ZERO.setScale(2),
                cargoDesgravamen, interes, importe.add(interes).add(cargoDesgravamen)));
        desembolsar(cuenta, c, importe, null, actorId);
        auditoria.registrar(actorId, "CREDITO_APROBADO", MODULO, "credito", c.getId(), SOLICITADO, ACTIVO, null);
    }

    /** Retiro del cupo de socio, general o de una promoción abierta: queda por aprobar y aparta cupo mientras espera. */
    @Transactional
    public Long solicitarCupo(Long usuarioId, BigDecimal importe, String promocion, String clave) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        Optional<SolicitudCupo> previa = solicitudesCupo.findByClave(clave);
        if (previa.isPresent()) {
            if (!previa.get().getPersonaId().equals(personaId) || previa.get().getImporte().compareTo(importe) != 0) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return previa.get().getId();
        }
        if (!socios.esSocio(personaId)) {
            throw NegocioException.conflicto("SOLO_SOCIOS", "El cupo emergente es exclusivo de socios");
        }
        validarCupo(personaId, importe.add(solicitudesCupo.pendiente(personaId)), promocion, importe);
        SolicitudCupo s = solicitudesCupo.save(new SolicitudCupo(null, personaId, importe, promocion, PENDIENTE, null, clave,
                null, null, Instant.now(reloj)));
        auditoria.registrar(usuarioId, "CUPO_SOLICITADO", MODULO, "solicitud_cupo", s.getId(), null, importe.toPlainString(), promocion);
        return s.getId();
    }

    private void validarCupo(Long personaId, BigDecimal totalPedido, String promocion, BigDecimal importe) {
        if (promocion != null) {
            exigirLimitePromocion(personaId, promocion, importe);
        }
        BigDecimal disponible = parametros.valorVigente("EMERGENTE_SOCIO_CUPO").subtract(creditos.cupoUtilizado(personaId));
        if (totalPedido.compareTo(disponible) > 0) {
            throw NegocioException.conflicto("CUPO_EXCEDIDO", "El importe supera su cupo disponible");
        }
    }

    @Transactional
    public void aprobarCupo(Long actorId, Long solicitudId) {
        Long personaId = solicitudesCupo.findById(solicitudId).map(SolicitudCupo::getPersonaId)
                .orElseThrow(() -> NegocioException.noEncontrado("Solicitud no encontrada"));
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        SolicitudCupo s = bloquearSolicitud(solicitudId);
        BigDecimal importe = s.getImporte();
        String promocion = s.getPromocion();
        validarCupo(personaId, importe, promocion, importe);
        resolver(s, "APROBADA", actorId, null);
        Credito c = promocion == null ? lineaGeneral(personaId) : lineaPromocion(personaId, promocion);
        // Lo ya devengado conserva su tasa; lo nuevo devenga con la vigente.
        devengar(c);
        if (promocion == null) {
            c.setTasaInteres(parametros.valorVigente("EMERGENTE_SOCIO_INTERES"));
            c.setTasaDesgravamen(parametros.valorVigente("EMERGENTE_SOCIO_DESGRAVAMEN"));
        } else {
            c.setTasaInteres(parametros.valorVigente("ESTACIONAL_TASA"));
        }
        c.setCapitalPendiente(c.getCapitalPendiente().add(importe));
        creditos.save(c);
        desembolsar(cuenta, c, importe, null, actorId);
        auditoria.registrar(actorId, "CUPO_APROBADO", MODULO, "solicitud_cupo", s.getId(), PENDIENTE, "APROBADA", null);
    }

    @Transactional
    public void rechazarCupo(Long actorId, Long solicitudId, String motivo) {
        SolicitudCupo s = bloquearSolicitud(solicitudId);
        resolver(s, "RECHAZADA", actorId, motivo);
        auditoria.registrar(actorId, "CUPO_RECHAZADO", MODULO, "solicitud_cupo", s.getId(), PENDIENTE, "RECHAZADA", motivo);
    }

    private SolicitudCupo bloquearSolicitud(Long id) {
        SolicitudCupo s = solicitudesCupo.bloquear(id).orElseThrow(() -> NegocioException.noEncontrado("Solicitud no encontrada"));
        em.refresh(s);
        if (!PENDIENTE.equals(s.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "La solicitud ya fue resuelta");
        }
        return s;
    }

    private void resolver(SolicitudCupo s, String estado, Long actorId, String motivo) {
        s.setEstado(estado);
        s.setMotivo(motivo);
        s.setResueltaPor(actorId);
        s.setResueltaEn(Instant.now(reloj));
    }

    @Transactional(readOnly = true)
    public List<SolicitudCupoVista> misSolicitudesCupo(Long usuarioId) {
        return solicitudesCupo.findByPersonaIdOrderByIdDesc(personaDe(usuarioId)).stream().map(this::vistaSolicitud).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudCupoVista> solicitudesCupoPendientes() {
        return solicitudesCupo.findByEstadoOrderById(PENDIENTE).stream().map(this::vistaSolicitud).toList();
    }

    private SolicitudCupoVista vistaSolicitud(SolicitudCupo s) {
        return new SolicitudCupoVista(s.getId(), personas.nombreCompleto(s.getPersonaId()), s.getImporte(), s.getPromocion(),
                s.getEstado(), s.getMotivo(), s.getCreadoEn());
    }

    private Credito lineaGeneral(Long personaId) {
        return creditos.findByPersonaIdAndFamilia(personaId, EMERGENTE_SOCIO).orElseGet(() -> linea(
                nuevo(personaId, EMERGENTE_SOCIO, null, SOCIO, ACTIVO, PCT_DIARIO,
                        parametros.valorVigente("EMERGENTE_SOCIO_INTERES"),
                        parametros.valorVigente("EMERGENTE_SOCIO_DESGRAVAMEN"))));
    }

    private Credito lineaPromocion(Long personaId, String promocion) {
        int anio = hoy().getYear();
        return creditos.findByPersonaIdAndFamiliaAndModalidadAndAnioPromocion(personaId, EMERGENTE_ESTACIONAL,
                promocion, anio).orElseGet(() -> {
                    Credito nuevo = nuevo(personaId, EMERGENTE_ESTACIONAL, promocion, SOCIO, ACTIVO, PCT_DIARIO,
                            parametros.valorVigente("ESTACIONAL_TASA"), BigDecimal.ZERO);
                    nuevo.setAnioPromocion(anio);
                    return linea(nuevo);
                });
    }

    /** El límite es por promoción: lo ya retirado cuenta aunque se haya pagado. */
    private void exigirLimitePromocion(Long personaId, String promocion, BigDecimal importe) {
        if (!promocion.equals(promocionAbierta())) {
            throw NegocioException.conflicto("PROMOCION_CERRADA", "Esa promoción no está abierta este mes");
        }
        BigDecimal retirado = creditos.findByPersonaIdAndFamiliaAndModalidadAndAnioPromocion(personaId, EMERGENTE_ESTACIONAL,
                promocion, hoy().getYear()).map(c -> desembolsos.totalDe(c.getId())).orElse(BigDecimal.ZERO);
        if (retirado.add(importe).compareTo(parametros.valorVigente("ESTACIONAL_LIMITE")) > 0) {
            throw NegocioException.conflicto("LIMITE_EXCEDIDO", "El importe supera el máximo de la promoción");
        }
    }

    private Credito linea(Credito c) {
        c.setUltimoDevengo(hoy());
        c.setFechaInicio(hoy());
        return creditos.save(c);
    }

    private String promocionAbierta() {
        int mes = hoy().getMonthValue();
        if (mes == parametros.valorVigente("ESTACIONAL_MES_ESTUDIANTIL").intValue()) {
            return "ESTUDIANTIL";
        }
        return mes == parametros.valorVigente("ESTACIONAL_MES_NAVIDENO").intValue() ? "NAVIDENO" : null;
    }

    private Long desembolsar(CuentaPrincipal cuenta, Credito c, BigDecimal importe, String clave, Long actorId) {
        Long operacion = cuentas.crearOperacionInterna("DESEMBOLSO", importe, clave, actorId);
        cuentas.mover(cuenta, operacion, ENTRADA, importe, "DESEMBOLSO_CREDITO");
        desembolsos.save(new DesembolsoCredito(null, c.getId(), operacion, importe, Instant.now(reloj)));
        auditoria.registrar(actorId, "CREDITO_DESEMBOLSADO", MODULO, "credito", c.getId(), null, importe.toPlainString(), null);
        return operacion;
    }

    // --- Pagos

    /** Letras y emergente de cliente: se paga la siguiente cuota, entera o en parte. Cupo: hasta lo adeudado. */
    @Transactional
    public Long pagar(Long usuarioId, Long creditoId, BigDecimal importe, boolean usarAhorroLetras, boolean usarEncaje,
            String clave) {
        Long personaId = personaDe(usuarioId);
        Credito leido = creditos.findById(creditoId).filter(c -> c.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "PAGO_CREDITO", null, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        Credito c = bloquear(leido.getId());
        if (!ACTIVO.equals(c.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Este crédito no admite pagos");
        }
        Long operacion = esLinea(c)
                ? pagarLinea(cuenta, c, importe, clave, usuarioId)
                : pagarCuota(cuenta, c, importe, usarAhorroLetras, usarEncaje, clave, usuarioId);
        auditoria.registrar(usuarioId, "CREDITO_PAGO", MODULO, "credito", c.getId(), null, operacion.toString(), null);
        return operacion;
    }

    /**
     * Sin importe, o usando encaje o ahorro letras, cubre lo que falta de la cuota con su mora. Un pago parcial cobra primero capital.
     * Lo que exceda de la letra pasa a las siguientes: la persona puede adelantarse hasta cubrir todo el crédito.
     */
    private Long pagarCuota(CuentaPrincipal cuenta, Credito c, BigDecimal importe, boolean usarAhorro, boolean usarEncaje,
            String clave, Long actorId) {
        List<CuotaCredito> todas = cuotas.findByCreditoIdOrderByNumero(c.getId());
        List<CuotaCredito> pendientes = todas.stream().filter(q -> q.getPagadaEn() == null).toList();
        if (pendientes.isEmpty()) {
            throw new IllegalStateException("Crédito activo sin cuotas pendientes: " + c.getId());
        }
        CuotaCredito cuota = pendientes.get(0);
        boolean ultima = cuota.getNumero().equals(todas.size());
        if ((usarAhorro || usarEncaje) && (!ultima || !LETRAS.equals(c.getFamilia()))) {
            throw NegocioException.conflicto("SOLO_ULTIMA_LETRA", "Ahorro letras y encaje solo pueden cubrir la última letra");
        }
        LocalDate hoy = hoy();
        BigDecimal aPagar = importe == null || usarAhorro || usarEncaje ? deudaDe(cuota, c, hoy) : importe;
        BigDecimal deuda = pendientes.stream().map(q -> deudaDe(q, c, hoy)).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (aPagar.compareTo(deuda) > 0) {
            throw NegocioException.conflicto("PAGO_EXCEDIDO", "El importe supera lo que falta del crédito");
        }
        Instant ahora = Instant.now(reloj);
        Long operacion = cuentas.crearOperacionInterna("PAGO_CREDITO", aPagar, clave, actorId);
        // Liberar antes del cobro hace que esos fondos cubran la letra; el remanente queda en la cuenta principal.
        if (usarAhorro) {
            liberarAhorro(cuenta, c, operacion, null, ahora);
        }
        if (usarEncaje) {
            liberarEncaje(cuenta, c, operacion, ahora);
        }
        cuentas.mover(cuenta, operacion, SALIDA, aPagar, "PAGO_CREDITO");

        BigDecimal resto = aPagar;
        for (CuotaCredito q : pendientes) {
            if (resto.signum() <= 0) {
                break;
            }
            BigDecimal aEstaLetra = resto.min(deudaDe(q, c, hoy));
            aplicarALetra(c, q, operacion, aEstaLetra, ahora, hoy, actorId);
            resto = resto.subtract(aEstaLetra);
        }
        if (todas.get(todas.size() - 1).getPagadaEn() != null) {
            finalizar(cuenta, c, operacion, todas.get(todas.size() - 1).getId(), ahora, actorId);
        }
        return operacion;
    }

    private static BigDecimal deudaDe(CuotaCredito q, Credito c, LocalDate hoy) {
        return q.pendiente().add(q.moraPendiente(hoy, c.getTasaMora()));
    }

    /** Definido por el propietario: capital, desgravamen, interés y ahorro letras. La mora se cobra al final. */
    private void aplicarALetra(Credito c, CuotaCredito cuota, Long operacion, BigDecimal monto, Instant ahora, LocalDate hoy, Long actorId) {
        BigDecimal aCapital = monto.min(cuota.getCapital().subtract(cuota.getCapitalPagado()));
        BigDecimal resto = monto.subtract(aCapital);
        BigDecimal aDesgravamen = resto.min(cuota.getFondoDesgravamen().subtract(cuota.getDesgravamenPagado()));
        resto = resto.subtract(aDesgravamen);
        BigDecimal aInteres = resto.min(cuota.getInteres().subtract(cuota.getInteresPagado()));
        resto = resto.subtract(aInteres);
        BigDecimal aAhorro = resto.min(cuota.getAhorroLetras().subtract(cuota.getAhorroPagado()));
        BigDecimal aMora = resto.subtract(aAhorro);

        PagoCredito pago = pagos.save(new PagoCredito(null, c.getId(), cuota.getId(), operacion, monto, ahora));
        detalle(pago, "CAPITAL", aCapital);
        detalle(pago, "INTERES", aInteres);
        detalle(pago, "FONDO_DESGRAVAMEN", aDesgravamen);
        detalle(pago, "AHORRO_LETRAS", aAhorro);
        detalle(pago, "MORA", aMora);
        ganancia(aInteres, "Interés del crédito #" + c.getId() + ", letra " + cuota.getNumero(), actorId);
        ganancia(aMora, "Mora del crédito #" + c.getId() + ", letra " + cuota.getNumero(), actorId);

        if (aAhorro.signum() > 0) {
            AhorroLetrasCredito ahorro = ahorros.findByCreditoId(c.getId())
                    .orElseThrow(() -> new IllegalStateException("Crédito sin ahorro letras: " + c.getId()));
            ahorro.setSaldo(ahorro.getSaldo().add(aAhorro));
            movimientosAhorro.save(new MovimientoAhorroLetras(null, c.getId(), cuota.getId(), "ACUMULACION", aAhorro, ahora));
        }
        cuota.setCapitalPagado(cuota.getCapitalPagado().add(aCapital));
        cuota.setInteresPagado(cuota.getInteresPagado().add(aInteres));
        cuota.setDesgravamenPagado(cuota.getDesgravamenPagado().add(aDesgravamen));
        cuota.setAhorroPagado(cuota.getAhorroPagado().add(aAhorro));
        cuota.setMoraPagada(cuota.getMoraPagada().add(aMora));
        c.setCapitalPendiente(c.getCapitalPendiente().subtract(aCapital));
        if (cuota.pendiente().signum() == 0) {
            // Pagada la letra, la mora deja de correr aunque quede mora por cobrar.
            if (cuota.getMoraHasta() == null) {
                cuota.setMoraHasta(hoy);
            }
            if (cuota.moraPendiente(hoy, c.getTasaMora()).signum() == 0) {
                cuota.setPagadaEn(ahora);
            }
        }
    }

    private void finalizar(CuentaPrincipal cuenta, Credito c, Long operacion, Long cuotaId, Instant ahora, Long actorId) {
        if (LETRAS.equals(c.getFamilia())) {
            liberarAhorro(cuenta, c, operacion, cuotaId, ahora);
            liberarEncaje(cuenta, c, operacion, ahora);
        }
        c.setEstado(FINALIZADO);
        c.setFinalizadoEn(ahora);
        auditoria.registrar(actorId, "CREDITO_FINALIZADO", MODULO, "credito", c.getId(), ACTIVO, FINALIZADO, null);
    }

    /** Definido por el propietario: con la mitad más una de las letras pagadas, se termina pagando solo el capital. */
    @Transactional
    public Long precancelar(Long usuarioId, Long creditoId, String clave) {
        Long personaId = personaDe(usuarioId);
        Credito leido = creditos.findById(creditoId).filter(x -> x.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "PAGO_CREDITO", null, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        Credito c = bloquear(leido.getId());
        if (!ACTIVO.equals(c.getEstado()) || !LETRAS.equals(c.getFamilia())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Este crédito no admite precancelación");
        }
        List<CuotaCredito> todas = cuotas.findByCreditoIdOrderByNumero(c.getId());
        long pagadas = todas.stream().filter(q -> q.getPagadaEn() != null).count();
        if (pagadas < letrasParaPrecancelar(todas.size())) {
            throw NegocioException.conflicto("PRECANCELACION_NO_DISPONIBLE",
                    "Puede precancelar cuando haya pagado " + letrasParaPrecancelar(todas.size()) + " letras");
        }
        BigDecimal capital = c.getCapitalPendiente();
        if (capital.signum() <= 0) {
            throw NegocioException.conflicto("SIN_CAPITAL_PENDIENTE", "No queda capital por precancelar: pague lo que falta de la letra");
        }
        Instant ahora = Instant.now(reloj);
        LocalDate hoy = hoy();
        // Lo que no se perdona es la mora ya generada: el atraso sí ocurrió.
        BigDecimal mora = BigDecimal.ZERO;
        for (CuotaCredito q : todas) {
            BigDecimal deMora = q.getPagadaEn() == null ? q.moraPendiente(hoy, c.getTasaMora()) : BigDecimal.ZERO;
            q.setMoraPagada(q.getMoraPagada().add(deMora));
            mora = mora.add(deMora);
        }
        Long operacion = cuentas.crearOperacionInterna("PAGO_CREDITO", capital.add(mora), clave, usuarioId);
        // Encaje y ahorro letras vuelven antes del cobro: esos fondos también pueden cubrir el capital.
        liberarAhorro(cuenta, c, operacion, null, ahora);
        liberarEncaje(cuenta, c, operacion, ahora);
        cuentas.mover(cuenta, operacion, SALIDA, capital.add(mora), "PRECANCELACION_CREDITO");
        PagoCredito pago = pagos.save(new PagoCredito(null, c.getId(), null, operacion, capital.add(mora), ahora));
        detalle(pago, "CAPITAL", capital);
        detalle(pago, "MORA", mora);
        ganancia(mora, "Mora del crédito #" + c.getId(), usuarioId);
        c.setCapitalPendiente(BigDecimal.ZERO.setScale(2));
        c.setPrecanceladoEn(ahora);
        c.setEstado(FINALIZADO);
        c.setFinalizadoEn(ahora);
        auditoria.registrar(usuarioId, "CREDITO_PRECANCELADO", MODULO, "credito", c.getId(), ACTIVO, FINALIZADO, capital.toPlainString());
        return operacion;
    }

    /** Mitad redondeada hacia abajo, más una: 12 letras exigen 7 y 5 letras exigen 3. */
    static int letrasParaPrecancelar(int letras) {
        return letras / 2 + 1;
    }

    private void liberarAhorro(CuentaPrincipal cuenta, Credito c, Long operacion, Long cuotaId, Instant ahora) {
        AhorroLetrasCredito ahorro = ahorros.findByCreditoId(c.getId())
                .orElseThrow(() -> new IllegalStateException("Crédito sin ahorro letras: " + c.getId()));
        if (ahorro.getSaldo().signum() > 0) {
            cuentas.mover(cuenta, operacion, ENTRADA, ahorro.getSaldo(), "LIBERACION_AHORRO_LETRAS");
            movimientosAhorro.save(new MovimientoAhorroLetras(null, c.getId(), cuotaId, "LIBERACION", ahorro.getSaldo(), ahora));
            ahorro.setSaldo(BigDecimal.ZERO.setScale(2));
            ahorro.setLiberadoEn(ahora);
        }
    }

    private void liberarEncaje(CuentaPrincipal cuenta, Credito c, Long operacion, Instant ahora) {
        EncajeCredito encaje = encajes.findByCreditoId(c.getId())
                .orElseThrow(() -> new IllegalStateException("Crédito sin encaje: " + c.getId()));
        if (encaje.getDevueltoEn() == null) {
            if (encaje.getImporte().signum() > 0) {
                cuentas.mover(cuenta, operacion, ENTRADA, encaje.getImporte(), "DEVOLUCION_ENCAJE");
            }
            encaje.setDevueltoEn(ahora);
        }
    }

    private Long pagarLinea(CuentaPrincipal cuenta, Credito c, BigDecimal importe, String clave, Long actorId) {
        if (importe == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "IMPORTE_REQUERIDO", "Indique el importe a pagar");
        }
        devengar(c);
        BigDecimal interes = c.getInteresAcumulado().setScale(2, RoundingMode.HALF_UP);
        BigDecimal desgravamen = c.getDesgravamenAcumulado().setScale(2, RoundingMode.HALF_UP);
        BigDecimal adeudado = interes.add(desgravamen).add(c.getCapitalPendiente());
        if (importe.compareTo(adeudado) > 0) {
            throw NegocioException.conflicto("PAGO_EXCEDIDO", "El importe supera lo adeudado");
        }
        // Primero cargos, luego capital.
        BigDecimal aInteres = importe.min(interes);
        BigDecimal aDesgravamen = importe.subtract(aInteres).min(desgravamen);
        BigDecimal aCapital = importe.subtract(aInteres).subtract(aDesgravamen);

        Instant ahora = Instant.now(reloj);
        Long operacion = cuentas.crearOperacionInterna("PAGO_CREDITO", importe, clave, actorId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "PAGO_CREDITO");
        PagoCredito pago = pagos.save(new PagoCredito(null, c.getId(), null, operacion, importe, ahora));
        detalle(pago, "INTERES", aInteres);
        detalle(pago, "FONDO_DESGRAVAMEN", aDesgravamen);
        detalle(pago, "CAPITAL", aCapital);
        ganancia(aInteres, "Interés del crédito #" + c.getId(), actorId);

        boolean saldado = importe.compareTo(adeudado) == 0;
        c.setInteresAcumulado(saldado ? BigDecimal.ZERO : c.getInteresAcumulado().subtract(aInteres).max(BigDecimal.ZERO));
        c.setDesgravamenAcumulado(saldado ? BigDecimal.ZERO : c.getDesgravamenAcumulado().subtract(aDesgravamen).max(BigDecimal.ZERO));
        c.setCapitalPendiente(c.getCapitalPendiente().subtract(aCapital));
        return operacion;
    }

    /** El interés cobrado es ganancia; el desgravamen no: es un fondo que se debe. */
    private void ganancia(BigDecimal interes, String concepto, Long actorId) {
        if (interes.signum() > 0) {
            eventos.publishEvent(new ec.janery.common.ResultadoGenerado("GANANCIA", interes, concepto, actorId));
        }
    }

    private void detalle(PagoCredito pago, String concepto, BigDecimal importe) {
        if (importe.signum() > 0) {
            detalles.save(new DetallePago(null, pago.getId(), concepto, importe));
        }
    }

    private void devengar(Credito c) {
        LocalDate hoy = hoy();
        long dias = ChronoUnit.DAYS.between(c.getUltimoDevengo(), hoy);
        if (dias > 0) {
            c.setInteresAcumulado(c.getInteresAcumulado().add(
                    CalculadoraCredito.devengo(c.getCapitalPendiente(), c.getTasaInteres(), dias)));
            c.setDesgravamenAcumulado(c.getDesgravamenAcumulado().add(
                    CalculadoraCredito.devengo(c.getCapitalPendiente(), c.getTasaDesgravamen(), dias)));
            c.setUltimoDevengo(hoy);
        }
    }

    // --- Consultas

    /** Cuotas sin pagar con el vencimiento ya pasado, la más antigua primero. El cupo de socio no tiene vencimiento. */
    @Transactional(readOnly = true)
    public List<CuotaVencida> cuotasVencidas(Long personaId) {
        return cuotas.vencidas(personaId, hoy()).stream().map(q -> {
            Credito c = creditos.findById(q.getCreditoId()).orElseThrow();
            return new CuotaVencida(q.getCreditoId(), q.getId(), q.getNumero(), q.getVencimiento(), c.getFamilia(),
                    c.getModalidad(), deudaDe(q, c, hoy()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CreditoVista> misCreditos(Long usuarioId) {
        return vistas(creditos.findByPersonaIdOrderByIdDesc(personaDe(usuarioId)));
    }

    @Transactional(readOnly = true)
    public CreditoVista detalle(Long usuarioId, Long creditoId) {
        Long personaId = personaDe(usuarioId);
        return creditos.findById(creditoId).filter(c -> c.getPersonaId().equals(personaId)).map(this::vista)
                .orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
    }

    @Transactional(readOnly = true)
    public Page<CreditoVista> listar(String estado, int pagina, int tamano) {
        Pageable pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id"));
        return (estado == null ? creditos.findAll(pageable) : creditos.findByEstado(estado, pageable)).map(this::vista);
    }

    private CreditoVista vista(Credito c) {
        return vistas(List.of(c)).get(0);
    }

    /** Lee cuotas, encajes y ahorros de todos los créditos de una vez: cada consulta es un viaje a la base de datos. */
    private List<CreditoVista> vistas(List<Credito> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lista.stream().map(Credito::getId).toList();
        Map<Long, List<CuotaCredito>> porCredito = cuotas.findByCreditoIdInOrderByNumero(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(CuotaCredito::getCreditoId));
        Map<Long, EncajeCredito> encajesPorCredito = encajes.findByCreditoIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(EncajeCredito::getCreditoId, e -> e));
        Map<Long, BigDecimal> ahorrosPorCredito = ahorros.findByCreditoIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(AhorroLetrasCredito::getCreditoId, AhorroLetrasCredito::getSaldo));
        return lista.stream().map(c -> vista(c, porCredito.getOrDefault(c.getId(), List.of()), encajesPorCredito.get(c.getId()),
                ahorrosPorCredito.get(c.getId()))).toList();
    }

    private CreditoVista vista(Credito c, List<CuotaCredito> propias, EncajeCredito encaje, BigDecimal ahorro) {
        BigDecimal interes = c.getInteresAcumulado();
        BigDecimal desgravamen = c.getDesgravamenAcumulado();
        if (esLinea(c)) {
            long dias = ChronoUnit.DAYS.between(c.getUltimoDevengo(), hoy());
            interes = interes.add(CalculadoraCredito.devengo(c.getCapitalPendiente(), c.getTasaInteres(), dias));
            desgravamen = desgravamen.add(CalculadoraCredito.devengo(c.getCapitalPendiente(), c.getTasaDesgravamen(), dias));
        }
        LocalDate hoy = hoy();
        boolean corre = ACTIVO.equals(c.getEstado());
        List<CuotaVista> letras = propias.stream().map(q -> new CuotaVista(q.getNumero(), q.getVencimiento(), q.getCapital(),
                q.getAhorroLetras(), q.getFondoDesgravamen(), q.getInteres(), q.getTotal(), q.pagado(),
                corre ? q.diasAtraso(hoy) : 0, corre ? q.moraPendiente(hoy, c.getTasaMora()) : BigDecimal.ZERO.setScale(2))).toList();
        BigDecimal mora = letras.stream().map(CuotaVista::mora).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        int atrasadas = (int) letras.stream().filter(q -> q.diasAtraso() > 0).count();
        List<Integer> pagadas = propias.stream().filter(q -> q.getPagadaEn() != null).map(CuotaCredito::getNumero).toList();
        BigDecimal pendiente = esLinea(c)
                ? interes.setScale(2, RoundingMode.HALF_UP).add(desgravamen.setScale(2, RoundingMode.HALF_UP)).add(c.getCapitalPendiente())
                : c.getPrecanceladoEn() != null ? BigDecimal.ZERO.setScale(2) : propias.stream().map(CuotaCredito::pendiente).reduce(BigDecimal.ZERO, BigDecimal::add).add(mora);
        int requeridas = letrasParaPrecancelar(letras.size());
        return new CreditoVista(c.getId(), c.getFamilia(), c.getModalidad(), c.getPerfil(), c.getEstado(), c.getImporte(),
                c.getPlazoMeses(), c.getTasaInteres(), c.getTasaDesgravamen(), c.getUnidadTasa(), c.getCapitalPendiente(),
                interes.setScale(2, RoundingMode.HALF_UP), desgravamen.setScale(2, RoundingMode.HALF_UP), pendiente,
                encaje == null ? null : encaje.getImporte(), encaje != null && encaje.getDevueltoEn() != null, ahorro,
                personas.nombreCompleto(c.getPersonaId()), c.getMotivoRechazo(), letras, pagadas, requeridas,
                LETRAS.equals(c.getFamilia()) && ACTIVO.equals(c.getEstado()) && !letras.isEmpty() && pagadas.size() >= requeridas
                        && c.getCapitalPendiente().signum() > 0,
                c.getPrecanceladoEn(), c.getCreadoEn(), c.getFinalizadoEn(), c.getOrigenEncaje(), c.getTasaMora(), mora, atrasadas);
    }

    // --- Apoyo

    private void guardarCuotas(Long creditoId, List<Letra> letras) {
        letras.forEach(l -> cuotas.save(new CuotaCredito(creditoId, l.numero(), l.vencimiento(), l.capital(),
                l.ahorroLetras(), l.fondoDesgravamen(), l.interes(), l.total())));
    }

    private Credito nuevo(Long personaId, String familia, String modalidad, String perfil, String estado, String unidad,
            BigDecimal tasa, BigDecimal desgravamen) {
        Credito c = new Credito();
        c.setPersonaId(personaId);
        c.setFamilia(familia);
        c.setModalidad(modalidad);
        c.setPerfil(perfil);
        c.setEstado(estado);
        c.setUnidadTasa(unidad);
        c.setTasaInteres(tasa);
        c.setTasaDesgravamen(desgravamen);
        c.setCreadoEn(Instant.now(reloj));
        return c;
    }

    private void validarLetras(BigDecimal importe, int plazo) {
        if (importe.compareTo(parametros.valorVigente("LETRAS_IMPORTE_MIN")) < 0
                || importe.compareTo(parametros.valorVigente("LETRAS_IMPORTE_MAX")) > 0) {
            throw NegocioException.conflicto("IMPORTE_FUERA_DE_RANGO", "El importe está fuera de los límites permitidos");
        }
        if (plazo < parametros.valorVigente("LETRAS_PLAZO_MIN").intValue()
                || plazo > parametros.valorVigente("LETRAS_PLAZO_MAX").intValue()) {
            throw NegocioException.conflicto("PLAZO_FUERA_DE_RANGO", "El plazo está fuera de los límites permitidos");
        }
    }

    private static boolean esLinea(Credito c) {
        return EMERGENTE_SOCIO.equals(c.getFamilia()) || EMERGENTE_ESTACIONAL.equals(c.getFamilia());
    }

    private static void exigirSolicitado(Credito c) {
        if (!SOLICITADO.equals(c.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "El crédito no está pendiente de aprobación");
        }
    }

    /**
     * Si la fila ya estaba cargada en esta transacción, la consulta con bloqueo devuelve esa copia antigua: hay que releerla.
     * Sin esto, quien esperó el bloqueo pisaría lo que acaba de guardar la operación anterior.
     */
    private Credito bloquear(Long id) {
        Credito c = creditos.bloquear(id).orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
        em.refresh(c);
        return c;
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "Complete sus datos personales")).getId();
    }

    private String perfil(Long personaId) {
        return socios.esSocio(personaId) ? SOCIO : CLIENTE;
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }
}
