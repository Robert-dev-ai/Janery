package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Avisos en tiempo real contra PostgreSQL, en el esquema janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class TiempoRealIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void sinTokenNoHayConexion() throws Exception {
        mvc.perform(get("/api/v1/eventos")).andExpect(status().isUnauthorized());
    }

    @Test
    void unaOperacionConfirmadaAvisaSoloElModuloSinDatos() throws Exception {
        String correo = registrar();
        MvcResult conexion = conectar(login(correo));

        perfil(login(correo), "{\"nombres\":\"Ana\",\"apellidos\":\"Secreto\",\"identificacion\":\"" + identificacion()
                + "\",\"telefono\":\"0990000000\",\"direccion\":\"Quito\"}").andExpectCreated();

        String recibido = conexion.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(recibido).contains("event:cambio").contains("data:PERSONA").doesNotContain("Secreto");
    }

    @Test
    void unaOperacionRechazadaOUnInicioDeSesionNoAvisan() throws Exception {
        String correo = registrar();
        String token = login(correo);
        MvcResult conexion = conectar(token);

        login(correo);
        // Perfil con un dato obligatorio vacío: no se guarda nada.
        perfil(token, "{\"nombres\":\"\",\"apellidos\":\"x\",\"identificacion\":\"x\",\"telefono\":\"x\",\"direccion\":\"x\"}");

        assertThat(conexion.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("event:cambio");
    }

    private record Respuesta(int status) {
        void andExpectCreated() {
            assertThat(status).isEqualTo(201);
        }
    }

    private MvcResult conectar(String token) throws Exception {
        return mvc.perform(get("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(request().asyncStarted()).andReturn();
    }

    private Respuesta perfil(String token, String cuerpo) throws Exception {
        return new Respuesta(mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus());
    }

    private static String identificacion() {
        return UUID.randomUUID().toString().substring(0, 18);
    }

    private String registrar() throws Exception {
        String correo = "v-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        return correo;
    }

    private String login(String correo) throws Exception {
        String r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("accessToken").asText();
    }
}
