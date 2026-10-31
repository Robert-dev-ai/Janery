package ec.janery.cuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.caja.CajaService;
import ec.janery.common.NegocioException;
import ec.janery.cuenta.Entidades.DepositoApertura;
import ec.janery.cuenta.Entidades.MovimientoCuenta;
import ec.janery.cuenta.Entidades.OperacionFinanciera;
import ec.janery.parametros.ParametroService;
import ec.janery.persona.Persona;
import ec.janery.persona.PersonaService;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CuentaService {

    interface CuentaRepository extends JpaRepository<CuentaPrincipal, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select c from CuentaPrincipal c where c.id = :id")
        Optional<CuentaPrincipal> bloquear(@Param("id") Long id);

        @Query("select c from CuentaPrincipal c where c.personaId = :personaId and c.estado not in ('CERRADA', 'RECHAZADA')")
        Optional<CuentaPrincipal> vigenteDe(@Param("personaId") Long personaId);

        Page<CuentaPrincipal> findByEstado(String estado, Pageable pageable);

        @Query(value = "SELECT nextval('secuencia_numero_cuenta')", nativeQuery = true)
        long siguienteNumero();
    }

    interface OperacionRepository extends JpaRepository<OperacionFinanciera, Long> {
        Optional<OperacionFinanciera> findByClaveIdempotencia(String clave);
    }

    interface DepositoRepository extends JpaRepository<DepositoApertura, Long> {
        Optional<DepositoApertura> findByCuentaId(Long cuentaId);
    }

    interface MovimientoRepository extends JpaRepository<MovimientoCuenta, Long> {
        Page<MovimientoCuenta> findByCuentaId(Long cuentaId, Pageable pageable);

        Optional<MovimientoCuenta> findFirstByOperacionId(Long operacionId);

        @Query("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from MovimientoCuenta m where m.cuentaId = :cuentaId
                """)
        BigDecimal saldoSegunLibro(@Param("cuentaId") Long cuentaId);
    }

    public record CuentaVista(CuentaPrincipal cuenta, String titular, BigDecimal depositoReembolsable) {
    }

    private static final String MODULO = "CUENTA";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";

    private final CuentaRepository cuentas;
    private final OperacionRepository operaciones;
    private final DepositoRepository depositos;
    private final MovimientoRepository movimientos;
    private final PersonaService personas;
    private final ParametroService parametros;
    private final CajaService caja;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final List<RestriccionCierre> restricciones;

    @Transactional
    public CuentaVista solicitarApertura(Long usuarioId, String metodoPago, String referencia) {
        Persona persona = personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "Complete sus datos personales antes de abrir la cuenta"));
        if (cuentas.vigenteDe(persona.getId()).isPresent()) {
            throw NegocioException.conflicto("CUENTA_EXISTENTE", "Ya tiene una cuenta o una solicitud en curso");
        }
        String numero = "JAN-%06d".formatted(cuentas.siguienteNumero());
        CuentaPrincipal cuenta = cuentas.save(
                new CuentaPrincipal(persona.getId(), numero, metodoPago, referencia, Instant.now(reloj)));
        auditoria.registrar(usuarioId, "APERTURA_SOLICITADA", MODULO, "cuenta", cuenta.getId(), null,
                CuentaPrincipal.PENDIENTE_PAGO, null);
        return vista(cuenta);
    }

    /** El dinero se recibe fuera del sistema; quien lo verifica confirma y entonces se contabiliza. */
    @Transactional
    public CuentaVista confirmarApertura(Long actorId, Long cuentaId) {
        CuentaPrincipal cuenta = bloquear(cuentaId);
        exigirEstado(cuenta, CuentaPrincipal.PENDIENTE_PAGO, "La cuenta no tiene una apertura pendiente");

        BigDecimal total = dinero(parametros.valorVigente("APERTURA_TOTAL"));
        BigDecimal ingreso = dinero(parametros.valorVigente("APERTURA_INGRESO"));
        BigDecimal reembolsable = dinero(parametros.valorVigente("APERTURA_REEMBOLSABLE"));
        if (ingreso.add(reembolsable).compareTo(total) != 0) {
            throw NegocioException.conflicto("PARAMETROS_INCOHERENTES",
                    "Ingreso y depósito reembolsable no suman el pago de apertura");
        }

        Instant ahora = Instant.now(reloj);
        OperacionFinanciera operacion = operaciones.save(new OperacionFinanciera(null, "APERTURA", total,
                cuenta.getMetodoPago(), cuenta.getReferenciaPago(), null, actorId, ahora));
        depositos.save(new DepositoApertura(null, cuenta.getId(), operacion.getId(), total, ingreso, reembolsable, null));
        // El reembolsable queda fuera del saldo disponible y de los ingresos de caja.
        caja.registrarEnPrincipal(operacion.getId(), ENTRADA, ingreso, "INGRESO_APERTURA");
        cuenta.activar(ahora);
        auditoria.registrar(actorId, "APERTURA_CONFIRMADA", MODULO, "cuenta", cuenta.getId(),
                CuentaPrincipal.PENDIENTE_PAGO, CuentaPrincipal.ACTIVA, null);
        return new CuentaVista(cuenta, personas.nombreCompleto(cuenta.getPersonaId()), reembolsable);
    }

    @Transactional
    public void rechazarApertura(Long actorId, Long cuentaId, String motivo) {
        CuentaPrincipal cuenta = bloquear(cuentaId);
        exigirEstado(cuenta, CuentaPrincipal.PENDIENTE_PAGO, "La cuenta no tiene una apertura pendiente");
        cuenta.rechazar();
        auditoria.registrar(actorId, "APERTURA_RECHAZADA", MODULO, "cuenta", cuenta.getId(),
                CuentaPrincipal.PENDIENTE_PAGO, CuentaPrincipal.RECHAZADA, motivo);
    }

    @Transactional
    public Long depositar(Long actorId, Long cuentaId, BigDecimal importe, String metodo, String referencia, String clave) {
        return movimientoManual(actorId, cuentaId, "DEPOSITO", ENTRADA, importe, metodo, referencia, clave);
    }

    @Transactional
    public Long retirar(Long actorId, Long cuentaId, BigDecimal importe, String metodo, String referencia, String clave) {
        return movimientoManual(actorId, cuentaId, "RETIRO", SALIDA, importe, metodo, referencia, clave);
    }

    private Long movimientoManual(Long actorId, Long cuentaId, String tipo, String sentido, BigDecimal importe,
            String metodo, String referencia, String clave) {
        CuentaPrincipal cuenta = bloquear(cuentaId);
        // Tras el bloqueo: un reintento concurrente espera aquí y encuentra la operación ya confirmada.
        Optional<OperacionFinanciera> previa = operaciones.findByClaveIdempotencia(clave);
        if (previa.isPresent()) {
            OperacionFinanciera op = previa.get();
            boolean mismaCuenta = movimientos.findFirstByOperacionId(op.getId())
                    .map(m -> m.getCuentaId().equals(cuentaId)).orElse(false);
            if (!mismaCuenta || !op.getTipo().equals(tipo) || op.getImporte().compareTo(importe) != 0) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA",
                        "La clave de idempotencia ya se usó en otra operación");
            }
            return op.getId();
        }
        exigirEstado(cuenta, CuentaPrincipal.ACTIVA, "La cuenta no está activa");
        BigDecimal nuevoSaldo = ENTRADA.equals(sentido)
                ? cuenta.getSaldoDisponible().add(importe)
                : cuenta.getSaldoDisponible().subtract(importe);
        if (nuevoSaldo.signum() < 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "El saldo disponible no cubre el retiro");
        }
        Instant ahora = Instant.now(reloj);
        OperacionFinanciera operacion = operaciones.save(
                new OperacionFinanciera(null, tipo, importe, metodo, referencia, clave, actorId, ahora));
        movimientos.save(new MovimientoCuenta(null, cuenta.getId(), operacion.getId(), sentido, importe, tipo,
                nuevoSaldo, ahora));
        caja.registrarEnPrincipal(operacion.getId(), sentido, importe, tipo + "_CUENTA");
        cuenta.aplicar(nuevoSaldo);
        auditoria.registrar(actorId, tipo, MODULO, "cuenta", cuenta.getId(), null, importe.toPlainString(), null);
        return operacion.getId();
    }

    // --- Operaciones internas de los productos. Bloquear siempre la cuenta primero: serializa a cada persona.

    /** Cuenta activa de la persona, bloqueada hasta el fin de la transacción. */
    @Transactional
    public CuentaPrincipal cuentaActivaBloqueada(Long personaId) {
        CuentaPrincipal cuenta = cuentas.vigenteDe(personaId)
                .filter(c -> CuentaPrincipal.ACTIVA.equals(c.getEstado()))
                .orElseThrow(() -> NegocioException.conflicto("CUENTA_INACTIVA", "Necesita una cuenta principal activa"));
        return bloquear(cuenta.getId());
    }

    /** Operación ya confirmada con esa clave, para responder igual a un reintento. Importe nulo: no se compara. */
    @Transactional(readOnly = true)
    public Optional<Long> operacionPrevia(String clave, String tipo, BigDecimal importe, Long actorId) {
        return operaciones.findByClaveIdempotencia(clave).map(op -> {
            if (!op.getTipo().equals(tipo) || !actorId.equals(op.getActorId())
                    || (importe != null && op.getImporte().compareTo(importe) != 0)) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return op.getId();
        });
    }

    @Transactional
    public Long crearOperacionInterna(String tipo, BigDecimal importe, String clave, Long actorId) {
        return operaciones.save(new OperacionFinanciera(null, tipo, importe, "INTERNO", null, clave, actorId,
                Instant.now(reloj))).getId();
    }

    /** Mueve saldo disponible con su asiento en el libro. Rechaza dejar el saldo en negativo. */
    @Transactional
    public void mover(CuentaPrincipal cuenta, Long operacionId, String sentido, BigDecimal importe, String concepto) {
        BigDecimal nuevoSaldo = ENTRADA.equals(sentido)
                ? cuenta.getSaldoDisponible().add(importe)
                : cuenta.getSaldoDisponible().subtract(importe);
        if (nuevoSaldo.signum() < 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "Su saldo disponible no cubre esta operación");
        }
        movimientos.save(new MovimientoCuenta(null, cuenta.getId(), operacionId, sentido, importe, concepto,
                nuevoSaldo, Instant.now(reloj)));
        cuenta.aplicar(nuevoSaldo);
    }

    @Transactional
    public void solicitarCierre(Long usuarioId) {
        CuentaPrincipal cuenta = bloquear(cuentaDe(usuarioId).getId());
        exigirEstado(cuenta, CuentaPrincipal.ACTIVA, "La cuenta no está activa");
        if (cuenta.getSaldoDisponible().signum() != 0) {
            throw NegocioException.conflicto("SALDO_PENDIENTE", "Retire su saldo disponible antes de cerrar la cuenta");
        }
        restricciones.forEach(r -> r.verificar(cuenta.getPersonaId()));
        cuenta.solicitarCierre();
        auditoria.registrar(usuarioId, "CIERRE_SOLICITADO", MODULO, "cuenta", cuenta.getId(),
                CuentaPrincipal.ACTIVA, CuentaPrincipal.PENDIENTE_CIERRE, null);
    }

    /** Registra la devolución del depósito reembolsable, ya entregado a la persona. */
    @Transactional
    public void confirmarCierre(Long actorId, Long cuentaId) {
        CuentaPrincipal cuenta = bloquear(cuentaId);
        exigirEstado(cuenta, CuentaPrincipal.PENDIENTE_CIERRE, "La cuenta no tiene un cierre pendiente");
        DepositoApertura deposito = depositos.findByCuentaId(cuentaId)
                .orElseThrow(() -> new IllegalStateException("Cuenta activa sin depósito de apertura: " + cuentaId));
        Instant ahora = Instant.now(reloj);
        operaciones.save(new OperacionFinanciera(null, "DEVOLUCION_CIERRE", deposito.getImporteReembolsable(),
                cuenta.getMetodoPago(), null, null, actorId, ahora));
        deposito.marcarDevuelto(ahora);
        cuenta.cerrar(ahora);
        auditoria.registrar(actorId, "CIERRE_CONFIRMADO", MODULO, "cuenta", cuenta.getId(),
                CuentaPrincipal.PENDIENTE_CIERRE, CuentaPrincipal.CERRADA, null);
    }

    @Transactional(readOnly = true)
    public CuentaVista miCuenta(Long usuarioId) {
        return vista(cuentaDe(usuarioId));
    }

    @Transactional(readOnly = true)
    public Page<MovimientoCuenta> misMovimientos(Long usuarioId, int pagina, int tamano) {
        return movimientos.findByCuentaId(cuentaDe(usuarioId).getId(),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
    }

    @Transactional(readOnly = true)
    public Optional<String> estadoCuentaVigente(Long usuarioId) {
        return personas.dePorUsuario(usuarioId)
                .flatMap(p -> cuentas.vigenteDe(p.getId()))
                .map(CuentaPrincipal::getEstado);
    }

    @Transactional(readOnly = true)
    public Page<CuentaVista> listar(String estado, int pagina, int tamano) {
        Pageable pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id"));
        Page<CuentaPrincipal> page = estado == null ? cuentas.findAll(pageable) : cuentas.findByEstado(estado, pageable);
        return page.map(this::vista);
    }

    /** Saldo guardado y saldo según el libro, en ese orden. Deben coincidir siempre. */
    @Transactional(readOnly = true)
    public List<BigDecimal> conciliar(Long cuentaId) {
        CuentaPrincipal cuenta = cuentas.findById(cuentaId)
                .orElseThrow(() -> NegocioException.noEncontrado("Cuenta no encontrada"));
        return List.of(cuenta.getSaldoDisponible(), movimientos.saldoSegunLibro(cuentaId));
    }

    private CuentaVista vista(CuentaPrincipal cuenta) {
        BigDecimal reembolsable = depositos.findByCuentaId(cuenta.getId())
                .filter(d -> d.getDevueltoEn() == null)
                .map(DepositoApertura::getImporteReembolsable)
                .orElse(null);
        return new CuentaVista(cuenta, personas.nombreCompleto(cuenta.getPersonaId()), reembolsable);
    }

    private CuentaPrincipal cuentaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId)
                .flatMap(p -> cuentas.vigenteDe(p.getId()))
                .orElseThrow(() -> NegocioException.noEncontrado("No tiene una cuenta principal"));
    }

    private CuentaPrincipal bloquear(Long cuentaId) {
        return cuentas.bloquear(cuentaId).orElseThrow(() -> NegocioException.noEncontrado("Cuenta no encontrada"));
    }

    private static void exigirEstado(CuentaPrincipal cuenta, String estado, String mensaje) {
        if (!estado.equals(cuenta.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", mensaje);
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
