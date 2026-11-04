package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import ec.janery.persona.Persona;

/**
 * Lleva los nombres y apellidos ya registrados al formato con que se guardan desde ahora: mayúscula inicial en cada
 * palabra. En Java y no en SQL para aplicar exactamente la misma regla que el registro (Persona.formatearNombre), sin
 * depender de cómo la base pase a minúsculas las letras con tilde. Cada cambio queda en la auditoría.
 */
public class V25__formatear_nombres extends BaseJavaMigration {

    private record Cambio(long id, String antes, String despues, String nombres, String apellidos) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        List<Cambio> cambios = new ArrayList<>();
        try (PreparedStatement consulta = context.getConnection().prepareStatement("SELECT id, nombres, apellidos FROM personas");
                ResultSet rs = consulta.executeQuery()) {
            while (rs.next()) {
                String nombres = rs.getString(2);
                String apellidos = rs.getString(3);
                String nuevosNombres = Persona.formatearNombre(nombres);
                String nuevosApellidos = Persona.formatearNombre(apellidos);
                if (!nombres.equals(nuevosNombres) || !apellidos.equals(nuevosApellidos)) {
                    cambios.add(new Cambio(rs.getLong(1), nombres + " " + apellidos, nuevosNombres + " " + nuevosApellidos,
                            nuevosNombres, nuevosApellidos));
                }
            }
        }
        try (PreparedStatement actualizar = context.getConnection()
                .prepareStatement("UPDATE personas SET nombres = ?, apellidos = ? WHERE id = ?");
                PreparedStatement auditar = context.getConnection().prepareStatement("""
                        INSERT INTO auditoria_eventos (actor_id, accion, modulo, entidad, entidad_id, antes, despues, motivo)
                        VALUES (NULL, 'NOMBRE_FORMATEADO', 'PERSONA', 'persona', ?, ?, ?, 'Mayúscula inicial en cada palabra')
                        """)) {
            for (Cambio c : cambios) {
                actualizar.setString(1, c.nombres());
                actualizar.setString(2, c.apellidos());
                actualizar.setLong(3, c.id());
                actualizar.addBatch();
                auditar.setString(1, String.valueOf(c.id()));
                auditar.setString(2, c.antes());
                auditar.setString(3, c.despues());
                auditar.addBatch();
            }
            actualizar.executeBatch();
            auditar.executeBatch();
        }
    }
}
