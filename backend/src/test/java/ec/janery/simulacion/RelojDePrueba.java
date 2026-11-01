package ec.janery.simulacion;

import java.time.Clock;

import org.springframework.jdbc.core.JdbcTemplate;

/** Acceso de las pruebas a lo que este paquete mantiene privado. */
public final class RelojDePrueba {

    private RelojDePrueba() {
    }

    public static Clock nuevo(Clock real, JdbcTemplate jdbc) {
        return new RelojSimulado(real, jdbc);
    }

    public static void validarBase(String url) {
        SimulacionConfig.validarBase(url);
    }
}
