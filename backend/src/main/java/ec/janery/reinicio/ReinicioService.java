package ec.janery.reinicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.caja.CajaService;
import ec.janery.contabilidad.ContabilidadService;
import ec.janery.credito.AjusteAhorroLetrasController;
import ec.janery.credito.CreditoExistenteService;
import ec.janery.cuenta.CuentaService;
import ec.janery.persona.PersonaService;
import ec.janery.socio.ParticipacionService;
import ec.janery.socio.ReservaService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * TEMPORAL (22/11/2026), a pedido del propietario: borra los datos financieros con su historial y carga los saldos reales
 * de hoy, revisados por él en la hoja de saldos. Usuarios, personas, socios, cuentas, cargos, permisos y parámetros se
 * conservan. Todo ocurre en una sola transacción: si el balance no cuadra al final, no queda nada hecho. Se borra con
 * ReinicioRunner cuando el reinicio esté hecho.
 */
@Service
@RequiredArgsConstructor
public class ReinicioService {

    public record Plan(BigDecimal cajaPrincipal, BigDecimal reserva, BigDecimal fondoDesgravamen, BigDecimal gananciasPorRepartir,
            List<Cuenta> cuentas, List<Socio> socios, List<Letras> letras, List<Cupo> cupos, List<Emergente> emergentes,
            BigDecimal activosEsperados) {
    }

    public record Cuenta(Long usuarioId, BigDecimal saldo) {
    }

    public record Socio(Long usuarioId, BigDecimal aporteInicial, YearMonth mensualesHasta, BigDecimal personales) {
    }

    public record Letras(Long usuarioId, String modalidad, BigDecimal importe, int plazoMeses, BigDecimal tasaInteres,
            BigDecimal tasaDesgravamen, boolean conAhorroLetras, LocalDate fechaInicio, int letrasPagadas, BigDecimal encaje,
            BigDecimal capitalDebe, BigDecimal ahorroLetras) {
    }

    public record Cupo(Long usuarioId, BigDecimal capitalDebe, BigDecimal interesPendiente, BigDecimal desgravamenPendiente) {
    }

    public record Emergente(Long usuarioId, BigDecimal importe, LocalDate fechaInicio, BigDecimal tasaInteres,
            BigDecimal tasaDesgravamen, BigDecimal capitalDebe, boolean cargosPagados) {
    }

    public record Resumen(BigDecimal activos, BigDecimal pasivos, BigDecimal patrimonio, BigDecimal diferencia, int creditos,
            int cuentas, int socios) {
    }

    /** Tablas con historial inmutable: sus disparadores se apagan solo dentro de esta transacción. */
    private static final List<String> INMUTABLES = List.of("asientos_manuales", "auditoria_eventos", "compraventas_capital",
            "movimientos_ahorro", "movimientos_ahorro_letras", "movimientos_caja", "movimientos_cuenta", "resultados",
            "variaciones_participacion", "movimientos_reserva", "saldos_iniciales");

    /** De las que dependen a aquellas de las que dependen. */
    private static final List<String> A_VACIAR = List.of("movimientos_ahorro_letras", "detalles_pago", "pagos_credito",
            "ahorro_letras_credito", "encajes_credito", "desembolsos_credito", "suministros_credito", "documentos_credito",
            "aprobaciones_credito", "cuotas_credito", "creditos", "movimientos_ahorro", "cuentas_ahorro",
            "variaciones_participacion", "aportes_socios", "aportes_caja_chica", "resultados", "cierres_anuales",
            "autorizaciones_retiro", "compraventas_capital", "asientos_manuales", "destinos_pago", "solicitudes_deposito",
            "solicitudes_retiro", "solicitudes_cupo", "comprobantes", "movimientos_caja", "movimientos_cuenta",
            "movimientos_reserva", "saldos_iniciales");

    private static final String MOTIVO = "Reinicio de datos";

    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final CuentaService cuentas;
    private final CajaService cajas;
    private final PersonaService personas;
    private final ParticipacionService participacion;
    private final ReservaService reservas;
    private final CreditoExistenteService creditos;
    private final AjusteAhorroLetrasController ahorroLetras;
    private final ContabilidadService contabilidad;
    private final AuditoriaService auditoria;

    @Transactional
    public Resumen ejecutar(Long actorId, Plan plan) {
        limpiar();

        Long saldoInicial = cuentas.crearOperacionInterna("SALDO_ANTERIOR", plan.cajaPrincipal(), null, actorId);
        cajas.registrar(CajaService.PRINCIPAL, saldoInicial, "ENTRADA", plan.cajaPrincipal(), "SALDO_ANTERIOR",
                "Dinero contado al reiniciar los datos");

        for (Cuenta c : plan.cuentas()) {
            if (c.saldo().signum() > 0) {
                var cuenta = cuentas.cuentaActivaBloqueada(personaDe(c.usuarioId()));
                Long op = cuentas.crearOperacionInterna("SALDO_ANTERIOR", c.saldo(), null, actorId);
                cuentas.mover(cuenta, op, "ENTRADA", c.saldo().setScale(2), "SALDO_ANTERIOR");
            }
        }
        for (Socio s : plan.socios()) {
            participacion.registrarAportesAnteriores(actorId, new ParticipacionService.AportesAnteriores(s.usuarioId(), null,
                    s.aporteInicial(), s.mensualesHasta(), null, s.personales(), null), clave());
        }
        for (Letras l : plan.letras()) {
            Long id = creditos.registrar(actorId, new CreditoExistenteService.Datos(l.usuarioId(), l.modalidad(), l.importe(),
                    l.plazoMeses(), l.tasaInteres(), l.tasaDesgravamen(), l.conAhorroLetras(), l.fechaInicio(), l.letrasPagadas(),
                    l.encaje(), l.capitalDebe(), null), clave());
            em.flush();
            ahorroLetras.ajustar(actorId, id, l.ahorroLetras(), MOTIVO);
        }
        for (Cupo c : plan.cupos()) {
            creditos.registrarCupoSocio(actorId, new CreditoExistenteService.CupoSocio(c.usuarioId(), c.capitalDebe(),
                    c.interesPendiente(), c.desgravamenPendiente(), null), clave());
        }
        for (Emergente e : plan.emergentes()) {
            creditos.registrarEmergenteCliente(actorId, new CreditoExistenteService.EmergenteCliente(e.usuarioId(), e.importe(),
                    e.fechaInicio(), e.tasaInteres(), e.tasaDesgravamen(), e.capitalDebe(), e.cargosPagados(), null), clave());
        }

        // El resultado acumulado antes del reinicio incluye la reserva ya separada y las ganancias aún sin repartir.
        Long anteriores = cuentas.crearOperacionInterna("SALDO_ANTERIOR",
                plan.reserva().add(plan.gananciasPorRepartir()).add(plan.fondoDesgravamen()), null, actorId);
        saldoInicial("2.5", plan.fondoDesgravamen(), "Fondo de desgravamen al reiniciar los datos", anteriores);
        saldoInicial("3.7", plan.reserva().add(plan.gananciasPorRepartir()), "Resultado acumulado antes del reinicio", anteriores);
        if (plan.reserva().signum() > 0) {
            reservas.registrar(anteriores, plan.reserva(), "SALDO_ANTERIOR", "Fondo de reserva al reiniciar los datos");
        }
        if (plan.gananciasPorRepartir().signum() > 0) {
            participacion.repartirGananciasAnteriores(actorId, plan.gananciasPorRepartir(), "Ganancias acumuladas hasta el reinicio");
        }

        // Sin historial: queda un solo evento que dice que los datos se reiniciaron.
        em.flush();
        conDisparadoresApagados(List.of("auditoria_eventos"), () -> {
            jdbc.update("DELETE FROM auditoria_eventos");
            jdbc.execute("ALTER TABLE auditoria_eventos ALTER COLUMN id RESTART WITH 1");
        });
        auditoria.registrar(actorId, "DATOS_REINICIADOS", "SISTEMA", null, null, null, null,
                "Saldos reales cargados desde la hoja de saldos revisada por el propietario");
        em.flush();

        var estado = contabilidad.estado(null);
        Resumen resumen = new Resumen(estado.totalActivos(), estado.pasivos().total(), estado.capital().total(), estado.diferencia(),
                plan.letras().size() + plan.cupos().size() + plan.emergentes().size(), plan.cuentas().size(), plan.socios().size());
        if (estado.diferencia().signum() != 0) {
            throw new IllegalStateException("El balance no cuadra después de cargar: diferencia " + estado.diferencia());
        }
        if (plan.activosEsperados() != null && estado.totalActivos().compareTo(plan.activosEsperados()) != 0) {
            throw new IllegalStateException("Los activos son " + estado.totalActivos() + " y la hoja dice " + plan.activosEsperados());
        }
        return resumen;
    }

    private void limpiar() {
        conDisparadoresApagados(INMUTABLES, () -> {
            for (String tabla : A_VACIAR) {
                jdbc.update("DELETE FROM " + tabla);
            }
            // Los depósitos de apertura se conservan: se devuelven al cerrar la cuenta. Su ingreso ya está en la caja contada.
            jdbc.update("UPDATE depositos_apertura SET importe_total = importe_reembolsable, importe_ingreso = 0");
            jdbc.update("""
                    DELETE FROM operaciones_financieras o
                    WHERE NOT EXISTS (SELECT 1 FROM depositos_apertura d WHERE d.operacion_id = o.id)""");
            jdbc.update("""
                    UPDATE operaciones_financieras o SET importe = d.importe_reembolsable
                    FROM depositos_apertura d WHERE d.operacion_id = o.id AND d.importe_reembolsable > 0""");
            jdbc.update("UPDATE cuentas_principales SET saldo_disponible = 0");
            for (String tabla : A_VACIAR) {
                jdbc.execute("ALTER TABLE " + tabla + " ALTER COLUMN id RESTART WITH 1");
            }
        });
    }

    private void conDisparadoresApagados(List<String> tablas, Runnable accion) {
        tablas.forEach(t -> jdbc.execute("ALTER TABLE " + t + " DISABLE TRIGGER USER"));
        accion.run();
        tablas.forEach(t -> jdbc.execute("ALTER TABLE " + t + " ENABLE TRIGGER USER"));
    }

    private void saldoInicial(String linea, BigDecimal importe, String descripcion, Long operacion) {
        if (importe.signum() != 0) {
            jdbc.update("INSERT INTO saldos_iniciales (linea, importe, descripcion, operacion_id, creado_en) VALUES (?, ?, ?, ?, now())",
                    linea, importe, descripcion, operacion);
        }
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> new IllegalStateException("No existe el usuario " + usuarioId))
                .getId();
    }

    private static String clave() {
        return "reinicio-" + UUID.randomUUID();
    }
}
