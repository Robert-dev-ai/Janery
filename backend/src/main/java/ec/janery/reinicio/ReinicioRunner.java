package ec.janery.reinicio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * TEMPORAL: corre el reinicio desde la consola, sin servidor web, contra la base del .env. Exige el perfil «reinicio», la
 * ruta del plan y escribir el host de la base en --janery.reinicio.confirmar, para no lanzarlo por error contra otra.
 */
@Component
@Profile("reinicio")
@RequiredArgsConstructor
class ReinicioRunner implements ApplicationRunner {

    private final ReinicioService reinicio;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    @Value("${janery.reinicio.plan}")
    private String plan;
    @Value("${janery.reinicio.confirmar:}")
    private String confirmar;
    @Value("${JANERY_DB_HOST:localhost}")
    private String host;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!host.equals(confirmar)) {
            throw new IllegalStateException("Para reiniciar la base de " + host + " escriba ese host en --janery.reinicio.confirmar");
        }
        ReinicioService.Plan datos = json.readValue(Files.readString(Path.of(plan)), ReinicioService.Plan.class);
        Long actor = jdbc.queryForObject("""
                SELECT uc.usuario_id FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                WHERE c.codigo = 'PRESIDENCIA' AND uc.hasta IS NULL ORDER BY uc.usuario_id LIMIT 1""", Long.class);
        // Los ajustes del ahorro letras exigen el permiso de quien registra datos anteriores.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null,
                List.of(new SimpleGrantedAuthority("SCOPE_REGISTRAR_CREDITOS_EXISTENTES"))));
        ReinicioService.Resumen r = reinicio.ejecutar(actor, datos);
        System.out.println("REINICIO HECHO: " + json.writeValueAsString(r));
    }
}
