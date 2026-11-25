package ec.janery.ahorro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.InteresDiario;
import ec.janery.common.NegocioException;
import ec.janery.cuenta.CuentaPrincipal;
import ec.janery.cuenta.CuentaService;
import ec.janery.cuenta.RestriccionCierre;
import ec.janery.parametros.ParametroService;
import ec.janery.persona.PersonaService;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** Ahorro normal y plazo fijo. Los fondos vienen de la cuenta principal y vuelven a ella; la caja no cambia. */
@Service
@RequiredArgsConstructor
public class AhorroService {

    @Entity(name = "CuentaAhorro")
    @Table(name = "cuentas_ahorro")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class CuentaAhorro {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long personaId;
        private String tipo;
        private String nombre;
        private String estado;
        private BigDecimal saldo;
        private BigDecimal tasaAnual;
        private Integer plazoMeses;
        private LocalDate venceEn;
        private Instant abiertaEn;
        private Instant cerradaEn;
        private BigDecimal interesAcumulado;
        private BigDecimal interesDisponible;
        private LocalDate ultimoDevengo;
        /** Solo en los ahorros normales abiertos con clave de idempotencia. */
        private String claveCreacion;
    }

    @Entity(name = "MovimientoAhorro")
    @Table(name = "movimientos_ahorro")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class MovimientoAhorro {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long cuentaAhorroId;
        private Long operacionId;
        private String sentido;
        private BigDecimal importe;
        private String concepto;
        private BigDecimal saldoResultante;
        private String periodoInteres;
        private BigDecimal tasaAplicada;
        private Instant creadoEn;
    }

    interface CuentaAhorroRepository extends JpaRepository<CuentaAhorro, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select c from CuentaAhorro c where c.id = :id")
        Optional<CuentaAhorro> bloquear(@Param("id") Long id);

        List<CuentaAhorro> findByPersonaIdOrderByIdDesc(Long personaId);

        Optional<CuentaAhorro> findByClaveCreacion(String clave);

        @Query("""
                select count(c) > 0 from CuentaAhorro c where c.personaId = :personaId and c.estado = 'ACTIVA'
                and (c.saldo > 0 or c.interesAcumulado + c.interesDisponible >= 0.01)
                """)
        boolean tieneSaldo(@Param("personaId") Long personaId);
    }

    interface MovimientoAhorroRepository extends JpaRepository<MovimientoAhorro, Long> {
        List<MovimientoAhorro> findByCuentaAhorroIdOrderByIdDesc(Long cuentaAhorroId);

        Optional<MovimientoAhorro> findFirstByOperacionId(Long operacionId);
    }

    public record AhorroVista(Long id, String tipo, String nombre, String estado, BigDecimal saldo, BigDecimal tasaAnual,
            Integer plazoMeses, LocalDate venceEn, BigDecimal interesAlVencimiento, boolean retirable,
            BigDecimal interesAcumulado, BigDecimal interesDisponible, LocalDate proximaLiberacion, String codigo) {
    }

    public record PlazoVista(int meses, BigDecimal tasaAnual) {
    }

    public record Productos(BigDecimal tasaNormal, List<PlazoVista> plazos) {
    }

    public record MovimientoVista(Long id, String sentido, BigDecimal importe, String concepto,
            BigDecimal saldoResultante, Instant fecha) {
    }

    private static final String NORMAL = "NORMAL";
    private static final String PLAZO_FIJO = "PLAZO_FIJO";
    private static final String ACTIVA = "ACTIVA";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";
    private static final String MODULO = "AHORRO";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal DOCE = BigDecimal.valueOf(12);
    private static final BigDecimal CERO = InteresDiario.CERO;
    private static final String TASA_NORMAL = "AHORRO_NORMAL_TASA";
    /** Definido por el propietario: solo estos plazos, cada uno con su tasa. */
    private static final List<Integer> PLAZOS = List.of(3, 6, 9, 12, 24);

    private final CuentaAhorroRepository ahorros;
    private final MovimientoAhorroRepository movimientos;
    private final CuentaService cuentas;
    private final PersonaService personas;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final InteresDiario interesDiario;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional
    public AhorroVista crearNormal(Long usuarioId, String nombre, String clave) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        // Tras el bloqueo: un doble clic espera aquí y recibe el ahorro ya abierto, no un segundo.
        Optional<CuentaAhorro> previo = ahorros.findByClaveCreacion(clave);
        if (previo.isPresent()) {
            if (!previo.get().getPersonaId().equals(personaId)) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return vista(previo.get());
        }
        CuentaAhorro a = ahorros.save(new CuentaAhorro(null, personaId, NORMAL, nombre, ACTIVA,
                BigDecimal.ZERO.setScale(2), null, null, null, Instant.now(reloj), null, CERO, CERO, LocalDate.now(reloj), clave));
        auditoria.registrar(usuarioId, "AHORRO_CREADO", MODULO, "ahorro", a.getId(), null, NORMAL, null);
        return vista(a);
    }

    @Transactional
    public AhorroVista crearPlazoFijo(Long usuarioId, String nombre, BigDecimal importe, int plazo, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "APERTURA_AHORRO", importe, usuarioId);
        if (previa.isPresent()) {
            // Un reintento recibe el mismo plazo fijo, como las demás operaciones con clave.
            return movimientos.findFirstByOperacionId(previa.get()).flatMap(m -> ahorros.findById(m.getCuentaAhorroId()))
                    .map(this::vista)
                    .orElseThrow(() -> new IllegalStateException("Apertura sin plazo fijo: operación " + previa.get()));
        }
        if (!PLAZOS.contains(plazo)) {
            throw NegocioException.conflicto("PLAZO_FUERA_DE_RANGO", "El plazo debe ser de 3, 6, 9, 12 o 24 meses");
        }
        CuentaAhorro a = ahorros.save(new CuentaAhorro(null, personaId, PLAZO_FIJO, nombre, ACTIVA, importe,
                parametros.valorVigente(parametroPlazo(plazo)), plazo, LocalDate.now(reloj).plusMonths(plazo),
                Instant.now(reloj), null, CERO, CERO, null, null));
        Long operacion = cuentas.crearOperacionInterna("APERTURA_AHORRO", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "APERTURA_PLAZO_FIJO");
        asiento(a, operacion, ENTRADA, importe, "APERTURA", null, null);
        auditoria.registrar(usuarioId, "AHORRO_CREADO", MODULO, "ahorro", a.getId(), null, PLAZO_FIJO, null);
        return vista(a);
    }

    @Transactional
    public Long depositar(Long usuarioId, Long ahorroId, BigDecimal importe, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "DEPOSITO_AHORRO", importe, usuarioId);
        if (previa.isPresent()) {
            return mismoAhorro(previa.get(), ahorroId);
        }
        CuentaAhorro a = propia(personaId, ahorroId);
        exigirActiva(a);
        if (!NORMAL.equals(a.getTipo())) {
            throw NegocioException.conflicto("PLAZO_FIJO_SIN_DEPOSITOS", "Un plazo fijo no admite depósitos adicionales");
        }
        devengar(a);
        Long operacion = cuentas.crearOperacionInterna("DEPOSITO_AHORRO", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "DEPOSITO_AHORRO");
        a.setSaldo(a.getSaldo().add(importe));
        asiento(a, operacion, ENTRADA, importe, "DEPOSITO", null, null);
        auditoria.registrar(usuarioId, "AHORRO_DEPOSITO", MODULO, "ahorro", a.getId(), null, importe.toPlainString(), null);
        return operacion;
    }

    /** Normal: cualquier importe del saldo; su interés va aparte. Plazo fijo: solo al vencer, todo el saldo más su interés. */
    @Transactional
    public Long retirar(Long usuarioId, Long ahorroId, BigDecimal importe, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "RETIRO_AHORRO", null, usuarioId);
        if (previa.isPresent()) {
            return mismoAhorro(previa.get(), ahorroId);
        }
        CuentaAhorro a = propia(personaId, ahorroId);
        exigirActiva(a);
        Long operacion = NORMAL.equals(a.getTipo())
                ? retirarNormal(cuenta, a, importe, clave, usuarioId)
                : liquidarPlazoFijo(cuenta, a, clave, usuarioId);
        auditoria.registrar(usuarioId, "AHORRO_RETIRO", MODULO, "ahorro", a.getId(), null, operacion.toString(), null);
        return operacion;
    }

    private Long retirarNormal(CuentaPrincipal cuenta, CuentaAhorro a, BigDecimal importe, String clave, Long actorId) {
        if (importe == null || importe.signum() <= 0) {
            throw NegocioException.conflicto("IMPORTE_REQUERIDO", "Indique el importe a retirar");
        }
        if (importe.compareTo(a.getSaldo()) > 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "El ahorro no tiene saldo suficiente");
        }
        devengar(a);
        Long operacion = cuentas.crearOperacionInterna("RETIRO_AHORRO", importe, clave, actorId);
        a.setSaldo(a.getSaldo().subtract(importe));
        asiento(a, operacion, SALIDA, importe, "RETIRO", null, null);
        cuentas.mover(cuenta, operacion, ENTRADA, importe, "RETIRO_AHORRO");
        return operacion;
    }

    private Long liquidarPlazoFijo(CuentaPrincipal cuenta, CuentaAhorro a, String clave, Long actorId) {
        if (LocalDate.now(reloj).isBefore(a.getVenceEn())) {
            throw NegocioException.conflicto("PLAZO_NO_VENCIDO", "No se puede retirar antes del " + a.getVenceEn());
        }
        BigDecimal interes = interesPlazoFijo(a);
        BigDecimal total = a.getSaldo().add(interes);
        Long operacion = cuentas.crearOperacionInterna("RETIRO_AHORRO", total, clave, actorId);
        BigDecimal saldo = a.getSaldo();
        if (interes.signum() > 0) {
            a.setSaldo(total);
            asiento(a, operacion, ENTRADA, interes, "INTERES", null, a.getTasaAnual());
            eventos.publishEvent(new ec.janery.common.ResultadoGenerado("PERDIDA", interes, "Interés del plazo fijo " + a.getNombre(), actorId));
        }
        a.setSaldo(BigDecimal.ZERO.setScale(2));
        asiento(a, operacion, SALIDA, saldo.add(interes), "LIQUIDACION", null, null);
        a.setEstado("CERRADA");
        a.setCerradaEn(Instant.now(reloj));
        cuentas.mover(cuenta, operacion, ENTRADA, total, "LIQUIDACION_PLAZO_FIJO");
        return operacion;
    }

    /** Lleva a la cuenta principal el interés ya liberado de un ahorro normal. Se contabiliza como gasto al pagarse. */
    @Transactional
    public Long retirarInteres(Long usuarioId, Long ahorroId, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "INTERES_AHORRO", null, usuarioId);
        if (previa.isPresent()) {
            return mismoAhorro(previa.get(), ahorroId);
        }
        CuentaAhorro a = propia(personaId, ahorroId);
        exigirActiva(a);
        if (!NORMAL.equals(a.getTipo())) {
            throw NegocioException.conflicto("INTERES_NO_DISPONIBLE", "El interés del plazo fijo se paga al terminar el contrato");
        }
        devengar(a);
        BigDecimal interes = a.getInteresDisponible().setScale(2, RoundingMode.DOWN);
        if (interes.signum() <= 0) {
            throw NegocioException.conflicto("INTERES_NO_DISPONIBLE", "Aún no hay interés liberado: se libera cada día 1");
        }
        Long operacion = cuentas.crearOperacionInterna("INTERES_AHORRO", interes, clave, usuarioId);
        a.setInteresDisponible(a.getInteresDisponible().subtract(interes));
        // Entra y sale en la misma operación: el saldo no cambia, y el libro registra el gasto y el pago.
        BigDecimal saldo = a.getSaldo();
        a.setSaldo(saldo.add(interes));
        asiento(a, operacion, ENTRADA, interes, "INTERES", null, parametros.valorVigente(TASA_NORMAL));
        a.setSaldo(saldo);
        asiento(a, operacion, SALIDA, interes, "RETIRO_INTERES", null, null);
        cuentas.mover(cuenta, operacion, ENTRADA, interes, "INTERES_AHORRO");
        eventos.publishEvent(new ec.janery.common.ResultadoGenerado("PERDIDA", interes, "Interés del ahorro " + a.getNombre(), usuarioId));
        auditoria.registrar(usuarioId, "AHORRO_INTERES", MODULO, "ahorro", a.getId(), null, interes.toPlainString(), null);
        return operacion;
    }

    @Transactional
    public AhorroVista renombrar(Long usuarioId, Long ahorroId, String nombre) {
        CuentaAhorro a = propia(personaDe(usuarioId), ahorroId);
        exigirActiva(a);
        String antes = a.getNombre();
        a.setNombre(nombre);
        auditoria.registrar(usuarioId, "AHORRO_RENOMBRADO", MODULO, "ahorro", a.getId(), antes, nombre, null);
        return vista(a);
    }

    /**
     * Eliminar un ahorro normal es cerrarlo: antes hay que pasar su saldo y su interés liberado a la cuenta principal.
     * El interés que aún corre se libera el día 1; si se cierra antes, ese interés no se paga.
     */
    @Transactional
    public void eliminar(Long usuarioId, Long ahorroId) {
        CuentaAhorro a = propia(personaDe(usuarioId), ahorroId);
        exigirActiva(a);
        if (!NORMAL.equals(a.getTipo())) {
            throw NegocioException.conflicto("PLAZO_FIJO_VIGENTE", "Un plazo fijo se cierra solo al retirarlo cuando termina");
        }
        devengar(a);
        if (a.getSaldo().signum() > 0 || a.getInteresDisponible().compareTo(new BigDecimal("0.01")) >= 0) {
            throw NegocioException.conflicto("AHORRO_CON_SALDO",
                    "Pase primero todo el dinero y el interés liberado de este ahorro a su cuenta principal");
        }
        a.setEstado("CERRADA");
        a.setCerradaEn(Instant.now(reloj));
        auditoria.registrar(usuarioId, "AHORRO_ELIMINADO", MODULO, "ahorro", a.getId(), ACTIVA, "CERRADA", null);
    }

    /** Valida al solicitar el crédito que el encaje pueda salir de ese ahorro. */
    @Transactional(readOnly = true)
    public void exigirAhorroNormal(Long personaId, Long ahorroId) {
        if (ahorroId == null) {
            throw NegocioException.conflicto("AHORRO_REQUERIDO", "Indique de qué ahorro se descontará el encaje");
        }
        CuentaAhorro a = ahorros.findById(ahorroId).filter(x -> x.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Ahorro no encontrado"));
        exigirActiva(a);
        if (!NORMAL.equals(a.getTipo())) {
            throw NegocioException.conflicto("AHORRO_NO_DISPONIBLE", "El encaje solo puede salir de un ahorro normal");
        }
    }

    /**
     * El encaje o los suministros de un crédito, pagados con un ahorro normal. concepto: ENCAJE (sigue siendo de la persona)
     * o SUMINISTROS (pasa a ser ganancia).
     */
    @Transactional
    public void descontarAlInicioDeCredito(Long personaId, Long ahorroId, BigDecimal importe, Long operacion, String concepto) {
        exigirAhorroNormal(personaId, ahorroId);
        CuentaAhorro a = propia(personaId, ahorroId);
        exigirActiva(a);
        if (importe.compareTo(a.getSaldo()) > 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "El ahorro elegido no cubre el encaje y los suministros");
        }
        devengar(a);
        a.setSaldo(a.getSaldo().subtract(importe));
        asiento(a, operacion, SALIDA, importe, concepto, null, null);
    }

    @Transactional(readOnly = true)
    public Productos productos() {
        Map<Integer, String> codigos = new LinkedHashMap<>();
        PLAZOS.forEach(p -> codigos.put(p, parametroPlazo(p)));
        Map<String, BigDecimal> tasas = parametros.valoresVigentes(codigos.values());
        return new Productos(parametros.valorVigente(TASA_NORMAL),
                PLAZOS.stream().map(p -> new PlazoVista(p, tasas.get(codigos.get(p)))).toList());
    }

    /**
     * Decisión del propietario (22/11/2026): ahorro que ya existía antes de usar Janery. Entra su saldo de hoy sin mover
     * dinero: ese dinero ya está en la caja o prestado. El normal gana interés desde hoy; el plazo fijo conserva su fecha
     * de apertura, su plazo y su tasa, y paga su interés al vencer como cualquier otro.
     */
    public record Anterior(Long usuarioId, String tipo, String nombre, BigDecimal saldo, LocalDate fechaApertura,
            Integer plazoMeses, BigDecimal tasaAnual) {
    }

    @Transactional
    public AhorroVista registrarAnterior(Long actorId, Anterior d, String clave) {
        Long personaId = personaDe(d.usuarioId());
        cuentas.cuentaActivaBloqueada(personaId);
        BigDecimal saldo = d.saldo().setScale(2);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "SALDO_ANTERIOR", saldo, actorId);
        if (previa.isPresent()) {
            return movimientos.findFirstByOperacionId(previa.get()).flatMap(m -> ahorros.findById(m.getCuentaAhorroId()))
                    .filter(a -> a.getPersonaId().equals(personaId)).map(this::vista)
                    .orElseThrow(() -> NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación"));
        }
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate apertura = d.fechaApertura() == null ? hoy : d.fechaApertura();
        if (apertura.isAfter(hoy)) {
            throw new NegocioException(org.springframework.http.HttpStatus.BAD_REQUEST, "FECHA_FUTURA",
                    "La fecha de apertura no puede ser posterior a hoy");
        }
        Instant abierta = apertura.atStartOfDay(reloj.getZone()).toInstant();
        CuentaAhorro a;
        if (PLAZO_FIJO.equals(d.tipo())) {
            if (d.plazoMeses() == null || d.plazoMeses() < 1 || d.tasaAnual() == null || d.fechaApertura() == null) {
                throw new NegocioException(org.springframework.http.HttpStatus.BAD_REQUEST, "DATOS_PLAZO_FIJO",
                        "Indique la fecha de apertura, el plazo y la tasa del plazo fijo");
            }
            a = new CuentaAhorro(null, personaId, PLAZO_FIJO, d.nombre(), ACTIVA, saldo, d.tasaAnual(), d.plazoMeses(),
                    apertura.plusMonths(d.plazoMeses()), abierta, null, CERO, CERO, null, null);
        } else {
            a = new CuentaAhorro(null, personaId, NORMAL, d.nombre(), ACTIVA, saldo, null, null, null, abierta, null, CERO, CERO,
                    hoy, null);
        }
        ahorros.save(a);
        Long operacion = cuentas.crearOperacionInterna("SALDO_ANTERIOR", saldo, clave, actorId);
        asiento(a, operacion, ENTRADA, saldo, "SALDO_ANTERIOR", null, null);
        auditoria.registrar(actorId, "AHORRO_ANTERIOR_REGISTRADO", MODULO, "ahorro", a.getId(), null,
                a.getTipo() + " " + saldo.toPlainString(), null);
        return vista(a);
    }

    private static String parametroPlazo(int meses) {
        return "PLAZO_FIJO_TASA_%d_MESES".formatted(meses);
    }

    /** Guarda el interés corrido hasta hoy. Debe llamarse antes de cambiar el saldo: corrió sobre el saldo anterior. */
    private void devengar(CuentaAhorro a) {
        InteresDiario.Interes i = interesAHoy(a);
        a.setInteresAcumulado(i.acumulado());
        a.setInteresDisponible(i.disponible());
        a.setUltimoDevengo(interesDiario.hoy());
    }

    /** Definido por el propietario: tasa anual dividida para 365 cada día, sin capitalizar; se libera cada día 1. */
    private InteresDiario.Interes interesAHoy(CuentaAhorro a) {
        if (!NORMAL.equals(a.getTipo())) {
            return new InteresDiario.Interes(a.getInteresAcumulado(), a.getInteresDisponible());
        }
        return interesDiario.aHoy(a.getSaldo(), a.getInteresAcumulado(), a.getInteresDisponible(), a.getUltimoDevengo(),
                TASA_NORMAL);
    }

    @Transactional(readOnly = true)
    public List<AhorroVista> misAhorros(Long usuarioId) {
        return ahorros.findByPersonaIdOrderByIdDesc(personaDe(usuarioId)).stream().map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoVista> movimientos(Long usuarioId, Long ahorroId) {
        CuentaAhorro a = propia(personaDe(usuarioId), ahorroId);
        return movimientos.findByCuentaAhorroIdOrderByIdDesc(a.getId()).stream().map(m -> new MovimientoVista(m.getId(),
                m.getSentido(), m.getImporte(), m.getConcepto(), m.getSaldoResultante(), m.getCreadoEn())).toList();
    }

    private AhorroVista vista(CuentaAhorro a) {
        boolean plazo = PLAZO_FIJO.equals(a.getTipo());
        BigDecimal tasa = plazo ? a.getTasaAnual() : parametros.valorVigente(TASA_NORMAL);
        boolean retirable = ACTIVA.equals(a.getEstado()) && (!plazo || !LocalDate.now(reloj).isBefore(a.getVenceEn()));
        InteresDiario.Interes interes = interesAHoy(a);
        return new AhorroVista(a.getId(), a.getTipo(), a.getNombre(), a.getEstado(), a.getSaldo(), tasa, a.getPlazoMeses(),
                a.getVenceEn(), plazo && ACTIVA.equals(a.getEstado()) ? interesPlazoFijo(a) : null, retirable,
                interes.acumulado().setScale(2, RoundingMode.DOWN), interes.disponible().setScale(2, RoundingMode.DOWN),
                plazo ? null : interesDiario.proximaLiberacion(), ec.janery.common.Codigos.ahorro(a.getTipo(), a.getId()));
    }

    private static BigDecimal interesPlazoFijo(CuentaAhorro a) {
        return a.getSaldo().multiply(a.getTasaAnual()).multiply(BigDecimal.valueOf(a.getPlazoMeses()))
                .divide(CIEN.multiply(DOCE), 2, RoundingMode.HALF_UP);
    }

    private void asiento(CuentaAhorro a, Long operacion, String sentido, BigDecimal importe, String concepto,
            String periodo, BigDecimal tasa) {
        movimientos.save(new MovimientoAhorro(null, a.getId(), operacion, sentido, importe, concepto, a.getSaldo(),
                periodo, tasa, Instant.now(reloj)));
    }

    /** Un reintento con la misma clave debe ser sobre el mismo ahorro; si no, la clave se está reutilizando. */
    private Long mismoAhorro(Long operacion, Long ahorroId) {
        boolean otro = movimientos.findFirstByOperacionId(operacion).map(m -> !m.getCuentaAhorroId().equals(ahorroId)).orElse(false);
        if (otro) {
            throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
        }
        return operacion;
    }

    /**
     * Bloqueado y releído: si la fila ya estaba cargada en esta transacción (descontarAlInicioDeCredito la lee antes), la
     * consulta con bloqueo devolvería esa copia antigua y se pisaría lo que guardó la operación anterior.
     */
    private CuentaAhorro propia(Long personaId, Long ahorroId) {
        CuentaAhorro a = ahorros.bloquear(ahorroId).filter(x -> x.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Ahorro no encontrado"));
        em.refresh(a);
        return a;
    }

    private static void exigirActiva(CuentaAhorro a) {
        if (!ACTIVA.equals(a.getEstado())) {
            throw NegocioException.conflicto("AHORRO_CERRADO", "Este ahorro está cerrado");
        }
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "Complete sus datos personales")).getId();
    }

    @Component
    @RequiredArgsConstructor
    static class AhorrosCierre implements RestriccionCierre {

        private final CuentaAhorroRepository ahorros;

        @Override
        public void verificar(Long personaId) {
            if (ahorros.tieneSaldo(personaId)) {
                throw NegocioException.conflicto("AHORROS_CON_SALDO", "Retire primero el saldo de sus ahorros");
            }
        }
    }
}
