package ec.janery.simulacion;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("simulacion")
class SimulacionConfig {

    /** Se comprueba antes de crear cualquier bean: Flyway no llega a tocar una base equivocada. */
    @Bean
    static BeanFactoryPostProcessor exigirBaseDeSimulacion(Environment entorno) {
        return fabrica -> validarBase(entorno.getRequiredProperty("spring.datasource.url"));
    }

    static void validarBase(String url) {
        if (!url.contains("simulacion")) {
            throw new IllegalStateException("El perfil simulacion solo puede usar una base o esquema de simulación");
        }
    }

    @Bean
    RelojSimulado reloj(@Value("${janery.zona-horaria}") String zona, JdbcTemplate jdbc) {
        return new RelojSimulado(Clock.system(ZoneId.of(zona)), jdbc);
    }
}
