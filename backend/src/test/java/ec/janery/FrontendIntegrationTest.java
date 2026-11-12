package ec.janery;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** El frontend publicado en el mismo dominio que la API (FrontendConfig), con un index.html de prueba. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class FrontendIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sirveLaAplicacionSinIniciarSesionEnCualquierRutaDeAngular() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("frontend-de-prueba")));
        mvc.perform(get("/creditos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("frontend-de-prueba")));
        mvc.perform(get("/admin/usuarios")).andExpect(status().isOk())
                .andExpect(content().string(containsString("frontend-de-prueba")));
    }

    @Test
    void laApiSigueProtegidaYLosArchivosQueFaltanDan404() throws Exception {
        mvc.perform(get("/api/v1/cuentas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/no-existe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/main-NOEXISTE.js")).andExpect(status().isNotFound());
    }

    @Test
    void elEstadoPublicoRespondeSinBaseDeDatos() throws Exception {
        mvc.perform(get("/api/v1/public/estado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("disponible"));
    }
}
