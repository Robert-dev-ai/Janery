package ec.janery.credito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.common.ResultadoGenerado;
import ec.janery.credito.Entidades.AhorroLetrasCredito;
import ec.janery.credito.Entidades.Credito;
import ec.janery.credito.Entidades.CuotaCredito;
import ec.janery.credito.Entidades.DetallePago;
import ec.janery.credito.Entidades.EncajeCredito;
import ec.janery.credito.Entidades.MovimientoAhorroLetras;
import ec.janery.credito.Entidades.PagoCredito;
import ec.janery.cuenta.CuentaPrincipal;
import ec.janery.cuenta.CuentaService;
import ec.janery.persona.PersonaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * Cobro de créditos: pagar letras, abonar al cupo de socio y precancelar. El dinero sale de la cuenta principal de la
 * persona; encaje y ahorro letras vuelven a ella al terminar. El orden de cobro de cada letra lo definió el propietario
 * (docs/reglas-negocio.md, sección 10).
 *
 * <p>Bloqueo: primero la cuenta principal y después el crédito, como en la aprobación. Así se serializan todas las
 * operaciones de una persona.
 */
@Service
@RequiredArgsConstructor
public class PagoCreditoService {

    interface PagoRepository extends org.springframework.data.jpa.repository.JpaRepository<PagoCredito, Long> {
        boolean existsByOperacionIdAndCreditoId(Long operacionId, Long creditoId);
    }

    interface DetalleRepository extends org.springframework.data.jpa.repository.JpaRepository<DetallePago, Long> {
    }

    interface MovimientoAhorroLetrasRepository extends org.springframework.data.jpa.repository.JpaRepository<MovimientoAhorroLetras, Long> {
    }

    private static final String LETRAS = "LETRAS";
    private static final String ACTIVO = "ACTIVO";
    private static final String FINALIZADO = "FINALIZADO";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";
    private static final String MODULO = "CREDITO";
    private static final String PAGO_CREDITO = "PAGO_CREDITO";

    private final CreditoService.CreditoRepository creditos;
    private final CreditoService.CuotaRepository cuotas;
    private final CreditoService.EncajeRepository encajes;
    private final CreditoService.AhorroLetrasRepository ahorros;
    private final PagoRepository pagos;
    private final DetalleRepository detalles;
    private final MovimientoAhorroLetrasRepository movimientosAhorro;
    private final CuentaService cuentas;
    private final PersonaService personas;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    @PersistenceContext
    private EntityManager em;

    /** Letras y emergente de cliente: se paga la siguiente cuota, entera o en parte. Cupo: hasta lo adeudado. */
    @Transactional
    public Long pagar(Long usuarioId, Long creditoId, BigDecimal importe, boolean usarAhorroLetras, boolean usarEncaje,
            String clave) {
        Long personaId = personaDe(usuarioId);
        Credito leido = creditos.findById(creditoId).filter(c -> c.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<Long> previa = cuentas.operacionPrevia(clave, PAGO_CREDITO, null, usuarioId);
        if (previa.isPresent()) {
            return mismoCredito(previa.get(), creditoId);
        }
        Credito c = bloquear(leido.getId());
        if (!ACTIVO.equals(c.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Este crédito no admite pagos");
        }
        Long operacion = c.esLinea()
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
        BigDecimal aPagar = importe == null || usarAhorro || usarEncaje ? cuota.deuda(hoy, c.getTasaMora()) : importe;
        BigDecimal deuda = pendientes.stream().map(q -> q.deuda(hoy, c.getTasaMora())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (aPagar.compareTo(deuda) > 0) {
            throw NegocioException.conflicto("PAGO_EXCEDIDO", "El importe supera lo que falta del crédito");
        }
        Instant ahora = Instant.now(reloj);
        Long operacion = cuentas.crearOperacionInterna(PAGO_CREDITO, aPagar, clave, actorId);
        // Liberar antes del cobro hace que esos fondos cubran la letra; el remanente queda en la cuenta principal.
        if (usarAhorro) {
            liberarAhorro(cuenta, c, operacion, null, ahora);
        }
        if (usarEncaje) {
            liberarEncaje(cuenta, c, operacion, ahora);
        }
        cuentas.mover(cuenta, operacion, SALIDA, aPagar, PAGO_CREDITO);

        BigDecimal resto = aPagar;
        for (CuotaCredito q : pendientes) {
            if (resto.signum() <= 0) {
                break;
            }
            BigDecimal aEstaLetra = resto.min(q.deuda(hoy, c.getTasaMora()));
            aplicarALetra(c, q, operacion, aEstaLetra, ahora, hoy, actorId);
            resto = resto.subtract(aEstaLetra);
        }
        if (todas.get(todas.size() - 1).getPagadaEn() != null) {
            finalizar(cuenta, c, operacion, todas.get(todas.size() - 1).getId(), ahora, actorId);
        }
        return operacion;
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
        Optional<Long> previa = cuentas.operacionPrevia(clave, PAGO_CREDITO, null, usuarioId);
        if (previa.isPresent()) {
            return mismoCredito(previa.get(), creditoId);
        }
        Credito c = bloquear(leido.getId());
        if (!ACTIVO.equals(c.getEstado()) || !LETRAS.equals(c.getFamilia())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Este crédito no admite precancelación");
        }
        List<CuotaCredito> todas = cuotas.findByCreditoIdOrderByNumero(c.getId());
        long pagadas = todas.stream().filter(q -> q.getPagadaEn() != null).count();
        int requeridas = CalculadoraCredito.letrasParaPrecancelar(todas.size());
        if (pagadas < requeridas) {
            throw NegocioException.conflicto("PRECANCELACION_NO_DISPONIBLE",
                    "Puede precancelar cuando haya pagado " + requeridas + " letras");
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
        Long operacion = cuentas.crearOperacionInterna(PAGO_CREDITO, capital.add(mora), clave, usuarioId);
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
        c.devengar(hoy());
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
        Long operacion = cuentas.crearOperacionInterna(PAGO_CREDITO, importe, clave, actorId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, PAGO_CREDITO);
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

    /**
     * Un reintento con la misma clave debe ser sobre el mismo crédito. Si no, la clave se reutilizó en otro pago y responder
     * con la operación anterior haría creer que se pagó un crédito que no se tocó.
     */
    private Long mismoCredito(Long operacion, Long creditoId) {
        if (!pagos.existsByOperacionIdAndCreditoId(operacion, creditoId)) {
            throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
        }
        return operacion;
    }

    /** El interés cobrado es ganancia; el desgravamen no: es un fondo que se debe. */
    private void ganancia(BigDecimal importe, String concepto, Long actorId) {
        if (importe.signum() > 0) {
            eventos.publishEvent(new ResultadoGenerado("GANANCIA", importe, concepto, actorId));
        }
    }

    private void detalle(PagoCredito pago, String concepto, BigDecimal importe) {
        if (importe.signum() > 0) {
            detalles.save(new DetallePago(null, pago.getId(), concepto, importe));
        }
    }

    /** Releído tras el bloqueo: ver CreditoService.bloquear. */
    private Credito bloquear(Long id) {
        Credito c = creditos.bloquear(id).orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
        em.refresh(c);
        return c;
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "Complete sus datos personales")).getId();
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }
}
