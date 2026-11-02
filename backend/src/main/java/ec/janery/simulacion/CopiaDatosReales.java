package ec.janery.simulacion;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/**
 * Lee la base real con una conexión de solo lectura: PostgreSQL rechaza cualquier escritura por ella. Al iniciar una
 * simulación copia toda la base tal como está; al finalizarla deja solo al personal, para que pueda volver a entrar.
 */
@Component
@Profile("simulacion")
class CopiaDatosReales {

    private final JdbcTemplate real;
    /** Prefijo de las tablas reales: el proxy de Neon ignora currentSchema. */
    private final String e;
    private final NamedParameterJdbcTemplate simulada;

    CopiaDatosReales(JdbcTemplate simulada, @Value("${JANERY_DB_HOST:localhost}") String host,
            @Value("${JANERY_DB_PORT:5432}") String puerto, @Value("${JANERY_DB_NAME:janery}") String base,
            @Value("${JANERY_DB_SCHEMA:janery}") String esquema, @Value("${JANERY_DB_SSLMODE:prefer}") String ssl,
            @Value("${spring.datasource.username:}") String usuario, @Value("${spring.datasource.password:}") String clave) {
        if (base.contains("simulacion")) {
            throw new IllegalStateException("JANERY_DB_NAME debe ser la base real, no la de simulación");
        }
        // readOnlyMode=always: PostgreSQL rechaza cualquier escritura por esta conexión.
        DriverManagerDataSource fuente = new DriverManagerDataSource(
                "jdbc:postgresql://%s:%s/%s?currentSchema=%s&sslmode=%s&readOnly=true&readOnlyMode=always"
                        .formatted(host, puerto, base, esquema, ssl), usuario, clave);
        this.real = new JdbcTemplate(fuente);
        this.e = esquema.replaceAll("[^a-zA-Z0-9_]", "") + ".";
        this.simulada = new NamedParameterJdbcTemplate(simulada);
    }

    /** Tablas que no se copian: el historial de Flyway, las sesiones, las visitas y el estado propio del simulador. */
    private static final Set<String> SIN_COPIA = Set.of("flyway_schema_history", "tokens_refresco", "visitas",
            "simulacion_reloj", "simulacion_estado");

    /**
     * Copia toda la base real, tal como está en este momento, sobre la simulada: personas, cuentas, créditos, ahorros,
     * socios, cajas, contabilidad y auditoría. Antes vacía la simulada. Las dos deben estar en la misma versión.
     */
    void copiarTodo() {
        String esquema = e.substring(0, e.length() - 1);
        String versionReal = real.queryForObject("SELECT version FROM " + e
                + "flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class);
        String versionSimulada = simulada.getJdbcTemplate().queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class);
        if (!java.util.Objects.equals(versionReal, versionSimulada)) {
            throw ec.janery.common.NegocioException.conflicto("VERSIONES_DISTINTAS", "La base real está en la versión "
                    + versionReal + " y el simulador en la " + versionSimulada + ": actualice ambos a la misma versión");
        }
        List<String> tablas = real.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'
                """, String.class, esquema).stream().filter(t -> !SIN_COPIA.contains(t)).toList();
        Map<String, Set<String>> dependencias = new java.util.HashMap<>();
        real.query("""
                SELECT c.conrelid::regclass::text, c.confrelid::regclass::text FROM pg_constraint c
                WHERE c.contype = 'f' AND c.connamespace = ?::regnamespace
                """, (java.sql.ResultSet rs) -> {
            String tabla = sinEsquema(rs.getString(1));
            String referida = sinEsquema(rs.getString(2));
            if (!tabla.equals(referida)) {
                dependencias.computeIfAbsent(tabla, k -> new java.util.HashSet<>()).add(referida);
            }
        }, esquema);
        Set<String> conIdentidad = new java.util.HashSet<>(real.queryForList("""
                SELECT table_name FROM information_schema.columns WHERE table_schema = ? AND is_identity = 'YES'
                """, String.class, esquema));

        simulada.getJdbcTemplate().execute("TRUNCATE " + String.join(", ", tablas) + " RESTART IDENTITY CASCADE");
        for (String tabla : ordenar(tablas, dependencias)) {
            copiarTabla(tabla, conIdentidad.contains(tabla));
        }
        // Secuencias propias, como la del número de cuenta: siguen donde iban en la base real.
        real.query("""
                SELECT s.relname, q.last_value FROM pg_class s JOIN pg_sequences q ON q.schemaname = ? AND q.sequencename = s.relname
                WHERE s.relkind = 'S' AND s.relnamespace = ?::regnamespace
                  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = s.oid AND d.deptype IN ('a', 'i'))
                """, (java.sql.ResultSet rs) -> {
            long ultimo = rs.getLong(2);
            simulada.getJdbcTemplate().execute("SELECT setval('" + rs.getString(1).replaceAll("[^a-z0-9_]", "") + "', "
                    + Math.max(ultimo, 1) + ", " + !rs.wasNull() + ")");
        }, esquema, esquema);
        for (String tabla : conIdentidad) {
            if (tablas.contains(tabla)) {
                simulada.getJdbcTemplate().execute("SELECT setval(pg_get_serial_sequence('" + tabla + "', 'id'), "
                        + "COALESCE((SELECT max(id) FROM " + tabla + "), 1), (SELECT max(id) FROM " + tabla + ") IS NOT NULL)");
            }
        }
    }

    /** Primero las tablas de las que dependen otras. */
    private static List<String> ordenar(List<String> tablas, Map<String, Set<String>> dependencias) {
        List<String> orden = new java.util.ArrayList<>();
        Set<String> hechas = new java.util.HashSet<>();
        while (orden.size() < tablas.size()) {
            boolean avanzo = false;
            for (String t : tablas) {
                if (!hechas.contains(t) && dependencias.getOrDefault(t, Set.of()).stream()
                        .allMatch(d -> hechas.contains(d) || !tablas.contains(d))) {
                    orden.add(t);
                    hechas.add(t);
                    avanzo = true;
                }
            }
            if (!avanzo) {
                throw new IllegalStateException("Dependencias circulares entre tablas: no se puede copiar la base");
            }
        }
        return orden;
    }

    private void copiarTabla(String tabla, boolean conId) {
        List<MapSqlParameterSource> lote = new java.util.ArrayList<>();
        String[] sql = new String[1];
        real.query("SELECT * FROM " + e + tabla + (conId ? " ORDER BY id" : ""), (java.sql.ResultSet rs) -> {
            var meta = rs.getMetaData();
            MapSqlParameterSource fila = new MapSqlParameterSource();
            List<String> columnas = new java.util.ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columnas.add(meta.getColumnName(i));
                fila.addValue(meta.getColumnName(i), rs.getObject(i));
            }
            if (sql[0] == null) {
                sql[0] = "INSERT INTO " + tabla + " (" + String.join(", ", columnas) + ")" + (conId ? " OVERRIDING SYSTEM VALUE" : "")
                        + " VALUES (" + columnas.stream().map(c -> ":" + c).collect(Collectors.joining(", ")) + ")";
            }
            lote.add(fila);
            if (lote.size() == 500) {
                simulada.batchUpdate(sql[0], lote.toArray(MapSqlParameterSource[]::new));
                lote.clear();
            }
        });
        if (!lote.isEmpty()) {
            simulada.batchUpdate(sql[0], lote.toArray(MapSqlParameterSource[]::new));
        }
    }

    private static String sinEsquema(String nombre) {
        return nombre.substring(nombre.lastIndexOf('.') + 1).replace("\"", "");
    }

    /** Requiere la base simulada recién creada por las migraciones: sin usuarios propios que choquen. */
    void copiarPersonal() {
        String personal = "SELECT DISTINCT usuario_id FROM " + e + "usuario_cargos WHERE hasta IS NULL";
        copiar("usuarios", true, "SELECT id, correo, hash_contrasena, estado, creado_en FROM " + e + "usuarios WHERE id IN (" + personal + ")");
        copiar("personas", true, "SELECT * FROM " + e + "personas WHERE usuario_id IN (" + personal + ")");
        copiar("usuario_cargos", true, "SELECT id, usuario_id, cargo_id, desde, hasta, NULL::bigint AS asignado_por FROM " + e
                + "usuario_cargos WHERE hasta IS NULL");
        simulada.getJdbcTemplate().update("DELETE FROM cargo_permisos");
        copiar("cargo_permisos", false, "SELECT cargo_id, permiso_id FROM " + e + "cargo_permisos");
        copiarParametros();
        for (String tabla : List.of("usuarios", "personas", "usuario_cargos")) {
            simulada.getJdbcTemplate().execute("SELECT setval(pg_get_serial_sequence('" + tabla + "', 'id'), "
                    + "GREATEST((SELECT max(id) FROM " + tabla + "), 1))");
        }
    }

    /** Solo cambia los valores que difieren: el historial del simulador muestra de dónde vino cada uno. */
    private void copiarParametros() {
        for (Map<String, Object> p : real.queryForList("""
                SELECT s.codigo, v.valor FROM %1$sparametro_valores v JOIN %1$sparametros_sistema s ON s.id = v.parametro_id
                WHERE v.vigente_hasta IS NULL""".formatted(e))) {
            MapSqlParameterSource datos = new MapSqlParameterSource(p);
            int cambiados = simulada.update("""
                    UPDATE parametro_valores SET vigente_hasta = now()
                    WHERE vigente_hasta IS NULL AND valor <> :valor
                      AND parametro_id = (SELECT id FROM parametros_sistema WHERE codigo = :codigo)""", datos);
            if (cambiados > 0) {
                simulada.update("""
                        INSERT INTO parametro_valores (parametro_id, valor, vigente_desde, motivo)
                        SELECT id, :valor, now(), 'Copiado de los datos reales al reiniciar el simulador'
                        FROM parametros_sistema WHERE codigo = :codigo""", datos);
            }
        }
    }

    private void copiar(String tabla, boolean conId, String consulta) {
        for (Map<String, Object> fila : real.queryForList(consulta)) {
            String columnas = String.join(", ", fila.keySet());
            String valores = fila.keySet().stream().map(c -> ":" + c).collect(Collectors.joining(", "));
            simulada.update("INSERT INTO " + tabla + " (" + columnas + ")" + (conId ? " OVERRIDING SYSTEM VALUE" : "") + " VALUES (" + valores + ")",
                    new MapSqlParameterSource(fila));
        }
    }
}
