package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.janery.correo.EnvioCorreo;

/** Recuperar la contraseña con un enlace por correo, contra PostgreSQL en el esquema aislado janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ EsqueletoIntegrationTest.EsquemaLimpio.class, RecuperacionCorreoIntegrationTest.CorreoDePrueba.class })
class RecuperacionCorreoIntegrationTest {

    record Enviado(String para, String texto) {
    }

    /** En vez de enviar, guarda el correo para leer el enlace. */
    @TestConfiguration
    static class CorreoDePrueba {
        static final List<Enviado> ENVIADOS = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        EnvioCorreo correoDePrueba() {
            return new EnvioCorreo() {
                @Override
                public boolean disponible() {
                    return true;
                }

                @Override
                public void enviar(String para, String asunto, String texto, String html) {
                    ENVIADOS.add(new Enviado(para, texto));
                }
            };
        }
    }

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        CorreoDePrueba.ENVIADOS.clear();
    }

    @Test
    void conElEnlaceEligeContrasenaYDesbloqueaElAcceso() throws Exception {
        String correo = registrar();
        // Tres intentos fallidos bloquean el acceso.
        for (int i = 0; i < 3; i++) {
            login(correo, "Clave-Equivocada-9");
        }
        login(correo, "Clave-Segura-1").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("ACCESO_BLOQUEADO"));

        pedir(correo).andExpect(status().isAccepted()).andExpect(jsonPath("$.medio").value("CORREO"));
        assertThat(CorreoDePrueba.ENVIADOS).hasSize(1);
        assertThat(CorreoDePrueba.ENVIADOS.get(0).para()).isEqualTo(correo);
        String token = token(CorreoDePrueba.ENVIADOS.get(0).texto());

        // Pedir otro enseguida no manda un segundo correo.
        pedir(correo).andExpect(status().isAccepted());
        assertThat(CorreoDePrueba.ENVIADOS).hasSize(1);

        confirmar(token, "Clave-Nueva-22").andExpect(status().isNoContent());
        login(correo, "Clave-Nueva-22").andExpect(status().isOk());
        // El enlace sirve una sola vez.
        confirmar(token, "Otra-Clave-33").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ENLACE_INVALIDO"));
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_contrasena FROM usuarios WHERE correo = ?", Boolean.class, correo)).isFalse();
    }

    @Test
    void noRevelaSiElCorreoExisteYRechazaEnlacesInventadosOVencidos() throws Exception {
        pedir("nadie-" + UUID.randomUUID() + "@janery.test").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.medio").value("CORREO"));
        assertThat(CorreoDePrueba.ENVIADOS).isEmpty();

        confirmar("inventado", "Clave-Nueva-22").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ENLACE_INVALIDO"));

        String correo = registrar();
        pedir(correo).andExpect(status().isAccepted());
        String token = token(CorreoDePrueba.ENVIADOS.get(0).texto());
        jdbc.update("UPDATE recuperaciones_contrasena SET expira_en = now() - interval '1 minute' WHERE usuario_id = "
                + "(SELECT id FROM usuarios WHERE correo = ?)", correo);
        confirmar(token, "Clave-Nueva-22").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ENLACE_INVALIDO"));
        login(correo, "Clave-Segura-1").andExpect(status().isOk());
    }

    // --- Apoyo

    private String registrar() throws Exception {
        String correo = "r-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", correo, "contrasena", "Clave-Segura-1"))))
                .andExpect(status().isCreated());
        return correo;
    }

    private ResultActions login(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "contrasena", contrasena))));
    }

    private ResultActions pedir(String identificador) throws Exception {
        return mvc.perform(post("/api/v1/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identificador", identificador))));
    }

    private ResultActions confirmar(String token, String nueva) throws Exception {
        return mvc.perform(post("/api/v1/auth/recuperar/confirmar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "nueva", nueva))));
    }

    private static String token(String texto) {
        Matcher m = TOKEN.matcher(texto);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
