package ec.janery.credito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

import ec.janery.auditoria.AuditoriaService;
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
                where c.personaId = :personaId and c.familia = 'EMERGENTE_CLIENTE' and c.estado = 'ACTIVO'
                """)
        boolean tieneEmergenteClienteAbierto(@Param("personaId") Long personaId);
    }

    interface CuotaRepository extends JpaRepository<CuotaCredito, Long> {
        List<CuotaCredito> findByCreditoIdOrderByNumero(Long creditoId);
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
    }

    interface AhorroLetrasRepository extends JpaRepository<AhorroLetrasCredito, Long> {
        Optional<AhorroLetrasCredito> findByCreditoId(Long creditoId);
    }

    interface MovimientoAhorroLetrasRepository extends JpaRepository<MovimientoAhorroLetras, Long> {
    }

    /** Estado del crédito con cargos devengados hasta hoy, sin guardar. */
    public record CreditoVista(Long id, String familia, String modalidad, String perfil, String estado,
            BigDecimal importe, Integer plazoMeses, BigDecimal tasaInteres, BigDecimal tasaDesgravamen,
            String unidadTasa, BigDecimal capitalPendiente, BigDecimal interes, BigDecimal desgravamen,
            BigDecimal totalPendiente, BigDecimal encaje, boolean encajeDevuelto, BigDecimal ahorroLetras,
            String titular, String motivoRechazo, List<Letra> cuotas, List<Integer> cuotasPagadas) {
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
    private static final List<String> MODALIDADES = List.of("NORMAL", "INVERSION", "ESTUDIO", "PERSONALIZADO");

    private final CreditoRepository creditos;
    private final CuotaRepository cuotas;
    private final DesembolsoRepository desembolsos;
    private final PagoRepository pagos;
    private final DetalleRepository detalles;
    private final EncajeRepository encajes;
    private final AhorroLetrasRepository ahorros;
    private final MovimientoAhorroLetrasRepository movimientosAhorro;
    private final CuentaService cuentas;
    private final PersonaService personas;
    private final SocioService socios;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    // --- Consulta de productos

    @Transactional(readOnly = true)
    public Productos productos(Long usuarioId) {
        Long personaId = personaDe(usuarioId);
        String perfil = perfil(personaId);
        List<ProductoLetras> letras = MODALIDADES.stream().map(m -> new ProductoLetras(m,
                parametros.valorVigente("LETRAS_INTERES_%s_%s".formatted(m, perfil)),
                parametros.valorVigente("LETRAS_ENCAJE_%s_%s".formatted(m, perfil)))).toList();
        boolean socio = SOCIO.equals(perfil);
        BigDecimal limite = parametros.valorVigente(socio ? "EMERGENTE_SOCIO_CUPO" : "EMERGENTE_CLIENTE_LIMITE");
        BigDecimal utilizado = socio ? creditos.cupoUtilizado(personaId) : BigDecimal.ZERO;
        return new Productos(perfil, letras, parametros.valorVigente("LETRAS_DESGRAVAMEN"),
                parametros.valorVigente("LETRAS_IMPORTE_MIN"), parametros.valorVigente("LETRAS_IMPORTE_MAX"),
                parametros.valorVigente("LETRAS_PLAZO_MIN").intValue(), parametros.valorVigente("LETRAS_PLAZO_MAX").intValue(),
                limite, utilizado, limite.subtract(utilizado).max(BigDecimal.ZERO),
                parametros.valorVigente(socio ? "EMERGENTE_SOCIO_INTERES" : "EMERGENTE_CLIENTE_INTERES"),
                parametros.valorVigente(socio ? "EMERGENTE_SOCIO_DESGRAVAMEN" : "EMERGENTE_CLIENTE_DESGRAVAMEN"),
                socio ? PCT_DIARIO : PCT_MENSUAL,
                socio ? promocionAbierta() : null,
                parametros.valorVigente("ESTACIONAL_LIMITE"), parametros.valorVigente("ESTACIONAL_TASA"));
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

    /** Las tasas quedan fijadas al solicitar; las fechas de las letras, al aprobar. */
    @Transactional
    public Long solicitarLetras(Long usuarioId, String modalidad, BigDecimal importe, int plazo) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        String perfil = perfil(personaId);
        validarLetras(importe, plazo);
        Credito c = nuevo(personaId, LETRAS, modalidad, perfil, SOLICITADO, PCT_MENSUAL,
                parametros.valorVigente("LETRAS_INTERES_%s_%s".formatted(modalidad, perfil)),
                parametros.valorVigente("LETRAS_DESGRAVAMEN"));
        c.setImporte(importe);
        c.setPlazoMeses(plazo);
        c.setPorcentajeEncaje(parametros.valorVigente("LETRAS_ENCAJE_%s_%s".formatted(modalidad, perfil)));
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
        Instant ahora = Instant.now(reloj);

        Long desembolso = cuentas.crearOperacionInterna("DESEMBOLSO", c.getImporte(), null, actorId);
        cuentas.mover(cuenta, desembolso, ENTRADA, c.getImporte(), "DESEMBOLSO_CREDITO");
        desembolsos.save(new DesembolsoCredito(null, c.getId(), desembolso, c.getImporte(), ahora));

        BigDecimal encaje = CalculadoraCredito.porcentaje(c.getImporte(), c.getPorcentajeEncaje());
        if (encaje.signum() > 0) {
            cuentas.mover(cuenta, desembolso, SALIDA, encaje, "ENCAJE_CREDITO");
        }
        encajes.save(new EncajeCredito(null, c.getId(), desembolso, encaje, ahora, null));
        ahorros.save(new AhorroLetrasCredito(null, c.getId(), BigDecimal.ZERO.setScale(2), null));

        c.setFechaInicio(hoy());
        guardarCuotas(c.getId(), CalculadoraCredito.letras(c.getImporte(), c.getPlazoMeses(), c.getTasaInteres(),
                c.getTasaDesgravamen(), c.getFechaInicio()));
        c.setCapitalPendiente(c.getImporte());
        c.setEstado(ACTIVO);
        auditoria.registrar(actorId, "CREDITO_APROBADO", MODULO, "credito", c.getId(), SOLICITADO, ACTIVO, null);
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

    /** Emergente de cliente: se desembolsa al momento, dentro del límite y de la frecuencia mensual. */
    @Transactional
    public Long solicitarEmergenteCliente(Long usuarioId, BigDecimal importe, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "DESEMBOLSO", importe, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
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
        LocalDate hoy = hoy();
        long usados = creditos.emergentesClienteEnPeriodo(personaId, hoy.withDayOfMonth(1), hoy.withDayOfMonth(hoy.lengthOfMonth()));
        if (usados >= parametros.valorVigente("EMERGENTE_CLIENTE_FRECUENCIA").longValue()) {
            throw NegocioException.conflicto("FRECUENCIA_EXCEDIDA", "Ya utilizó el emergente este mes");
        }
        BigDecimal tasa = parametros.valorVigente("EMERGENTE_CLIENTE_INTERES");
        BigDecimal desgravamen = parametros.valorVigente("EMERGENTE_CLIENTE_DESGRAVAMEN");
        Credito c = nuevo(personaId, EMERGENTE_CLIENTE, null, CLIENTE, ACTIVO, PCT_MENSUAL, tasa, desgravamen);
        c.setImporte(importe);
        c.setPlazoMeses(1);
        c.setCapitalPendiente(importe);
        c.setFechaInicio(hoy);
        creditos.save(c);

        BigDecimal interes = CalculadoraCredito.porcentaje(importe, tasa);
        BigDecimal cargoDesgravamen = CalculadoraCredito.porcentaje(importe, desgravamen);
        cuotas.save(new CuotaCredito(null, c.getId(), 1, hoy.plusMonths(1), importe, BigDecimal.ZERO.setScale(2),
                cargoDesgravamen, interes, importe.add(interes).add(cargoDesgravamen), null));
        return desembolsar(cuenta, c, importe, clave, usuarioId);
    }

    /** Disposición del cupo de socio, general o de una promoción abierta. Las promociones consumen el mismo cupo. */
    @Transactional
    public Long disponerCupo(Long usuarioId, BigDecimal importe, String promocion, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "DESEMBOLSO", importe, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
        }
        if (!socios.esSocio(personaId)) {
            throw NegocioException.conflicto("SOLO_SOCIOS", "El cupo emergente es exclusivo de socios");
        }
        BigDecimal disponible = parametros.valorVigente("EMERGENTE_SOCIO_CUPO").subtract(creditos.cupoUtilizado(personaId));
        if (importe.compareTo(disponible) > 0) {
            throw NegocioException.conflicto("CUPO_EXCEDIDO", "El importe supera su cupo disponible");
        }
        Credito c = promocion == null ? lineaGeneral(personaId) : lineaPromocion(personaId, promocion, importe);
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
        return desembolsar(cuenta, c, importe, clave, usuarioId);
    }

    private Credito lineaGeneral(Long personaId) {
        return creditos.findByPersonaIdAndFamilia(personaId, EMERGENTE_SOCIO).orElseGet(() -> linea(
                nuevo(personaId, EMERGENTE_SOCIO, null, SOCIO, ACTIVO, PCT_DIARIO,
                        parametros.valorVigente("EMERGENTE_SOCIO_INTERES"),
                        parametros.valorVigente("EMERGENTE_SOCIO_DESGRAVAMEN"))));
    }

    private Credito lineaPromocion(Long personaId, String promocion, BigDecimal importe) {
        if (!promocion.equals(promocionAbierta())) {
            throw NegocioException.conflicto("PROMOCION_CERRADA", "Esa promoción no está abierta este mes");
        }
        int anio = hoy().getYear();
        Credito c = creditos.findByPersonaIdAndFamiliaAndModalidadAndAnioPromocion(personaId, EMERGENTE_ESTACIONAL,
                promocion, anio).orElseGet(() -> {
                    Credito nuevo = nuevo(personaId, EMERGENTE_ESTACIONAL, promocion, SOCIO, ACTIVO, PCT_DIARIO,
                            parametros.valorVigente("ESTACIONAL_TASA"), BigDecimal.ZERO);
                    nuevo.setAnioPromocion(anio);
                    return linea(nuevo);
                });
        // El límite es por promoción: lo ya retirado cuenta aunque se haya pagado.
        if (desembolsos.totalDe(c.getId()).add(importe).compareTo(parametros.valorVigente("ESTACIONAL_LIMITE")) > 0) {
            throw NegocioException.conflicto("LIMITE_EXCEDIDO", "El importe supera el máximo de la promoción");
        }
        return c;
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

    /** Letras y emergente de cliente: se paga la siguiente cuota completa. Cupo: cualquier importe hasta lo adeudado. */
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
                : pagarCuota(cuenta, c, usarAhorroLetras, usarEncaje, clave, usuarioId);
        auditoria.registrar(usuarioId, "CREDITO_PAGO", MODULO, "credito", c.getId(), null, operacion.toString(), null);
        return operacion;
    }

    private Long pagarCuota(CuentaPrincipal cuenta, Credito c, boolean usarAhorro, boolean usarEncaje, String clave,
            Long actorId) {
        List<CuotaCredito> todas = cuotas.findByCreditoIdOrderByNumero(c.getId());
        CuotaCredito cuota = todas.stream().filter(q -> q.getPagadaEn() == null).findFirst()
                .orElseThrow(() -> new IllegalStateException("Crédito activo sin cuotas pendientes: " + c.getId()));
        boolean ultima = cuota.getNumero().equals(todas.size());
        if ((usarAhorro || usarEncaje) && (!ultima || !LETRAS.equals(c.getFamilia()))) {
            throw NegocioException.conflicto("SOLO_ULTIMA_LETRA", "Ahorro letras y encaje solo pueden cubrir la última letra");
        }
        Instant ahora = Instant.now(reloj);
        Long operacion = cuentas.crearOperacionInterna("PAGO_CREDITO", cuota.getTotal(), clave, actorId);
        // Liberar antes del cobro hace que esos fondos cubran la letra; el remanente queda en la cuenta principal.
        if (usarAhorro) {
            liberarAhorro(cuenta, c, operacion, null, ahora);
        }
        if (usarEncaje) {
            liberarEncaje(cuenta, c, operacion, ahora);
        }
        cuentas.mover(cuenta, operacion, SALIDA, cuota.getTotal(), "PAGO_CREDITO");

        PagoCredito pago = pagos.save(new PagoCredito(null, c.getId(), cuota.getId(), operacion, cuota.getTotal(), ahora));
        detalle(pago, "CAPITAL", cuota.getCapital());
        detalle(pago, "INTERES", cuota.getInteres());
        detalle(pago, "FONDO_DESGRAVAMEN", cuota.getFondoDesgravamen());
        detalle(pago, "AHORRO_LETRAS", cuota.getAhorroLetras());

        if (cuota.getAhorroLetras().signum() > 0) {
            AhorroLetrasCredito ahorro = ahorros.findByCreditoId(c.getId())
                    .orElseThrow(() -> new IllegalStateException("Crédito sin ahorro letras: " + c.getId()));
            ahorro.setSaldo(ahorro.getSaldo().add(cuota.getAhorroLetras()));
            movimientosAhorro.save(new MovimientoAhorroLetras(null, c.getId(), cuota.getId(), "ACUMULACION",
                    cuota.getAhorroLetras(), ahora));
        }
        cuota.setPagadaEn(ahora);
        c.setCapitalPendiente(c.getCapitalPendiente().subtract(cuota.getCapital()));
        if (ultima) {
            if (LETRAS.equals(c.getFamilia())) {
                liberarAhorro(cuenta, c, operacion, cuota.getId(), ahora);
                liberarEncaje(cuenta, c, operacion, ahora);
            }
            c.setEstado(FINALIZADO);
            c.setFinalizadoEn(ahora);
            auditoria.registrar(actorId, "CREDITO_FINALIZADO", MODULO, "credito", c.getId(), ACTIVO, FINALIZADO, null);
        }
        return operacion;
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

        boolean saldado = importe.compareTo(adeudado) == 0;
        c.setInteresAcumulado(saldado ? BigDecimal.ZERO : c.getInteresAcumulado().subtract(aInteres).max(BigDecimal.ZERO));
        c.setDesgravamenAcumulado(saldado ? BigDecimal.ZERO : c.getDesgravamenAcumulado().subtract(aDesgravamen).max(BigDecimal.ZERO));
        c.setCapitalPendiente(c.getCapitalPendiente().subtract(aCapital));
        return operacion;
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

    @Transactional(readOnly = true)
    public List<CreditoVista> misCreditos(Long usuarioId) {
        return creditos.findByPersonaIdOrderByIdDesc(personaDe(usuarioId)).stream().map(this::vista).toList();
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
        BigDecimal interes = c.getInteresAcumulado();
        BigDecimal desgravamen = c.getDesgravamenAcumulado();
        if (esLinea(c)) {
            long dias = ChronoUnit.DAYS.between(c.getUltimoDevengo(), hoy());
            interes = interes.add(CalculadoraCredito.devengo(c.getCapitalPendiente(), c.getTasaInteres(), dias));
            desgravamen = desgravamen.add(CalculadoraCredito.devengo(c.getCapitalPendiente(), c.getTasaDesgravamen(), dias));
        }
        List<CuotaCredito> propias = cuotas.findByCreditoIdOrderByNumero(c.getId());
        List<Letra> letras = propias.stream().map(q -> new Letra(q.getNumero(), q.getVencimiento(), q.getCapital(),
                q.getAhorroLetras(), q.getFondoDesgravamen(), q.getInteres(), q.getTotal())).toList();
        List<Integer> pagadas = propias.stream().filter(q -> q.getPagadaEn() != null).map(CuotaCredito::getNumero).toList();
        BigDecimal pendiente = esLinea(c)
                ? interes.setScale(2, RoundingMode.HALF_UP).add(desgravamen.setScale(2, RoundingMode.HALF_UP)).add(c.getCapitalPendiente())
                : propias.stream().filter(q -> q.getPagadaEn() == null).map(CuotaCredito::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        EncajeCredito encaje = encajes.findByCreditoId(c.getId()).orElse(null);
        BigDecimal ahorro = ahorros.findByCreditoId(c.getId()).map(AhorroLetrasCredito::getSaldo).orElse(null);
        return new CreditoVista(c.getId(), c.getFamilia(), c.getModalidad(), c.getPerfil(), c.getEstado(), c.getImporte(),
                c.getPlazoMeses(), c.getTasaInteres(), c.getTasaDesgravamen(), c.getUnidadTasa(), c.getCapitalPendiente(),
                interes.setScale(2, RoundingMode.HALF_UP), desgravamen.setScale(2, RoundingMode.HALF_UP), pendiente,
                encaje == null ? null : encaje.getImporte(), encaje != null && encaje.getDevueltoEn() != null, ahorro,
                personas.nombreCompleto(c.getPersonaId()), c.getMotivoRechazo(), letras, pagadas);
    }

    // --- Apoyo

    private void guardarCuotas(Long creditoId, List<Letra> letras) {
        letras.forEach(l -> cuotas.save(new CuotaCredito(null, creditoId, l.numero(), l.vencimiento(), l.capital(),
                l.ahorroLetras(), l.fondoDesgravamen(), l.interes(), l.total(), null)));
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

    private Credito bloquear(Long id) {
        return creditos.bloquear(id).orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
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
