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
    void tresContrasenasIncorrectasBloqueanYLaPresidenciaDaUnaTemporal() throws Exception {
        String correo = registrar();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(correo, "Mala-Clave-1")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("CREDENCIALES"));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(correo, "Mala-Clave-1")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("ACCESO_BLOQUEADO"));
        // Bloqueado, ni la contraseña correcta sirve.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(correo, CLAVE)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("ACCESO_BLOQUEADO"));

        mvc.perform(post("/api/v1/auth/recuperar").contentType(MediaType.APPLICATION_JSON).content("{\"identificador\":\"" + correo + "\"}"))
                .andExpect(status().isAccepted());
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, correo);
        String presidente = token(login("presidente@janery.test", "Clave-Presidente-1"));
        String temporal = json.readTree(mvc.perform(post("/api/v1/admin/usuarios/" + id + "/restablecer-contrasena")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("contrasenaTemporal").asText();
        assertThat(jdbc.queryForObject("select count(*) from solicitudes_restablecimiento where usuario_id = ? and estado = 'PENDIENTE'",
                Integer.class, id)).isZero();

        MockHttpServletResponse sesion = login(correo, temporal);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(sesion)))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true));
        contrasena(sesion, temporal, CLAVE_NUEVA).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select debe_cambiar_contrasena from usuarios where id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    void avisoDeLaCabeceraVisitasYDatosPublicos() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/public/info"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value("+593 996619908"))
                .andExpect(jsonPath("$.whatsapp").value("593996619908"));

        String presidente = "Bearer " + token(login("presidente@janery.test", "Clave-Presidente-1"));
        String persona = "Bearer " + token(login(registrar(), CLAVE));
        String texto = "Reunión de socios el sábado " + UUID.randomUUID().toString().substring(0, 4);
        mvc.perform(put("/api/v1/admin/anuncio").header(HttpHeaders.AUTHORIZATION, persona)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/anuncio").header(HttpHeaders.AUTHORIZATION, presidente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/anuncio").header(HttpHeaders.AUTHORIZATION, persona))
                .andExpect(jsonPath("$.texto").value(texto));

        String ruta = "/app/prueba-" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/v1/uso/visitas").header(HttpHeaders.AUTHORIZATION, persona)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ruta\":\"" + ruta + "\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/uso/visitas").header(HttpHeaders.AUTHORIZATION, persona)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ruta\":\"https://otro.sitio\"}")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/uso").header(HttpHeaders.AUTHORIZATION, persona))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from visitas where ruta = ?", Integer.class, ruta)).isEqualTo(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/uso")
                .header(HttpHeaders.AUTHORIZATION, presidente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.visitas").isNumber()).andExpect(jsonPath("$.pantallas").isArray());
    }

    @Test
    void laPresidenciaCorrigeUnCorreoMalEscrito() throws Exception {
        String correo = registrar();
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, correo);
        String correcto = "bien-" + UUID.randomUUID().toString().substring(0, 8) + "@janery.test";
        String presidente = token(login("presidente@janery.test", "Clave-Presidente-1"));
        mvc.perform(put("/api/v1/admin/usuarios/" + id + "/correo").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente)
                .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"" + correcto.toUpperCase() + "\"}"))
                .andExpect(status().isNoContent());
        login(correcto, CLAVE);
        mvc.perform(put("/api/v1/admin/usuarios/" + id + "/correo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(login(correcto, CLAVE)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"otro@janery.test\"}"))
                .andExpect(status().isForbidden());
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
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
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
