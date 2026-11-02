package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/** Editar el perfil, cambiar la contraseña y cerrar sesiones, contra PostgreSQL en el esquema janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class ConfiguracionIntegrationTest {

    private static final String CLAVE = "Clave-Segura-1";
    private static final String CLAVE_NUEVA = "Clave-Nueva-22";
    private static final String COOKIE = "janery_refresh";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cambiaTelefonoYDireccionSinTocarLosDemasDatos() throws Exception {
        String token = token(login(registrar(), CLAVE));
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Ana\",\"apellidos\":\"Paz\",\"identificacion\":\"" + identificacion()
                                + "\",\"telefono\":\"0990000000\",\"direccion\":\"Quito\"}"))
                .andExpect(status().isCreated());

        // Los campos no editables que lleguen en el cuerpo se ignoran.
        contacto(token, "{\"telefono\":\" 0980000000 \",\"direccion\":\"Cuenca\",\"nombres\":\"Otro\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("0980000000"))
                .andExpect(jsonPath("$.direccion").value("Cuenca"))
                .andExpect(jsonPath("$.nombres").value("Ana"));

        contacto(token, "{\"telefono\":\"\",\"direccion\":\"Cuenca\"}").andExpect(status().isBadRequest());
    }

    @Test
    void sinPerfilNoHayContactoQueEditar() throws Exception {
        contacto(token(login(registrar(), CLAVE)), "{\"telefono\":\"099\",\"direccion\":\"Quito\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void cambiarContrasenaExigeLaActualYCierraLasDemasSesiones() throws Exception {
        String correo = registrar();
        MockHttpServletResponse otra = login(correo, CLAVE);
        MockHttpServletResponse esta = login(correo, CLAVE);

        contrasena(esta, CLAVE, CLAVE_NUEVA).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(correo, CLAVE)))
                .andExpect(status().isUnauthorized());
        login(correo, CLAVE_NUEVA);
        refrescar(esta).andExpect(status().isOk());
        refrescar(otra).andExpect(status().isUnauthorized());
    }

    @Test
    void contrasenaActualIncorrectaSeRechazaYQuedaAuditada() throws Exception {
        String correo = registrar();
        MockHttpServletResponse sesion = login(correo, CLAVE);

        contrasena(sesion, "Clave-Equivocada-1", CLAVE_NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));

        login(correo, CLAVE);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria_eventos a JOIN usuarios u ON u.id = a.actor_id"
                        + " WHERE u.correo = ? AND a.accion = 'CONTRASENA_FALLIDA'",
                Integer.class, correo)).isEqualTo(1);
    }

    @Test
    void laContrasenaNuevaDebeSerDistintaYValida() throws Exception {
        MockHttpServletResponse sesion = login(registrar(), CLAVE);
        contrasena(sesion, CLAVE, CLAVE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_REPETIDA"));
        contrasena(sesion, CLAVE, "corta").andExpect(status().isBadRequest());
    }

    @Test
    void cerrarLasDemasSesionesConservaLaActual() throws Exception {
        String correo = registrar();
        MockHttpServletResponse otra = login(correo, CLAVE);
        MockHttpServletResponse esta = login(correo, CLAVE);

        mvc.perform(post("/api/v1/auth/sesiones/cerrar-otras")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(esta))
                        .cookie(new Cookie(COOKIE, esta.getCookie(COOKIE).getValue())))
                .andExpect(status().isNoContent());

        refrescar(esta).andExpect(status().isOk());
        refrescar(otra).andExpect(status().isUnauthorized());
    }

    @Test
    void estasOperacionesExigenSesion() throws Exception {
        mvc.perform(post("/api/v1/auth/sesiones/cerrar-otras")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/contrasena").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"" + CLAVE + "\",\"nueva\":\"" + CLAVE_NUEVA + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/personas/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\":\"099\",\"direccion\":\"Quito\"}")).andExpect(status().isUnauthorized());
    }

    private ResultActions contacto(String token, String cuerpo) throws Exception {
        return mvc.perform(put("/api/v1/personas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions contrasena(MockHttpServletResponse sesion, String actual, String nueva) throws Exception {
        return mvc.perform(post("/api/v1/auth/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(sesion))
                .cookie(new Cookie(COOKIE, sesion.getCookie(COOKIE).getValue()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"" + actual + "\",\"nueva\":\"" + nueva + "\"}"));
    }

    private ResultActions refrescar(MockHttpServletResponse sesion) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, sesion.getCookie(COOKIE).getValue())));
    }

    private static String credenciales(String correo, String clave) {
        return "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}";
    }

    private static String identificacion() {
        return UUID.randomUUID().toString().substring(0, 18);
    }

    private String registrar() throws Exception {
        String correo = "c-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credenciales(correo, CLAVE)))
                .andExpect(status().isCreated());
        return correo;
    }

    private MockHttpServletResponse login(String correo, String clave) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(correo, clave)))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    private String token(MockHttpServletResponse sesion) throws Exception {
        return json.readTree(sesion.getContentAsString()).get("accessToken").asText();
    }
}
