package ec.janery.credito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

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
import ec.janery.cuenta.CuentaService;
import ec.janery.parametros.ParametroService;
import ec.janery.persona.PersonaService;
import ec.janery.socio.SocioService;
import lombok.RequiredArgsConstructor;

/**
 * Decisión del propietario (02/11/2026): los créditos a letras que ya existían antes de usar Janery se registran con sus
 * propias condiciones (tasa, plazo, fecha de inicio) y con las letras que ya se pagaron. Desde ahí se cobran como
 * cualquier otro crédito.
 *
 * <p>
 * Lo ya ocurrido no se repite: no entra dinero a la cuenta de la persona, no se cobra el encaje y lo pagado antes no se
 * reparte otra vez como ganancia. En el balance entran solo los saldos de hoy: el capital pendiente (1.4), el encaje
 * retenido y el ahorro letras acumulado (2.3).
 */
@Service
@RequiredArgsConstructor
public class CreditoExistenteService {

    public record Datos(Long usuarioId, String modalidad, BigDecimal importe, int plazoMeses, BigDecimal tasaInteres,
            BigDecimal tasaDesgravamen, boolean conAhorroLetras, LocalDate fechaInicio, int letrasPagadas, BigDecimal encaje,
            BigDecimal capitalDebe, String nota) {
    }

    /**
     * Cómo quedaría el crédito, sin guardar. Atrasadas: letras ya vencidas que no están entre las pagadas. Abono: capital
     * que ya pagó además de sus letras completas, cuando debe menos de lo que dicen sus letras pendientes.
     */
    public record Vista(String perfil, List<Letra> cuotas, int letrasPagadas, int letrasAtrasadas, BigDecimal capitalPendiente,
            BigDecimal abonoCapital, BigDecimal ahorroAcumulado, BigDecimal encaje, BigDecimal tasaMora, BigDecimal totalPendiente) {
    }

    private static final String LETRAS = "LETRAS";
    private static final String MODULO = "CREDITO";
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final List<String> MODALIDADES = List.of("NORMAL", "INVERSION", "ESTUDIO", "PERSONALIZADO");

    private final CreditoService.CreditoRepository creditos;
    private final CreditoService.CuotaRepository cuotas;
    private final CreditoService.DesembolsoRepository desembolsos;
    private final PagoCreditoService.PagoRepository pagos;
    private final PagoCreditoService.DetalleRepository detalles;
    private final CreditoService.EncajeRepository encajes;
    private final CreditoService.AhorroLetrasRepository ahorros;
    private final PagoCreditoService.MovimientoAhorroLetrasRepository movimientosAhorro;
    private final CuentaService cuentas;
    private final PersonaService personas;
    private final SocioService socios;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public Vista previsualizar(Datos d) {
        return calcular(personaDe(d.usuarioId()), d);
    }

    /** La clave evita registrar dos veces el mismo crédito si se reintenta. */
    @Transactional
    public Long registrar(Long actorId, Datos d, String clave) {
        Long personaId = personaDe(d.usuarioId());
        // Bloquea la cuenta: las letras pendientes se cobrarán de ella.
        cuentas.cuentaActivaBloqueada(personaId);
        Optional<Credito> previo = creditos.findByClaveSolicitud(clave);
        if (previo.isPresent()) {
            if (!previo.get().getPersonaId().equals(personaId) || previo.get().getImporte().compareTo(d.importe()) != 0) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return previo.get().getId();
        }
        Vista v = calcular(personaId, d);
        Instant ahora = Instant.now(reloj);

        Credito c = new Credito();
        c.setPersonaId(personaId);
        c.setFamilia(LETRAS);
        c.setModalidad(d.modalidad());
        c.setPerfil(v.perfil());
        c.setEstado("ACTIVO");
        c.setUnidadTasa("PCT_MENSUAL");
        c.setTasaInteres(d.tasaInteres());
        c.setTasaDesgravamen(d.tasaDesgravamen());
        c.setImporte(d.importe());
        c.setPlazoMeses(d.plazoMeses());
        c.setPorcentajeEncaje(d.encaje().multiply(BigDecimal.valueOf(100)).divide(d.importe(), 6, RoundingMode.HALF_UP));
        c.setFechaInicio(d.fechaInicio());
        c.setCapitalPendiente(v.capitalPendiente());
        c.setTasaMora(v.tasaMora());
        c.setClaveSolicitud(clave);
        c.setExistente(true);
        c.setCreadoEn(ahora);
        creditos.save(c);

        // El abono cuenta como capital adelantado: cubre desde la primera letra pendiente hacia adelante, como un pago parcial.
        BigDecimal abono = v.abonoCapital();
        for (Letra l : v.cuotas()) {
            CuotaCredito q = new CuotaCredito(c.getId(), l.numero(), l.vencimiento(), l.capital(), l.ahorroLetras(),
                    l.fondoDesgravamen(), l.interes(), l.total());
            if (l.numero() <= d.letrasPagadas()) {
                q.setCapitalPagado(l.capital());
                q.setInteresPagado(l.interes());
                q.setDesgravamenPagado(l.fondoDesgravamen());
                q.setAhorroPagado(l.ahorroLetras());
                q.setMoraHasta(l.vencimiento());
                q.setPagadaEn(l.vencimiento().atStartOfDay(ZONA).toInstant());
            } else if (abono.signum() > 0) {
                BigDecimal aEsta = abono.min(l.capital());
                q.setCapitalPagado(aEsta);
                abono = abono.subtract(aEsta);
            }
            cuotas.save(q);
        }

        // Saldos de hoy: lo que falta de capital es cartera; el encaje y el ahorro letras se deben a la persona.
        Long saldoInicial = cuentas.crearOperacionInterna("DESEMBOLSO", v.capitalPendiente(), null, actorId);
        desembolsos.save(new DesembolsoCredito(null, c.getId(), saldoInicial, v.capitalPendiente(), ahora));
        encajes.save(new EncajeCredito(null, c.getId(), saldoInicial, v.encaje(), ahora, null));
        ahorros.save(new AhorroLetrasCredito(null, c.getId(), v.ahorroAcumulado(), null));
        if (v.ahorroAcumulado().signum() > 0) {
            Long operacion = cuentas.crearOperacionInterna("PAGO_CREDITO", v.ahorroAcumulado(), null, actorId);
            PagoCredito pago = pagos.save(new PagoCredito(null, c.getId(), null, operacion, v.ahorroAcumulado(), ahora));
            detalles.save(new DetallePago(null, pago.getId(), "AHORRO_LETRAS", v.ahorroAcumulado()));
            movimientosAhorro.save(new MovimientoAhorroLetras(null, c.getId(), null, "ACUMULACION", v.ahorroAcumulado(), ahora));
        }
        auditoria.registrar(actorId, "CREDITO_EXISTENTE_REGISTRADO", MODULO, "credito", c.getId(), null,
                d.importe().toPlainString() + " desde " + d.fechaInicio() + ", " + d.letrasPagadas() + " de " + d.plazoMeses()
                        + " letras pagadas, debe " + v.capitalPendiente().toPlainString() + " de capital",
                d.nota());
        return c.getId();
    }

    // --- Emergentes anteriores (22/11/2026)

    /**
     * Emergente de cliente que ya existía: un solo pago, que vence un mes después de recibir el dinero. Interés y
     * desgravamen se calculan sobre el importe, como en los emergentes nuevos, salvo que ya los haya pagado.
     */
    public record EmergenteCliente(Long usuarioId, BigDecimal importe, LocalDate fechaInicio, BigDecimal tasaInteres,
            BigDecimal tasaDesgravamen, BigDecimal capitalDebe, boolean cargosPagados, String nota) {
    }

    /** Cupo de socio que ya existía: lo que debe hoy de capital y los cargos ya corridos que aún no pagó. */
    public record CupoSocio(Long usuarioId, BigDecimal capitalDebe, BigDecimal interesPendiente, BigDecimal desgravamenPendiente,
            String nota) {
    }

    private static final String EMERGENTE_CLIENTE = "EMERGENTE_CLIENTE";
    private static final String EMERGENTE_SOCIO = "EMERGENTE_SOCIO";

    @Transactional
    public Long registrarEmergenteCliente(Long actorId, EmergenteCliente d, String clave) {
        Long personaId = personaDe(d.usuarioId());
        cuentas.cuentaActivaBloqueada(personaId);
        Optional<Credito> previo = creditos.findByClaveSolicitud(clave);
        if (previo.isPresent()) {
            return mismoCredito(previo.get(), personaId, d.importe());
        }
        if (d.fechaInicio().isAfter(LocalDate.now(reloj))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FECHA_FUTURA", "La fecha en que recibió el dinero no puede ser posterior a hoy");
        }
        BigDecimal debe = (d.capitalDebe() == null ? d.importe() : d.capitalDebe()).setScale(2);
        if (debe.signum() <= 0) {
            throw NegocioException.conflicto("CREDITO_TERMINADO", "Si ya no debe capital, el emergente está terminado: no hace falta registrarlo");
        }
        if (debe.compareTo(d.importe()) > 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DEUDA_MAYOR_QUE_IMPORTE", "No puede deber más capital que el importe que recibió");
        }
        if (creditos.tieneEmergenteClienteAbierto(personaId)) {
            throw NegocioException.conflicto("EMERGENTE_ABIERTO", "Esta persona ya tiene un emergente sin pagar");
        }
        String perfil = socios.esSocio(personaId) ? "SOCIO" : "CLIENTE";
        Instant ahora = Instant.now(reloj);
        Credito c = new Credito();
        c.setPersonaId(personaId);
        c.setFamilia(EMERGENTE_CLIENTE);
        c.setPerfil(perfil);
        c.setEstado("ACTIVO");
        c.setUnidadTasa("PCT_MENSUAL");
        c.setTasaInteres(d.tasaInteres());
        c.setTasaDesgravamen(d.tasaDesgravamen());
        c.setImporte(d.importe());
        c.setPlazoMeses(1);
        c.setFechaInicio(d.fechaInicio());
        c.setCapitalPendiente(debe);
        c.setTasaMora("CLIENTE".equals(perfil) ? parametros.valorVigente("MORA_DIARIA") : BigDecimal.ZERO);
        c.setClaveSolicitud(clave);
        c.setExistente(true);
        c.setCreadoEn(ahora);
        creditos.save(c);

        BigDecimal interes = CalculadoraCredito.porcentaje(d.importe(), d.tasaInteres());
        BigDecimal desgravamen = CalculadoraCredito.porcentaje(d.importe(), d.tasaDesgravamen());
        CuotaCredito q = new CuotaCredito(c.getId(), 1, d.fechaInicio().plusMonths(1), d.importe(), BigDecimal.ZERO.setScale(2),
                desgravamen, interes, d.importe().add(interes).add(desgravamen));
        q.setCapitalPagado(d.importe().subtract(debe));
        if (d.cargosPagados()) {
            q.setInteresPagado(interes);
            q.setDesgravamenPagado(desgravamen);
        }
        cuotas.save(q);

        Long saldoInicial = cuentas.crearOperacionInterna("DESEMBOLSO", debe, null, actorId);
        desembolsos.save(new DesembolsoCredito(null, c.getId(), saldoInicial, debe, ahora));
        auditoria.registrar(actorId, "EMERGENTE_EXISTENTE_REGISTRADO", MODULO, "credito", c.getId(), null,
                d.importe().toPlainString() + " desde " + d.fechaInicio() + ", debe " + debe.toPlainString() + " de capital", d.nota());
        return c.getId();
    }

    /**
     * Se suma a su cupo de socio: si ya tiene uno en Janery, lo corrido hasta hoy conserva su tasa y desde hoy devenga
     * sobre el total. El cupo disponible baja en lo que debe.
     */
    @Transactional
    public Long registrarCupoSocio(Long actorId, CupoSocio d, String clave) {
        Long personaId = personaDe(d.usuarioId());
        cuentas.cuentaActivaBloqueada(personaId);
        BigDecimal debe = d.capitalDebe().setScale(2);
        Optional<Long> previa = cuentas.operacionPrevia(clave, "DESEMBOLSO", debe, actorId);
        if (previa.isPresent()) {
            return desembolsos.findByOperacionId(previa.get()).map(DesembolsoCredito::getCreditoId)
                    .orElseThrow(() -> NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación"));
        }
        if (!socios.esSocio(personaId)) {
            throw NegocioException.conflicto("SOLO_SOCIOS", "El cupo emergente es solo de socios: registre primero a la persona como socia");
        }
        LocalDate hoy = LocalDate.now(reloj);
        Instant ahora = Instant.now(reloj);
        Credito c = creditos.findByPersonaIdAndFamilia(personaId, EMERGENTE_SOCIO).orElseGet(() -> {
            Credito nuevo = new Credito();
            nuevo.setPersonaId(personaId);
            nuevo.setFamilia(EMERGENTE_SOCIO);
            nuevo.setPerfil("SOCIO");
            nuevo.setEstado("ACTIVO");
            nuevo.setUnidadTasa("PCT_DIARIO");
            nuevo.setTasaInteres(parametros.valorVigente("EMERGENTE_SOCIO_INTERES"));
            nuevo.setTasaDesgravamen(parametros.valorVigente("EMERGENTE_SOCIO_DESGRAVAMEN"));
            nuevo.setUltimoDevengo(hoy);
            nuevo.setFechaInicio(hoy);
            nuevo.setExistente(true);
            nuevo.setCreadoEn(ahora);
            return nuevo;
        });
        if (c.getId() != null) {
            c.devengar(hoy);
        }
        c.setCapitalPendiente(c.getCapitalPendiente().add(debe));
        c.setInteresAcumulado(c.getInteresAcumulado().add(cero(d.interesPendiente())));
        c.setDesgravamenAcumulado(c.getDesgravamenAcumulado().add(cero(d.desgravamenPendiente())));
        creditos.save(c);

        Long saldoInicial = cuentas.crearOperacionInterna("DESEMBOLSO", debe, clave, actorId);
        desembolsos.save(new DesembolsoCredito(null, c.getId(), saldoInicial, debe, ahora));
        auditoria.registrar(actorId, "CUPO_EXISTENTE_REGISTRADO", MODULO, "credito", c.getId(), null,
                "debe " + debe.toPlainString() + " de capital, " + cero(d.interesPendiente()).toPlainString() + " de interés y "
                        + cero(d.desgravamenPendiente()).toPlainString() + " de desgravamen", d.nota());
        return c.getId();
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : valor.setScale(2);
    }

    private static Long mismoCredito(Credito previo, Long personaId, BigDecimal importe) {
        if (!previo.getPersonaId().equals(personaId) || previo.getImporte().compareTo(importe) != 0) {
            throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
        }
        return previo.getId();
    }

    private Vista calcular(Long personaId, Datos d) {
        validar(d);
        String perfil = socios.esSocio(personaId) ? "SOCIO" : "CLIENTE";
        List<Letra> letras = d.conAhorroLetras()
                ? CalculadoraCredito.letras(d.importe(), d.plazoMeses(), d.tasaInteres(), d.tasaDesgravamen(), d.fechaInicio())
                : CalculadoraCredito.letrasSinAhorro(d.importe(), d.plazoMeses(), d.tasaInteres(), d.tasaDesgravamen(), d.fechaInicio());
        List<Letra> pagadas = letras.subList(0, d.letrasPagadas());
        List<Letra> pendientes = letras.subList(d.letrasPagadas(), letras.size());
        LocalDate hoy = LocalDate.now(reloj);
        int atrasadas = (int) pendientes.stream().filter(l -> l.vencimiento().isBefore(hoy)).count();
        BigDecimal segunLetras = pendientes.stream().map(Letra::capital).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        // Sin el dato, debe justo el capital de sus letras pendientes.
        BigDecimal capitalPendiente = d.capitalDebe() == null ? segunLetras : d.capitalDebe().setScale(2);
        if (capitalPendiente.compareTo(segunLetras) > 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DEUDA_MAYOR_QUE_LETRAS", "Con " + d.letrasPagadas()
                    + " letras pagadas, el capital que falta es " + segunLetras.toPlainString()
                    + " USD: no puede deber más. Si debe más, es que pagó menos letras");
        }
        BigDecimal abono = segunLetras.subtract(capitalPendiente);
        BigDecimal ahorro = pagadas.stream().map(Letra::ahorroLetras).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal total = pendientes.stream().map(Letra::total).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add).subtract(abono);
        // Como en los demás créditos: la mora es solo de clientes y usa la tasa vigente al registrarlo.
        BigDecimal mora = "CLIENTE".equals(perfil) ? parametros.valorVigente("MORA_DIARIA") : BigDecimal.ZERO;
        return new Vista(perfil, letras, d.letrasPagadas(), atrasadas, capitalPendiente, abono, ahorro, d.encaje().setScale(2), mora, total);
    }

    private void validar(Datos d) {
        if (!MODALIDADES.contains(d.modalidad())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "MODALIDAD_INVALIDA", "Elija una modalidad válida");
        }
        if (d.fechaInicio().isAfter(LocalDate.now(reloj))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FECHA_FUTURA", "La fecha de inicio no puede ser posterior a hoy");
        }
        if (d.letrasPagadas() >= d.plazoMeses()) {
            throw NegocioException.conflicto("CREDITO_TERMINADO", "Si ya pagó todas las letras, el crédito está terminado: no hace falta registrarlo");
        }
        if (d.capitalDebe() != null && d.capitalDebe().signum() <= 0) {
            throw NegocioException.conflicto("CREDITO_TERMINADO", "Si ya no debe capital, el crédito está terminado: no hace falta registrarlo");
        }
        if (d.encaje().compareTo(d.importe()) > 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "ENCAJE_INVALIDO", "El encaje no puede superar el importe del crédito");
        }
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.noEncontrado("Persona no encontrada")).getId();
    }
}
