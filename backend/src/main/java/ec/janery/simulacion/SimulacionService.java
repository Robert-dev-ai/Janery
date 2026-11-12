package ec.janery.simulacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import lombok.RequiredArgsConstructor;

@Service
@Profile("simulacion")
@RequiredArgsConstructor
public class SimulacionService {

    private static final int MAX_DIAS = 3660;

    /** activa: hay una copia de los datos reales en uso, hecha en iniciadaEn. */
    public record Estado(LocalDate fecha, Instant ahora, long diasAdelantados, boolean activa, Instant iniciadaEn,
            String iniciadaPor) {
    }

    private final RelojSimulado reloj;
    private final AuditoriaService auditoria;
    private final org.flywaydb.core.Flyway flyway;
    private final CopiaDatosReales copia;
    private final ec.janery.usuario.AuthService auth;
    private final ec.janery.cuenta.CuentaService cuentas;
    private final ec.janery.socio.ParticipacionService participacion;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public Estado estado() {
        // La tabla existe solo mientras hay una simulación: al finalizarla, la limpieza de la base la borra.
        boolean existe = Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('simulacion_estado') IS NOT NULL", Boolean.class));
        List<Map<String, Object>> fila = existe ? jdbc.queryForList("SELECT iniciada_en, iniciada_por FROM simulacion_estado") : List.of();
        Instant inicio = fila.isEmpty() ? null : ((java.sql.Timestamp) fila.get(0).get("iniciada_en")).toInstant();
        return new Estado(LocalDate.now(reloj), reloj.instant(), reloj.diasAdelantados(), inicio != null, inicio,
                fila.isEmpty() ? null : (String) fila.get(0).get("iniciada_por"));
    }

    /**
     * Interés de ahorros y mora se calculan con la fecha al consultarlos: no hay procesos que ejecutar al cruzar un mes.
     * Se indica una sola de las dos opciones: días a sumar o fecha de destino.
     */
    public synchronized Estado avanzar(Long actorId, Integer dias, LocalDate fecha) {
        if ((dias == null) == (fecha == null)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DESTINO_INVALIDO", "Indique los días o la fecha, solo uno de los dos");
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (dias != null && dias > MAX_DIAS) {
            throw excesivo();
        }
        LocalDate destino = fecha != null ? fecha : hoy.plusDays(dias);
        if (!destino.isAfter(hoy)) {
            throw NegocioException.conflicto("FECHA_NO_FUTURA", "La fecha simulada solo puede avanzar");
        }
        if (destino.isAfter(hoy.plusDays(MAX_DIAS))) {
            throw excesivo();
        }
        LocalTime hora = LocalTime.now(reloj);
        reloj.avanzarHasta(destino.atTime(hora).atZone(reloj.getZone()).toInstant());
        auditoria.registrar(actorId, "RELOJ_AVANZADO", "SIMULACION", "reloj", null, hoy.toString(), destino.toString(), null);
        return estado();
    }

    /**
     * Copia toda la base real tal como está ahora: sobre esa copia se prueba lo que se quiera. La base real no cambia: se
     * lee por una conexión de solo lectura.
     */
    public synchronized Estado iniciar(Long actorId) {
        exigirBaseDeSimulacion();
        List<Map<String, Object>> sesiones = sesionesAbiertas();
        // Sin borrar la estructura: copiarTodo vacía cada tabla antes de copiar. Solo se aplican migraciones pendientes.
        flyway.migrate();
        jdbc.execute("DROP TABLE IF EXISTS simulacion_estado");
        jdbc.update("DELETE FROM tokens_refresco");
        reloj.volverAFechaReal();
        copia.copiarTodo();
        restaurarSesiones(sesiones);
        String quien = jdbc.queryForList("SELECT correo FROM usuarios WHERE id = ?", String.class, actorId).stream().findFirst().orElse(null);
        jdbc.execute("CREATE TABLE IF NOT EXISTS simulacion_estado (id int PRIMARY KEY CHECK (id = 1), iniciada_en timestamptz NOT NULL, iniciada_por varchar(254))");
        jdbc.update("INSERT INTO simulacion_estado (id, iniciada_en, iniciada_por) VALUES (1, now(), ?)", quien);
        auditoria.registrar(actorId, "SIMULACION_INICIADA", "SIMULACION", "simulador", null, null, null, null);
        return estado();
    }

    /**
     * Descarta todo lo simulado. Quedan solo el personal y los parámetros, para poder entrar e iniciar otra simulación.
     */
    public synchronized Estado finalizar(Long actorId) {
        exigirBaseDeSimulacion();
        List<Map<String, Object>> sesiones = sesionesAbiertas();
        flyway.clean();
        flyway.migrate();
        reloj.volverAFechaReal();
        copia.copiarPersonal();
        restaurarSesiones(sesiones);
        auditoria.registrar(actorId, "SIMULACION_FINALIZADA", "SIMULACION", "simulador", null, null, null, null);
        return estado();
    }

    /**
     * Iniciar y finalizar vacían la base conectada. La URL ya se comprobó al arrancar, pero se pregunta a la propia base:
     * una URL mal configurada no debe bastar para borrar datos reales.
     */
    private void exigirBaseDeSimulacion() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conexion -> {
            ec.janery.config.ProteccionDatosReales.exigirDesechable(conexion, List.of(conexion.getSchema()));
            return null;
        });
    }

    /** Las sesiones del simulador viven en su base: se conservan para que nadie tenga que volver a entrar. */
    private List<Map<String, Object>> sesionesAbiertas() {
        return jdbc.queryForList("SELECT usuario_id, hash_token, expira_en, creado_en FROM tokens_refresco WHERE revocado_en IS NULL AND expira_en > now()");
    }

    private void restaurarSesiones(List<Map<String, Object>> sesiones) {
        for (Map<String, Object> s : sesiones) {
            jdbc.update("""
                    INSERT INTO tokens_refresco (usuario_id, hash_token, expira_en, creado_en)
                    SELECT ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM usuarios WHERE id = ?)
                    """, s.get("usuario_id"), s.get("hash_token"), s.get("expira_en"), s.get("creado_en"), s.get("usuario_id"));
        }
    }

    public record PersonaPrueba(String correo, String contrasena, String nombre, String cuenta) {
    }

    private static final String CLAVE_PRUEBA = "Prueba-1234";

    /** Una persona con cuenta activa y saldo, lista para probar créditos, ahorros o aportes. Opcionalmente, socia. */
    @org.springframework.transaction.annotation.Transactional
    public synchronized PersonaPrueba crearPersona(Long actorId, String nombres, java.math.BigDecimal saldo, boolean socio) {
        String sufijo = Long.toString(System.currentTimeMillis(), 36);
        String correo = "prueba-" + sufijo + "@simulador.janery";
        String cedula = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        Long usuarioId = auth.registrarConPerfil(correo, CLAVE_PRUEBA, new ec.janery.persona.PersonaController.PerfilRequest(
                nombres, "Prueba", cedula, "+593 999999999", "Simulador"));
        var cuenta = cuentas.solicitarApertura(usuarioId, "EFECTIVO", null).cuenta();
        cuentas.confirmarApertura(actorId, cuenta.getId());
        java.math.BigDecimal aporte = socio ? participacion.condiciones().aporteInicial() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal total = saldo.add(aporte);
        if (total.signum() > 0) {
            cuentas.depositar(actorId, cuenta.getId(), total, "EFECTIVO", null, "prueba-" + sufijo);
        }
        if (socio) {
            participacion.hacerseSocio(usuarioId, "prueba-socio-" + sufijo);
        }
        auditoria.registrar(actorId, "PERSONA_PRUEBA_CREADA", "SIMULACION", "usuario", usuarioId, null, correo, null);
        return new PersonaPrueba(correo, CLAVE_PRUEBA, nombres + " Prueba", cuenta.getNumero());
    }

    private static NegocioException excesivo() {
        return NegocioException.conflicto("SALTO_EXCESIVO", "No se puede avanzar más de " + MAX_DIAS + " días de una vez");
    }
}
