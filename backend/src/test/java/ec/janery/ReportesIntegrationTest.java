package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.janery.reportes.ReporteService;

/** Informes, áreas y permisos por cargo, contra PostgreSQL en el esquema janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class ReportesIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReporteService reportes;

    private record Persona(String token, long cuenta, String correo, String apellido) {
    }

    @Test
    void todosLosInformesEjecutanYExportan() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        String auth = bearer(presidente());
        for (ReporteService.Definicion d : reportes.catalogo()) {
            mvc.perform(get("/api/v1/admin/reportes/" + d.codigo()).header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.columnas").isNotEmpty());
            mvc.perform(get("/api/v1/admin/reportes/" + d.codigo() + "/csv").header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/admin/reportes/NO_EXISTE").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
    }

    @Test
    void conciliacionCuadraTrasOperarYLasCajasVanSeparadas() throws Exception {
        Persona p = persona();
        depositar(p, "80.00");
        JsonNode t = informe("CONCILIACION", "");
        int diferencia = t.get("columnas").size() - 1;
        for (JsonNode fila : t.get("filas")) {
            assertThat(new java.math.BigDecimal(fila.get(diferencia).asText())).isEqualByComparingTo("0");
        }
        JsonNode principal = informe("CAJA_PRINCIPAL", "");
        assertThat(principal.get("filas").toString()).contains("DEPOSITO_CUENTA", "INGRESO_APERTURA");
        assertThat(informe("CAJA_CHICA", "").get("filas").toString()).doesNotContain("DEPOSITO_CUENTA");
    }

    @Test
    void fechasFiltranYSeValidan() throws Exception {
        Persona p = persona();
        depositar(p, "10.00");
        assertThat(informe("ESTADO_CUENTAS", "?hasta=2001-01-01").get("filas")).isEmpty();
        assertThat(informe("ESTADO_CUENTAS", "").get("filas").toString()).contains(p.apellido());
        mvc.perform(get("/api/v1/admin/reportes/ESTADO_CUENTAS?desde=2026-02-01&hasta=2026-01-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
    }

    @Test
    void csvUsaFormatoDeExcelYNeutralizaFormulas() {
        byte[] bytes = ec.janery.reportes.CsvDePrueba.generar(new ReporteService.Tabla(List.of("Texto", "Importe"), List.of(false, true),
                List.of(List.of("=HYPERLINK(\"x\")", "12.50"), java.util.Arrays.asList(null, "-3.00")), false));
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿\"Texto\";\"Importe\"\r\n");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\";12,50");
        assertThat(csv).contains(";-3,00");
    }

    @Test
    void exportarQuedaAuditado() throws Exception {
        Integer antes = jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'INFORME_EXPORTADO'", Integer.class);
        mvc.perform(get("/api/v1/admin/reportes/CREDITOS/csv").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'INFORME_EXPORTADO'", Integer.class))
                .isEqualTo(antes + 1);
    }

    @Test
    void estadoDeCuentaPropioNoIncluyeAOtrasPersonas() throws Exception {
        Persona a = persona();
        Persona b = persona();
        depositar(a, "11.00");
        depositar(b, "22.00");
        String csv = mvc.perform(get("/api/v1/cuentas-principales/me/estado-de-cuenta.csv").header(HttpHeaders.AUTHORIZATION, bearer(a.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains(a.apellido(), "11,00").doesNotContain(b.apellido());
    }

    @Test
    void sinPermisoNoHayInformesNiAdministracionDeCargos() throws Exception {
        String auth = bearer(persona().token());
        mvc.perform(get("/api/v1/admin/reportes").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reportes/CREDITOS/csv").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/cargos").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/cargos/SECRETARIA/permisos/CONSULTAR_INFORMES").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasAreasSoloMuestranLosCargosDelUsuario() throws Exception {
        Persona p = persona();
        mvc.perform(get("/api/v1/areas/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        asignarCargo(p, "ASUNTOS_SOCIALES");
        mvc.perform(get("/api/v1/areas/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("ASUNTOS_SOCIALES"))
                .andExpect(jsonPath("$[0].permisos").isEmpty());
    }

    @Test
    void concederPermisoACargoLoHabilitaYRetirarloLoQuita() throws Exception {
        Persona p = persona();
        asignarCargo(p, "SECRETARIA");
        String admin = bearer(presidente());
        String ruta = "/api/v1/admin/cargos/SECRETARIA/permisos/CONSULTAR_INFORMES";

        mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNoContent());
        // Conceder dos veces no falla ni duplica.
        mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/reportes").header(HttpHeaders.AUTHORIZATION, bearer(login(p.correo(), "Clave-Segura-1"))))
                .andExpect(status().isOk());

        mvc.perform(delete(ruta).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/reportes").header(HttpHeaders.AUTHORIZATION, bearer(login(p.correo(), "Clave-Segura-1"))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/cargos/INVENTADO/permisos/CONSULTAR_INFORMES").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void presidenciaNoPuedePerderLaAdministracionDeUsuarios() throws Exception {
        mvc.perform(delete("/api/v1/admin/cargos/PRESIDENCIA/permisos/ADMIN_USUARIOS").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PERMISO_PROTEGIDO"));
    }

    // --- Ayudas

    private JsonNode informe(String codigo, String consulta) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/admin/reportes/" + codigo + consulta).header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void asignarCargo(Persona p, String cargo) throws Exception {
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, p.correo());
        mvc.perform(post("/api/v1/admin/usuarios/" + id + "/cargos/" + cargo).header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
    }

    private void depositar(Persona p, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/depositos").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String correo, String contrasena) throws Exception {
        String r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("accessToken").asText();
    }

    private String presidente() throws Exception {
        return login("presidente@janery.test", "Clave-Presidente-1");
    }

    private Persona persona() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 12);
        String correo = "r-" + sufijo + "@janery.test";
        String apellido = "Apellido" + sufijo;
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombres", "Ana", "apellidos", apellido,
                                "identificacion", sufijo, "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        String r = mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cuenta = json.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        return new Persona(token, cuenta, correo, apellido);
    }
}
