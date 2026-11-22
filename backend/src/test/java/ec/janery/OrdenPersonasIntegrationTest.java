package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.janery.consultas.ConsultaPersonasService;

/** Los listados muestran «Apellidos Nombres», primero los socios y luego las demás personas, cada grupo alfabético. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class OrdenPersonasIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ConsultaPersonasService consultas;

    @Test
    void sociosPrimeroYLuegoLasDemasPersonasPorApellido() throws Exception {
        // Una marca propia en el apellido: el listado solo trae a estas cuatro personas.
        String marca = "Qx" + UUID.randomUUID().toString().substring(0, 6);
        String token = presidente();
        crear(token, "Bravo " + marca, "Luis");
        crear(token, "Álvarez " + marca, "Ana");
        long zuniga = crear(token, "Zúñiga " + marca, "Pedro");
        long castro = crear(token, "Castro " + marca, "María");
        hacerSocio(zuniga);
        hacerSocio(castro);

        List<String> nombres = consultas.personasFinancieras(marca, 0, 50).contenido().stream()
                .map(ConsultaPersonasService.PersonaFinanciera::nombre).toList();

        assertThat(nombres).containsExactly(
                "Castro " + marca + " María", "Zúñiga " + marca + " Pedro",
                "Álvarez " + marca + " Ana", "Bravo " + marca + " Luis");
    }

    @Test
    void laBusquedaAceptaElNombreEnCualquierOrden() throws Exception {
        String marca = "Qx" + UUID.randomUUID().toString().substring(0, 6);
        crear(presidente(), "Paredes " + marca, "Rosa");

        // Nombres y luego apellidos, o apellidos y luego nombres: las dos formas la encuentran.
        assertThat(consultas.personasFinancieras("rosa paredes " + marca, 0, 10).contenido()).hasSize(1);
        assertThat(consultas.personasFinancieras("paredes " + marca + " rosa", 0, 10).contenido()).hasSize(1);
    }

    // --- Apoyo

    /** Crea la persona con su cuenta activa y devuelve el id de la persona. */
    private long crear(String token, String apellidos, String nombres) throws Exception {
        String r = mvc.perform(post("/api/v1/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", "orden-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test",
                                "nombres", nombres, "apellidos", apellidos, "identificacion",
                                String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long usuarioId = json.readTree(r).get("usuarioId").asLong();
        return jdbc.queryForObject("SELECT id FROM personas WHERE usuario_id = ?", Long.class, usuarioId);
    }

    private void hacerSocio(long personaId) {
        jdbc.update("INSERT INTO socios (persona_id, desde, estado) VALUES (?, now(), 'ACTIVO')", personaId);
    }

    private String presidente() throws Exception {
        String r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", "presidente@janery.test", "contrasena", "Clave-Presidente-1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("accessToken").asText();
    }
}
