package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** La presidencia crea la cuenta de una persona, contra PostgreSQL en el esquema aislado janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class AltaPersonaIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void creaUsuarioConCuentaActivaYContrasenaTemporal() throws Exception {
        String correo = correo();
        String cedula = cedula();
        JsonNode alta = json.readTree(crear(presidente(), correo, cedula).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long usuarioId = alta.get("usuarioId").asLong();
        String temporal = alta.get("contrasenaTemporal").asText();
        assertThat(temporal).hasSize(10);
        assertThat(alta.get("cuenta").asText()).startsWith("JAN-");

        assertThat(jdbc.queryForObject("""
                SELECT c.estado FROM cuentas_principales c JOIN personas p ON p.id = c.persona_id WHERE p.usuario_id = ?""",
                String.class, usuarioId)).isEqualTo("ACTIVA");
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_contrasena FROM usuarios WHERE id = ?", Boolean.class, usuarioId)).isTrue();

        // Entra con su cédula y la contraseña temporal.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", cedula, "contrasena", temporal))))
                .andExpect(status().isOk());
    }

    @Test
    void siLaCedulaYaExisteNoQuedaNadaAMedias() throws Exception {
        String cedula = cedula();
        crear(presidente(), correo(), cedula).andExpect(status().isCreated());
        String otro = correo();
        crear(presidente(), otro, cedula).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE correo = ?", Long.class, otro)).isZero();
    }

    @Test
    void soloQuienAdministraUsuariosPuedeCrear() throws Exception {
        String correo = correo();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        crear(login(correo, "Clave-Segura-1"), correo(), cedula()).andExpect(status().isForbidden());
        crear(presidente(), "no-es-correo", cedula()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").exists());
    }

    // --- Apoyo

    private ResultActions crear(String token, String correo, String cedula) throws Exception {
        return mvc.perform(post("/api/v1/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "nombres", "Ana", "apellidos", "Ventanilla",
                        "identificacion", cedula, "telefono", "0990000000", "direccion", "Quito"))));
    }

    private static String correo() {
        return "alta-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
    }

    private static String cedula() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
    }

    private String login(String identificador, String contrasena) throws Exception {
        String r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", identificador, "contrasena", contrasena))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("accessToken").asText();
    }

    private String presidente() throws Exception {
        return login("presidente@janery.test", "Clave-Presidente-1");
    }
}
