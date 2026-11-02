package ec.janery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableScheduling
public class JaneryApplication {

    public static void main(String[] args) {
        // Hikari comprueba la conexión con un viaje a la base si lleva más de medio segundo sin usarse: con Neon, a otro
        // país, eso suma una espera a casi cada petición. Neon solo apaga la base tras 5 minutos sin uso, así que una
        // conexión usada en los últimos 30 segundos sigue viva.
        if (System.getProperty("com.zaxxer.hikari.aliveBypassWindowMs") == null) {
            System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "30000");
        }
        SpringApplication.run(JaneryApplication.class, args);
    }
}
