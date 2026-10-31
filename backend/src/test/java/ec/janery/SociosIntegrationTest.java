package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

/** Aportes, participación, resultados, cierre anual y caja chica contra PostgreSQL, en el esquema janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class SociosIntegrationTest {

    private static final int ANIO_ANTERIOR = YearMonth.now(ZoneId.of("America/Guayaquil")).getYear() - 1;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private record Persona(String token, long cuenta, String correo) {
    }

    /** Cada prueba reparte solo entre sus socios. TRUNCATE no dispara los triggers de solo inserción. */
    @BeforeEach
    void aislarSocios() {
        jdbc.execute("TRUNCATE variaciones_participacion, aportes_socios, aportes_caja_chica, resultados, cierres_anuales");
        jdbc.update("update socios set estado = 'INACTIVO'");
    }

    @Test
    void aportesObligatorioYPersonalSeSeparanYAumentanLaParticipacion() throws Exception {
        Persona p = socio("100.00");
        aportar(p, "{\"tipo\":\"OBLIGATORIO\"}").andExpect(status().isOk());
        aportar(p, "{\"tipo\":\"OBLIGATORIO\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("APORTE_YA_REALIZADO"));
        aportar(p, "{\"tipo\":\"PERSONAL\",\"importe\":20.00}").andExpect(status().isOk());
        aportar(p, "{\"tipo\":\"PERSONAL\"}").andExpect(status().isBadRequest());

        JsonNode r = resumen(p);
        assertThat(r.get("totalObligatorio").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(r.get("totalPersonal").decimalValue()).isEqualByComparingTo("20");
        assertThat(r.get("valorActual").decimalValue()).isEqualByComparingTo("22.50");
        assertThat(r.get("porcentaje").decimalValue()).isEqualByComparingTo("100");
        assertThat(r.get("obligatorioDelMesPagado").asBoolean()).isTrue();
        assertThat(saldo(p)).isEqualByComparingTo("77.50");
    }

    @Test
    void periodosDeAporteAtrasadosSiFuturosNo() throws Exception {
        Persona p = socio("100.00");
        YearMonth actual = YearMonth.now(ZoneId.of("America/Guayaquil"));
        aportar(p, "{\"tipo\":\"OBLIGATORIO\",\"periodo\":\"" + actual.minusMonths(1) + "\"}").andExpect(status().isOk());
        aportar(p, "{\"tipo\":\"OBLIGATORIO\",\"periodo\":\"" + actual.plusMonths(1) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PERIODO_FUTURO"));
        aportar(p, "{\"tipo\":\"OBLIGATORIO\",\"periodo\":\"mal\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void reintentoDeAporteNoCobraDosVeces() throws Exception {
        Persona p = socio("100.00");
        String clave = UUID.randomUUID().toString();
        aportarCon(p, "{\"tipo\":\"PERSONAL\",\"importe\":10.00}", clave).andExpect(status().isOk());
        aportarCon(p, "{\"tipo\":\"PERSONAL\",\"importe\":10.00}", clave).andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("90");
    }

    @Test
    void cajaChicaEsIndependienteYNoAumentaLaParticipacion() throws Exception {
        Persona p = socio("10.00");
        BigDecimal chica = saldoDe("/api/v1/caja-chica");
        BigDecimal principal = saldoDe("/api/v1/tesoreria");

        conClave(p.token(), "/api/v1/socios/me/aportes-caja-chica", "{}").andExpect(status().isOk());
        conClave(p.token(), "/api/v1/socios/me/aportes-caja-chica", "{}").andExpect(status().isConflict());

        assertThat(saldoDe("/api/v1/caja-chica")).isEqualByComparingTo(chica.add(new BigDecimal("2.50")));
        assertThat(saldoDe("/api/v1/tesoreria")).isEqualByComparingTo(principal.subtract(new BigDecimal("2.50")));
        assertThat(resumen(p).get("valorActual").decimalValue()).isEqualByComparingTo("0");
        assertThat(resumen(p).get("totalCajaChica").decimalValue()).isEqualByComparingTo("2.50");

        BigDecimal disponible = saldoDe("/api/v1/caja-chica");
        conClave(presidente(), "/api/v1/caja-chica/gastos", "{\"importe\":" + disponible.add(BigDecimal.ONE) + ",\"detalle\":\"Exceso\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        BigDecimal principalAntes = saldoDe("/api/v1/tesoreria");
        conClave(presidente(), "/api/v1/caja-chica/gastos", "{\"importe\":1.00,\"detalle\":\"Útiles\"}").andExpect(status().isOk());
        assertThat(saldoDe("/api/v1/caja-chica")).isEqualByComparingTo(disponible.subtract(BigDecimal.ONE));
        assertThat(saldoDe("/api/v1/tesoreria")).isEqualByComparingTo(principalAntes);
    }

    @Test
    void gananciasYPerdidasSeRepartenYLaEvolucionMensualCuadra() throws Exception {
        Persona a = socio("400.00");
        Persona b = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":300.00}").andExpect(status().isOk());
        aportar(b, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());

        resultado("GANANCIA", "100.00", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("375");
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo("125");

        resultado("PERDIDA", "50.00", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("337.50");
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo("112.50");

        JsonNode periodo = json.readTree(mvc.perform(get("/api/v1/socios/me/participacion").header(HttpHeaders.AUTHORIZATION, bearer(a.token())))
                .andReturn().getResponse().getContentAsString()).get(0);
        assertThat(periodo.get("periodo").asText()).isEqualTo(YearMonth.now(ZoneId.of("America/Guayaquil")).toString());
        assertThat(periodo.get("valorInicial").decimalValue()).isEqualByComparingTo("0");
        assertThat(periodo.get("variaciones")).hasSize(3);
        assertThat(periodo.get("valorFinal").decimalValue()).isEqualByComparingTo("337.50");

        resultado("PERDIDA", "450.01", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERDIDA_EXCEDE_CAPITAL"));
        resultado("GANANCIA", "1.00", "2999-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHA_FUTURA"));
    }

    @Test
    void conservaPeriodosAnterioresConSuSaldoInicial() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":50.00}").andExpect(status().isOk());
        String antes = YearMonth.now(ZoneId.of("America/Guayaquil")).minusMonths(2).atDay(15).toString();
        resultado("GANANCIA", "10.00", antes).andExpect(status().isNoContent());

        JsonNode periodos = json.readTree(mvc.perform(get("/api/v1/socios/me/participacion").header(HttpHeaders.AUTHORIZATION, bearer(a.token())))
                .andReturn().getResponse().getContentAsString());
        // Tres meses: el de la ganancia, uno sin movimientos y el actual.
        assertThat(periodos).hasSize(3);
        assertThat(periodos.get(2).get("valorFinal").decimalValue()).isEqualByComparingTo("10");
        assertThat(periodos.get(1).get("valorInicial").decimalValue()).isEqualByComparingTo("10");
        assertThat(periodos.get(1).get("variaciones")).isEmpty();
        assertThat(periodos.get(0).get("valorInicial").decimalValue()).isEqualByComparingTo("10");
        assertThat(periodos.get(0).get("valorFinal").decimalValue()).isEqualByComparingTo("60");
    }

    @Test
    void cierreAnualSeparaDiezPorCientoYPagaElRestoUnaSolaVez() throws Exception {
        Persona a = socio("300.00");
        Persona b = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":300.00}").andExpect(status().isOk());
        aportar(b, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        String fecha = ANIO_ANTERIOR + "-06-30";
        resultado("GANANCIA", "120.00", fecha).andExpect(status().isNoContent());
        resultado("PERDIDA", "20.00", fecha).andExpect(status().isNoContent());
        BigDecimal reservaAntes = reserva();
        BigDecimal principalAntes = saldoDe("/api/v1/tesoreria");

        mvc.perform(post("/api/v1/admin/cierres").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + ANIO_ANTERIOR + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoNeto").value(100.0))
                .andExpect(jsonPath("$.reserva").value(10.0))
                .andExpect(jsonPath("$.distribuido").value(90.0));

        // Neto 100: reserva 10, y 90 repartidos 3:1 según el valor de cada socio.
        assertThat(reserva()).isEqualByComparingTo(reservaAntes.add(BigDecimal.TEN));
        assertThat(saldoDe("/api/v1/tesoreria")).isEqualByComparingTo(principalAntes.subtract(BigDecimal.TEN));
        assertThat(saldo(a)).isEqualByComparingTo("67.50");
        assertThat(saldo(b)).isEqualByComparingTo("22.50");
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("300");
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo("100");

        mvc.perform(post("/api/v1/admin/cierres").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + ANIO_ANTERIOR + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ANIO_CERRADO"));
        resultado("GANANCIA", "1.00", fecha).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ANIO_CERRADO"));
    }

    @Test
    void anioConPerdidaNoReservaNiReparte() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        resultado("PERDIDA", "10.00", ANIO_ANTERIOR + "-03-01").andExpect(status().isNoContent());
        BigDecimal reservaAntes = reserva();
        mvc.perform(post("/api/v1/admin/cierres").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + ANIO_ANTERIOR + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserva").value(0.0)).andExpect(jsonPath("$.distribuido").value(0.0));
        assertThat(reserva()).isEqualByComparingTo(reservaAntes);
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("90");
    }

    @Test
    void anioEnCursoNoSeCierra() throws Exception {
        mvc.perform(post("/api/v1/admin/cierres").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + (ANIO_ANTERIOR + 1) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ANIO_EN_CURSO"));
    }

    @Test
    void permisosYAccesoDeNoSocios() throws Exception {
        Persona cliente = persona("0");
        String auth = bearer(cliente.token());
        mvc.perform(get("/api/v1/socios/me").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
        aportar(cliente, "{\"tipo\":\"PERSONAL\",\"importe\":1.00}").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/caja-chica").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/socios").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/cierres").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":2020}"))
                .andExpect(status().isForbidden());
        conClave(cliente.token(), "/api/v1/caja-chica/gastos", "{\"importe\":1.00,\"detalle\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void elCargoDeCajaChicaGestionaSoloEseFondo() throws Exception {
        Persona p = persona("0");
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, p.correo());
        mvc.perform(post("/api/v1/admin/usuarios/" + id + "/cargos/CAJA_CHICA").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        String token = login(p.correo(), "Clave-Segura-1");
        mvc.perform(get("/api/v1/caja-chica").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tesoreria").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void unSocioActivoNoCierraSuCuenta() throws Exception {
        Persona p = socio("0");
        mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOCIO_ACTIVO"));
    }

    // --- Ayudas

    private ResultActions resultado(String tipo, String importe, String fecha) throws Exception {
        String extra = fecha == null ? "" : ",\"fecha\":\"" + fecha + "\"";
        return mvc.perform(post("/api/v1/admin/resultados").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\",\"importe\":" + importe + ",\"concepto\":\"Prueba\"" + extra + "}"));
    }

    private ResultActions aportar(Persona p, String cuerpo) throws Exception {
        return conClave(p.token(), "/api/v1/socios/me/aportes", cuerpo);
    }

    private ResultActions aportarCon(Persona p, String cuerpo, String clave) throws Exception {
        return mvc.perform(post("/api/v1/socios/me/aportes").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions conClave(String token, String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode resumen(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/socios/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("saldoDisponible").decimalValue();
    }

    private BigDecimal saldoDe(String ruta) throws Exception {
        return json.readTree(mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("saldo").decimalValue();
    }

    private BigDecimal reserva() {
        return jdbc.queryForObject("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from movimientos_caja m join cajas c on c.id = m.caja_id where c.codigo = 'FONDO_RESERVA'
                """, BigDecimal.class);
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

    private Persona socio(String saldoInicial) throws Exception {
        Persona p = persona(saldoInicial);
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, p.correo());
        mvc.perform(post("/api/v1/admin/usuarios/" + id + "/socio").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        return p;
    }

    /** Persona con perfil, cuenta activa y el saldo indicado. */
    private Persona persona(String saldoInicial) throws Exception {
        String correo = "s-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombres", "Ana", "apellidos", "Prueba",
                                "identificacion", UUID.randomUUID().toString().substring(0, 18),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        String r = mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cuenta = json.readTree(r).get("id").asLong();
        String admin = bearer(presidente());
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        if (new BigDecimal(saldoInicial).signum() > 0) {
            mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/depositos").header(HttpHeaders.AUTHORIZATION, admin)
                            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"importe\":" + saldoInicial + ",\"metodoPago\":\"EFECTIVO\"}"))
                    .andExpect(status().isOk());
        }
        return new Persona(token, cuenta, correo);
    }
}
