package ec.janery.ahorro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Scheduled;
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

        @Query("select c.id from CuentaAhorro c where c.tipo = 'NORMAL' and c.estado = 'ACTIVA' and c.saldo > 0")
        List<Long> normalesConSaldo();

        @Query("select count(c) > 0 from CuentaAhorro c where c.personaId = :personaId and c.estado = 'ACTIVA' and c.saldo > 0")
        boolean tieneSaldo(@Param("personaId") Long personaId);
    }

    interface MovimientoAhorroRepository extends JpaRepository<MovimientoAhorro, Long> {
        List<MovimientoAhorro> findByCuentaAhorroIdOrderByIdDesc(Long cuentaAhorroId);

        boolean existsByCuentaAhorroIdAndPeriodoInteres(Long cuentaAhorroId, String periodo);
    }

    public record AhorroVista(Long id, String tipo, String nombre, String estado, BigDecimal saldo, BigDecimal tasaAnual,
            Integer plazoMeses, LocalDate venceEn, BigDecimal interesAlVencimiento, boolean retirable) {
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

    private final CuentaAhorroRepository ahorros;
    private final MovimientoAhorroRepository movimientos;
    private final CuentaService cuentas;
    private final PersonaService personas;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional
    public AhorroVista crearNormal(Long usuarioId, String nombre) {
        Long personaId = personaDe(usuarioId);
        cuentas.cuentaActivaBloqueada(personaId);
        CuentaAhorro a = ahorros.save(new CuentaAhorro(null, personaId, NORMAL, nombre, ACTIVA,
                BigDecimal.ZERO.setScale(2), null, null, null, Instant.now(reloj), null));
        auditoria.registrar(usuarioId, "AHORRO_CREADO", MODULO, "ahorro", a.getId(), null, NORMAL, null);
        return vista(a);
    }

    @Transactional
    public AhorroVista crearPlazoFijo(Long usuarioId, String nombre, BigDecimal importe, int plazo, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "APERTURA_AHORRO", importe, usuarioId);
        if (previa.isPresent()) {
            throw NegocioException.conflicto("OPERACION_REPETIDA", "Este depósito ya se registró");
        }
        if (plazo < parametros.valorVigente("PLAZO_FIJO_PLAZO_MIN").intValue()
                || plazo > parametros.valorVigente("PLAZO_FIJO_PLAZO_MAX").intValue()) {
            throw NegocioException.conflicto("PLAZO_FUERA_DE_RANGO", "El plazo está fuera de los límites permitidos");
        }
        CuentaAhorro a = ahorros.save(new CuentaAhorro(null, personaId, PLAZO_FIJO, nombre, ACTIVA, importe,
                parametros.valorVigente("PLAZO_FIJO_TASA"), plazo, LocalDate.now(reloj).plusMonths(plazo),
                Instant.now(reloj), null));
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
            return previa.get();
        }
        CuentaAhorro a = propia(personaId, ahorroId);
        exigirActiva(a);
        if (!NORMAL.equals(a.getTipo())) {
            throw NegocioException.conflicto("PLAZO_FIJO_SIN_DEPOSITOS", "Un plazo fijo no admite depósitos adicionales");
        }
        Long operacion = cuentas.crearOperacionInterna("DEPOSITO_AHORRO", importe, clave, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "DEPOSITO_AHORRO");
        a.setSaldo(a.getSaldo().add(importe));
        asiento(a, operacion, ENTRADA, importe, "DEPOSITO", null, null);
        auditoria.registrar(usuarioId, "AHORRO_DEPOSITO", MODULO, "ahorro", a.getId(), null, importe.toPlainString(), null);
        return operacion;
    }

    /** Normal: cualquier importe disponible. Plazo fijo: solo al vencer, todo el saldo más su interés. */
    @Transactional
    public Long retirar(Long usuarioId, Long ahorroId, BigDecimal importe, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "RETIRO_AHORRO", null, usuarioId);
        if (previa.isPresent()) {
            return previa.get();
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
        }
        a.setSaldo(BigDecimal.ZERO.setScale(2));
        asiento(a, operacion, SALIDA, saldo.add(interes), "LIQUIDACION", null, null);
        a.setEstado("CERRADA");
        a.setCerradaEn(Instant.now(reloj));
        cuentas.mover(cuenta, operacion, ENTRADA, total, "LIQUIDACION_PLAZO_FIJO");
        return operacion;
    }

    /** Abona el interés del mes a un ahorro normal. Repetirlo para el mismo mes no tiene efecto. */
    @Transactional
    public boolean abonarInteres(Long ahorroId, YearMonth periodo) {
        CuentaAhorro a = ahorros.bloquear(ahorroId).orElseThrow();
        String clave = periodo.toString();
        if (!ACTIVA.equals(a.getEstado()) || !NORMAL.equals(a.getTipo())
                || movimientos.existsByCuentaAhorroIdAndPeriodoInteres(a.getId(), clave)) {
            return false;
        }
        BigDecimal tasa = parametros.valorVigente("AHORRO_NORMAL_TASA");
        BigDecimal interes = a.getSaldo().multiply(tasa).divide(CIEN.multiply(DOCE), 2, RoundingMode.HALF_UP);
        if (interes.signum() <= 0) {
            return false;
        }
        Long operacion = cuentas.crearOperacionInterna("INTERES_AHORRO", interes, null, null);
        a.setSaldo(a.getSaldo().add(interes));
        asiento(a, operacion, ENTRADA, interes, "INTERES", clave, tasa);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> normalesConSaldo() {
        return ahorros.normalesConSaldo();
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
        return new AhorroVista(a.getId(), a.getTipo(), a.getNombre(), a.getEstado(), a.getSaldo(), tasa, a.getPlazoMeses(),
                a.getVenceEn(), plazo && ACTIVA.equals(a.getEstado()) ? interesPlazoFijo(a) : null, retirable);
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

    private CuentaAhorro propia(Long personaId, Long ahorroId) {
        return ahorros.bloquear(ahorroId).filter(a -> a.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Ahorro no encontrado"));
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

    /** Cada día 1 abona el mes anterior. Cada ahorro va en su propia transacción: un fallo no frena a los demás. */
    @Component
    @RequiredArgsConstructor
    static class InteresMensual {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InteresMensual.class);

        private final AhorroService service;
        private final Clock reloj;

        @Scheduled(cron = "0 0 1 1 * *", zone = "${janery.zona-horaria}")
        void ejecutar() {
            YearMonth periodo = YearMonth.now(reloj).minusMonths(1);
            for (Long id : service.normalesConSaldo()) {
                try {
                    service.abonarInteres(id, periodo);
                } catch (RuntimeException e) {
                    log.error("No se pudo abonar el interés del ahorro {} en {}", id, periodo, e);
                }
            }
        }
    }
}
