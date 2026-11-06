package ec.janery.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.stereotype.Component;

/**
 * Última barrera antes de borrar datos: pruebas, simulador y datos reales comparten servidor y rol de PostgreSQL, y solo
 * la configuración los separa. Se pregunta a la propia base dónde se está, sin fiarse de la URL ni de los nombres
 * configurados, y se rechaza cualquier limpieza fuera de una base de simulación o de esquemas de prueba.
 *
 * <p>Como callback de Flyway se ejecuta antes de cada {@code clean}, lo lance quien lo lance.
 */
@Component
public class ProteccionDatosReales implements Callback {

    /** Desechable: una base de simulación, o solo esquemas de prueba (terminados en {@code _test}). */
    public static boolean esDesechable(String base, Collection<String> esquemas) {
        if (base != null && base.toLowerCase(Locale.ROOT).contains("simulacion")) {
            return true;
        }
        return !esquemas.isEmpty()
                && esquemas.stream().allMatch(e -> e != null && e.toLowerCase(Locale.ROOT).endsWith("_test"));
    }

    /** Lanza si la conexión no está en una base o esquema desechable. esquemas: los que se van a tocar. */
    public static void exigirDesechable(Connection conexion, Collection<String> esquemas) throws SQLException {
        String base;
        try (Statement consulta = conexion.createStatement();
                ResultSet rs = consulta.executeQuery("SELECT current_database()")) {
            rs.next();
            base = rs.getString(1);
        }
        if (!esDesechable(base, esquemas)) {
            throw new IllegalStateException("Operación destructiva rechazada: la base " + base + " con los esquemas "
                    + esquemas + " puede contener datos reales. Solo se permite en una base de simulación o en esquemas *_test");
        }
    }

    @Override
    public boolean supports(Event evento, Context contexto) {
        return evento == Event.BEFORE_CLEAN;
    }

    @Override
    public boolean canHandleInTransaction(Event evento, Context contexto) {
        return true;
    }

    @Override
    public void handle(Event evento, Context contexto) {
        try {
            exigirDesechable(contexto.getConnection(), esquemas(contexto.getConfiguration()));
        } catch (SQLException | IllegalStateException e) {
            throw new FlywayException(e.getMessage(), e);
        }
    }

    /** Los esquemas que limpiaría Flyway: los configurados y el predeterminado. */
    private static List<String> esquemas(Configuration configuracion) {
        List<String> esquemas = new ArrayList<>(Arrays.asList(configuracion.getSchemas()));
        if (configuracion.getDefaultSchema() != null && !esquemas.contains(configuracion.getDefaultSchema())) {
            esquemas.add(configuracion.getDefaultSchema());
        }
        return esquemas;
    }

    @Override
    public String getCallbackName() {
        return "proteccion-datos-reales";
    }
}
