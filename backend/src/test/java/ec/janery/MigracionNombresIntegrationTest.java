package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import ec.janery.persona.Persona;

/**
 * La migración V25 formatea en SQL los nombres que ya estaban guardados. Debe dar lo mismo que Persona.formatearNombre,
 * que formatea los que se registran desde entonces: si no, habría nombres antiguos y nuevos con formatos distintos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class MigracionNombresIntegrationTest {

    private static final String[] NOMBRES = { "maría  josé", "  JOSÉ LUIS  ", "ruiz-pérez", "ÑUSTA", "o'brien", "ana\tbelén",
            "de la cruz", "a", "ÁNGEL - DAVID", "Ya Bien" };

    @Autowired
    private DataSource datos;

    @Test
    void laMigracionFormateaIgualQueElRegistro() throws Exception {
        String migracion = new ClassPathResource("db/migration/V25__formatear_nombres.sql").getContentAsString(StandardCharsets.UTF_8);
        String funcion = migracion.substring(migracion.indexOf("CREATE FUNCTION"), migracion.indexOf("$$;") + 3);
        // Una sola conexión: la función es temporal y solo existe en ella.
        try (Connection conexion = datos.getConnection(); Statement crear = conexion.createStatement();
                PreparedStatement formatear = conexion.prepareStatement("SELECT pg_temp.formatear_nombre(?)")) {
            crear.execute(funcion);
            for (String nombre : NOMBRES) {
                formatear.setString(1, nombre);
                try (ResultSet rs = formatear.executeQuery()) {
                    rs.next();
                    assertThat(rs.getString(1)).as(nombre).isEqualTo(Persona.formatearNombre(nombre));
                }
            }
            crear.execute("DROP FUNCTION pg_temp.formatear_nombre(TEXT)");
        }
    }
}
