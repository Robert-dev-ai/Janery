package ec.janery.ahorro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /** Interés de un ahorro normal a hoy: lo disponible se liberó el último día 1; lo acumulado se libera el próximo. */
    private record Interes(BigDecimal acumulado, BigDecimal disponible) {
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
    private static final BigDecimal DIAS_ANIO = BigDecimal.valueOf(365);
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(6);
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
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional
    public AhorroVista crearNormal(Long usuarioId, String nombre) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        CuentaAhorro a = ahorros.save(new CuentaAhorro(null, personaId, NORMAL, nombre, ACTIVA,
                BigDecimal.ZERO.setScale(2), null, null, null, Instant.now(reloj), null, CERO, CERO, LocalDate.now(reloj)));
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
                Instant.now(reloj), null, CERO, CERO, null));
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
        asiento(a, operacion, ENTRADA, interes, "INTERES", null, parametros.valorVigente("AHORRO_NORMAL_TASA"));
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
        return new Productos(parametros.valorVigente("AHORRO_NORMAL_TASA"),
                PLAZOS.stream().map(p -> new PlazoVista(p, tasas.get(codigos.get(p)))).toList());
    }

    private static String parametroPlazo(int meses) {
        return "PLAZO_FIJO_TASA_%d_MESES".formatted(meses);
    }

    /** Guarda el interés corrido hasta hoy. Debe llamarse antes de cambiar el saldo: corrió sobre el saldo anterior. */
    private void devengar(CuentaAhorro a) {
        Interes i = interesAHoy(a);
        a.setInteresAcumulado(i.acumulado());
        a.setInteresDisponible(i.disponible());
        a.setUltimoDevengo(LocalDate.now(reloj));
    }

    /** Definido por el propietario: 5 % anual dividido para 365 cada día, sin capitalizar; se libera cada día 1. */
    private Interes interesAHoy(CuentaAhorro a) {
        LocalDate hoy = LocalDate.now(reloj);
        BigDecimal acumulado = a.getInteresAcumulado();
        BigDecimal disponible = a.getInteresDisponible();
        LocalDate desde = a.getUltimoDevengo();
        if (!NORMAL.equals(a.getTipo()) || desde == null || !desde.isBefore(hoy)) {
            return new Interes(acumulado, disponible);
        }
        BigDecimal diario = a.getSaldo().multiply(parametros.valorVigente("AHORRO_NORMAL_TASA"));
        LocalDate primero = hoy.withDayOfMonth(1);
        if (desde.isBefore(primero)) {
            disponible = disponible.add(acumulado).add(interesDe(diario, desde, primero));
            acumulado = CERO;
            desde = primero;
        }
        return new Interes(acumulado.add(interesDe(diario, desde, hoy)), disponible);
    }

    private static BigDecimal interesDe(BigDecimal saldoPorTasa, LocalDate desde, LocalDate hasta) {
        return saldoPorTasa.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(desde, hasta)))
                .divide(CIEN.multiply(DIAS_ANIO), 6, RoundingMode.HALF_UP);
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
        BigDecimal tasa = plazo ? a.getTasaAnual() : parametros.valorVigente("AHORRO_NORMAL_TASA");
        boolean retirable = ACTIVA.equals(a.getEstado()) && (!plazo || !LocalDate.now(reloj).isBefore(a.getVenceEn()));
        Interes interes = interesAHoy(a);
        return new AhorroVista(a.getId(), a.getTipo(), a.getNombre(), a.getEstado(), a.getSaldo(), tasa, a.getPlazoMeses(),
                a.getVenceEn(), plazo && ACTIVA.equals(a.getEstado()) ? interesPlazoFijo(a) : null, retirable,
                interes.acumulado().setScale(2, RoundingMode.DOWN), interes.disponible().setScale(2, RoundingMode.DOWN),
                plazo ? null : LocalDate.now(reloj).withDayOfMonth(1).plusMonths(1), ec.janery.common.Codigos.ahorro(a.getTipo(), a.getId()));
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
