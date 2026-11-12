package ec.janery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;

class ProteccionDatosRealesTest {

    private final ProteccionDatosReales proteccion = new ProteccionDatosReales();

    @Test
    void soloSonDesechablesLaBaseDeSimulacionYLosEsquemasDePrueba() {
        assertThat(ProteccionDatosReales.esDesechable("janery_simulacion", List.of("janery"))).isTrue();
        assertThat(ProteccionDatosReales.esDesechable("neondb", List.of("janery_test"))).isTrue();
        assertThat(ProteccionDatosReales.esDesechable("neondb", List.of("janery_simulacion_test"))).isTrue();

        assertThat(ProteccionDatosReales.esDesechable("neondb", List.of("janery"))).isFalse();
        assertThat(ProteccionDatosReales.esDesechable("neondb", List.of("janery_test", "janery"))).isFalse();
        assertThat(ProteccionDatosReales.esDesechable("neondb", List.of())).isFalse();
        assertThat(ProteccionDatosReales.esDesechable("janery", List.of("public"))).isFalse();
    }

    @Test
    void flywayNoPuedeLimpiarElEsquemaReal() throws Exception {
        Context contexto = contexto("neondb", "janery");

        assertThat(proteccion.supports(Event.BEFORE_CLEAN, contexto)).isTrue();
        assertThat(proteccion.supports(Event.BEFORE_MIGRATE, contexto)).isFalse();
        assertThatThrownBy(() -> proteccion.handle(Event.BEFORE_CLEAN, contexto))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("rechazada");
    }

    @Test
    void flywayPuedeLimpiarLosEsquemasDePruebaYElSimulador() throws Exception {
        assertThatCode(() -> proteccion.handle(Event.BEFORE_CLEAN, contexto("neondb", "janery_test"))).doesNotThrowAnyException();
        assertThatCode(() -> proteccion.handle(Event.BEFORE_CLEAN, contexto("janery_simulacion", "janery"))).doesNotThrowAnyException();
    }

    private static Context contexto(String base, String esquema) throws Exception {
        ResultSet filas = mock(ResultSet.class);
        when(filas.next()).thenReturn(true);
        when(filas.getString(1)).thenReturn(base);
        Statement consulta = mock(Statement.class);
        when(consulta.executeQuery(anyString())).thenReturn(filas);
        Connection conexion = mock(Connection.class);
        when(conexion.createStatement()).thenReturn(consulta);
        Configuration configuracion = mock(Configuration.class);
        when(configuracion.getSchemas()).thenReturn(new String[] { esquema });
        when(configuracion.getDefaultSchema()).thenReturn(esquema);
        Context contexto = mock(Context.class);
        when(contexto.getConnection()).thenReturn(conexion);
        when(contexto.getConfiguration()).thenReturn(configuracion);
        return contexto;
    }
}
