package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        jdbc.execute("TRUNCATE variaciones_participacion, aportes_socios, aportes_caja_chica, resultados, cierres_anuales, compraventas_capital, autorizaciones_retiro");
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
    void periodosDeAporteAtrasadosYDelAnioSiDelSiguienteNo() throws Exception {
        Persona p = socio("100.00");
        YearMonth actual = YearMonth.now(ZoneId.of("America/Guayaquil"));
        aportar(p, "{\"tipo\":\"OBLIGATORIO\",\"periodo\":\"" + actual.minusMonths(1) + "\"}").andExpect(status().isOk());
        aportar(p, "{\"tipo\":\"OBLIGATORIO\",\"periodo\":\"" + YearMonth.of(actual.getYear(), 12) + "\"}").andExpect(status().isOk());
        aportar(p, "{\"tipo\":\"OBLIGATORIO\",\"periodo\":\"" + YearMonth.of(actual.getYear() + 1, 1) + "\"}")
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
    void elCentavoQueNoSePuedeRepartirEsperaHastaSerDivisible() throws Exception {
        Persona a = socio("100.00");
        Persona b = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        aportar(b, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        BigDecimal valorA = resumen(a).get("valorActual").decimalValue();

        // 0,01 no alcanza para dos socios: nadie recibe nada y el centavo queda a la espera.
        resultado("GANANCIA", "0.01", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(valorA);
        assertThat(enEspera().get("ganancia").decimalValue()).isEqualByComparingTo("0.01");

        // Con otro centavo ya es divisible: a cada uno 0,01 y no queda nada.
        resultado("GANANCIA", "0.01", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(valorA.add(new BigDecimal("0.01")));
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo(valorA.add(new BigDecimal("0.01")));
        assertThat(enEspera().get("ganancia").decimalValue()).isEqualByComparingTo("0");

        // Lo mismo con una pérdida, y lo que sobra de una división inexacta también espera.
        resultado("PERDIDA", "0.03", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(valorA);
        assertThat(enEspera().get("perdida").decimalValue()).isEqualByComparingTo("0.01");
    }

    private JsonNode enEspera() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/admin/resultados/en-espera").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void gananciasYPerdidasSeRepartenYLaEvolucionMensualCuadra() throws Exception {
        Persona a = socio("400.00");
        Persona b = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":300.00}").andExpect(status().isOk());
        aportar(b, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());

        // Ganancia de 100: 10 a reserva y 90 repartidos 3:1.
        resultado("GANANCIA", "100.00", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("367.50");
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo("122.50");

        // La pérdida se reparte completa, y no toca la reserva.
        BigDecimal reservaTrasGanancia = reserva();
        resultado("PERDIDA", "49.00", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("330.75");
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo("110.25");
        assertThat(reserva()).isEqualByComparingTo(reservaTrasGanancia);

        JsonNode periodo = json.readTree(mvc.perform(get("/api/v1/socios/me/participacion").header(HttpHeaders.AUTHORIZATION, bearer(a.token())))
                .andReturn().getResponse().getContentAsString()).get(0);
        assertThat(periodo.get("periodo").asText()).isEqualTo(YearMonth.now(ZoneId.of("America/Guayaquil")).toString());
        assertThat(periodo.get("valorInicial").decimalValue()).isEqualByComparingTo("0");
        assertThat(periodo.get("variaciones")).hasSize(3);
        assertThat(periodo.get("valorFinal").decimalValue()).isEqualByComparingTo("330.75");

        resultado("PERDIDA", "441.01", null).andExpect(status().isConflict())
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
        // Ganancia de 10, sin los 1 de reserva.
        assertThat(periodos.get(2).get("valorFinal").decimalValue()).isEqualByComparingTo("9");
        assertThat(periodos.get(1).get("valorInicial").decimalValue()).isEqualByComparingTo("9");
        assertThat(periodos.get(1).get("variaciones")).isEmpty();
        assertThat(periodos.get(0).get("valorInicial").decimalValue()).isEqualByComparingTo("9");
        assertThat(periodos.get(0).get("valorFinal").decimalValue()).isEqualByComparingTo("59");
    }

    @Test
    void cadaGananciaSeparaSuReservaAlMomentoYElCierreSoloTotaliza() throws Exception {
        Persona a = socio("300.00");
        Persona b = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":300.00}").andExpect(status().isOk());
        aportar(b, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        String fecha = ANIO_ANTERIOR + "-06-30";
        BigDecimal reservaAntes = reserva();
        BigDecimal principalAntes = saldoDe("/api/v1/tesoreria");

        resultado("GANANCIA", "120.00", fecha).andExpect(status().isNoContent());
        assertThat(reserva()).isEqualByComparingTo(reservaAntes.add(new BigDecimal("12")));
        assertThat(saldoDe("/api/v1/tesoreria")).isEqualByComparingTo(principalAntes.subtract(new BigDecimal("12")));
        resultado("PERDIDA", "20.00", fecha).andExpect(status().isNoContent());

        BigDecimal valorA = resumen(a).get("valorActual").decimalValue();
        BigDecimal reservaAntesDelCierre = reserva();
        mvc.perform(post("/api/v1/admin/cierres").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"anio\":" + ANIO_ANTERIOR + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoNeto").value(100.0))
                .andExpect(jsonPath("$.reserva").value(12.0))
                .andExpect(jsonPath("$.distribuido").value(88.0));

        // 108 repartidos y 20 de pérdida: todo queda en los fondos personales, sin pagos a las cuentas.
        assertThat(valorA).isEqualByComparingTo("366");
        assertThat(resumen(b).get("valorActual").decimalValue()).isEqualByComparingTo("122");
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(valorA);
        assertThat(saldo(a)).isEqualByComparingTo("0");
        assertThat(saldo(b)).isEqualByComparingTo("0");
        assertThat(reserva()).isEqualByComparingTo(reservaAntesDelCierre);

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
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserva").value(0.0)).andExpect(jsonPath("$.distribuido").value(-10.0));
        assertThat(reserva()).isEqualByComparingTo(reservaAntes);
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo("90");
    }

    @Test
    void elFondoPersonalEsUnoSoloPeroSeSeparaPorOrigen() throws Exception {
        Persona a = socio("100.00");
        long socioId = resumen(a).get("socioId").asLong();
        BigDecimal caja = saldoDe("/api/v1/tesoreria");
        String clave = UUID.randomUUID().toString();
        String cuerpo = "{\"importe\":200.00,\"metodoPago\":\"EFECTIVO\"}";
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/v1/admin/socios/" + socioId + "/aporte-inicial").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                            .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk());
        }
        // El dinero entra de fuera y no sale de su cuenta principal; el reintento no duplica.
        assertThat(saldoDe("/api/v1/tesoreria")).isEqualByComparingTo(caja.add(new BigDecimal("200")));
        assertThat(saldo(a)).isEqualByComparingTo("100");

        aportar(a, "{\"tipo\":\"OBLIGATORIO\"}").andExpect(status().isOk());
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":7.50}").andExpect(status().isOk());
        resultado("GANANCIA", "10.00", null).andExpect(status().isNoContent());

        JsonNode r = resumen(a);
        assertThat(r.get("totalInicial").decimalValue()).isEqualByComparingTo("200");
        assertThat(r.get("totalObligatorio").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(r.get("totalPersonal").decimalValue()).isEqualByComparingTo("7.50");
        assertThat(r.get("ganancias").decimalValue()).isEqualByComparingTo("9");
        assertThat(r.get("valorActual").decimalValue()).isEqualByComparingTo("219");
    }

    @Test
    void aporteInicialExigePermiso() throws Exception {
        Persona a = socio("0");
        long socioId = resumen(a).get("socioId").asLong();
        conClave(a.token(), "/api/v1/admin/socios/" + socioId + "/aporte-inicial", "{\"importe\":1.00,\"metodoPago\":\"EFECTIVO\"}")
                .andExpect(status().isForbidden());
    }

    // --- Retiro de ganancias

    @Test
    void gananciaDelAnioEnCursoNoSeRetiraYLaDeAniosAnterioresSi() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        autorizar(a).andExpect(status().isNoContent());
        resultado("GANANCIA", "10.00", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("0");
        retirarGanancia(a, "1.00").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("GANANCIA_NO_DISPONIBLE"));

        resultado("GANANCIA", "100.00", ANIO_ANTERIOR + "-06-30").andExpect(status().isNoContent());
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("90");
        retirarGanancia(a, "90.01").andExpect(status().isConflict());

        retirarGanancia(a, "40.00").andExpect(status().isOk());
        assertThat(saldo(a)).isEqualByComparingTo("40");
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("50");
        assertThat(resumen(a).get("totalPersonal").decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void sinAutorizacionDelPresidenteNoHayRetiroYPuedeRevocarse() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        resultado("GANANCIA", "100.00", ANIO_ANTERIOR + "-06-30").andExpect(status().isNoContent());
        retirarGanancia(a, "1.00").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RETIRO_NO_AUTORIZADO"));

        autorizar(a).andExpect(status().isNoContent());
        assertThat(resumen(a).get("retiroAutorizado").asBoolean()).isTrue();
        retirarGanancia(a, "1.00").andExpect(status().isOk());

        mvc.perform(delete("/api/v1/admin/socios/" + resumen(a).get("socioId").asLong() + "/autorizacion-retiro")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        retirarGanancia(a, "1.00").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RETIRO_NO_AUTORIZADO"));
    }

    @Test
    void perdidaDeEsteAnioLimitaElRetiro() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        resultado("GANANCIA", "100.00", ANIO_ANTERIOR + "-06-30").andExpect(status().isNoContent());
        resultado("PERDIDA", "30.00", null).andExpect(status().isNoContent());
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("60");
    }

    @Test
    void losAportesNuncaSeRetiran() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        autorizar(a).andExpect(status().isNoContent());
        retirarGanancia(a, "1.00").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("GANANCIA_NO_DISPONIBLE"));
    }

    @Test
    void elMenuMuestraLoQueDebeYSoloSeDescuentaLoElegido() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        long socioId = resumen(a).get("socioId").asLong();
        resultado("GANANCIA", "100.00", ANIO_ANTERIOR + "-06-30").andExpect(status().isNoContent());
        entroHaceMeses(socioId, 2);

        // Nada se descuenta por sí solo.
        JsonNode menu = menu(socioId);
        assertThat(menu.get("obligaciones")).hasSize(4);
        assertThat(menu.get("total").decimalValue()).isEqualByComparingTo("10");
        assertThat(menu.get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("90");
        autorizar(a).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("OBLIGACIONES_PENDIENTES"));

        String periodo = menu.get("obligaciones").get(0).get("periodo").asText();
        BigDecimal chica = saldoDe("/api/v1/caja-chica");
        String clave = UUID.randomUUID().toString();
        String cuerpo = "{\"descuentos\":[{\"tipo\":\"APORTE_MENSUAL\",\"periodo\":\"" + periodo + "\"},{\"tipo\":\"CAJA_CHICA\",\"periodo\":\"" + periodo + "\"}]}";
        for (int intento = 0; intento < 2; intento++) {
            descontar(socioId, cuerpo, clave).andExpect(status().isNoContent());
        }

        JsonNode r = resumen(a);
        assertThat(r.get("obligacionesPendientes").decimalValue()).isEqualByComparingTo("5");
        assertThat(r.get("totalObligatorio").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(r.get("totalCajaChica").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(r.get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("85");
        assertThat(saldoDe("/api/v1/caja-chica")).isEqualByComparingTo(chica.add(new BigDecimal("2.50")));
        // El aporte mensual sigue en el fondo; lo de caja chica sale de él.
        assertThat(r.get("valorActual").decimalValue()).isEqualByComparingTo("187.50");

        descontar(socioId, cuerpo, UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("OBLIGACION_INEXISTENTE"));
    }

    @Test
    void descuentoNoSuperaLasGananciasYNoAplicaNadaSiFallaUnaParte() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        long socioId = resumen(a).get("socioId").asLong();
        resultado("GANANCIA", "3.00", ANIO_ANTERIOR + "-06-30").andExpect(status().isNoContent());
        entroHaceMeses(socioId, 1);
        String periodo = menu(socioId).get("obligaciones").get(0).get("periodo").asText();

        descontar(socioId, "{\"descuentos\":[{\"tipo\":\"APORTE_MENSUAL\",\"periodo\":\"" + periodo + "\"},{\"tipo\":\"CAJA_CHICA\",\"periodo\":\"" + periodo + "\"}]}", UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("GANANCIAS_INSUFICIENTES"));

        JsonNode r = resumen(a);
        assertThat(r.get("totalObligatorio").decimalValue()).isEqualByComparingTo("0");
        assertThat(r.get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("2.70");
    }

    @Test
    void elMesEnCursoNoCuentaComoVencido() throws Exception {
        Persona a = socio("0");
        assertThat(resumen(a).get("obligacionesPendientes").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void cuotaVencidaSePuedeDescontarEnParteCobrandoPrimeroElCapital() throws Exception {
        Persona a = socio("100.00");
        aportar(a, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        long socioId = resumen(a).get("socioId").asLong();
        resultado("GANANCIA", "1000.00", ANIO_ANTERIOR + "-06-30").andExpect(status().isNoContent());
        jdbc.update("update personas set direccion = 'Quito' where usuario_id = (select id from usuarios where correo = ?)", a.correo());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/personas/me/datos-credito").header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"lugarTrabajo":"Tienda","ocupacion":"Vendedora","antiguedadTrabajo":"2 años","ingresosMensuales":500.00,
                         "fechaNacimiento":"1990-01-01","estadoCivil":"SOLTERO","referenciaNombre":"Luis","referenciaTelefono":"099",
                         "banco":"Pichincha","tipoCuentaBanco":"AHORROS","numeroCuentaBanco":"123"}""")).andExpect(status().isOk());
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long credito = json.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/" + credito + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        BigDecimal saldoAntes = saldo(a);
        // Dos días: la fecha de la base de datos está en UTC y de noche ya es el día siguiente.
        jdbc.update("update cuotas_credito set vencimiento = current_date - 2 where credito_id = ? and numero = 1", credito);
        BigDecimal cuota = jdbc.queryForObject("select total from cuotas_credito where credito_id = ? and numero = 1", BigDecimal.class, credito);
        JsonNode linea = menu(socioId).get("obligaciones").get(0);
        assertThat(linea.get("tipo").asText()).isEqualTo("CUOTA");
        assertThat(linea.get("importe").decimalValue()).isEqualByComparingTo(cuota);
        long cuotaId = linea.get("cuotaId").asLong();

        descontar(socioId, "{\"descuentos\":[{\"tipo\":\"CUOTA\",\"cuotaId\":" + cuotaId + ",\"importe\":60.00}]}", UUID.randomUUID().toString())
                .andExpect(status().isNoContent());

        // Los 60 van a capital, y la cuota sigue pendiente por lo que falta.
        assertThat(jdbc.queryForObject("select capital_pagado from cuotas_credito where id = ?", BigDecimal.class, cuotaId)).isEqualByComparingTo("60");
        assertThat(jdbc.queryForObject("select interes_pagado from cuotas_credito where id = ?", BigDecimal.class, cuotaId)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select capital_pendiente from creditos where id = ?", BigDecimal.class, credito)).isEqualByComparingTo("240");
        assertThat(menu(socioId).get("obligaciones").get(0).get("importe").decimalValue()).isEqualByComparingTo(cuota.subtract(new BigDecimal("60")));
        assertThat(saldo(a)).isEqualByComparingTo(saldoAntes);

        descontar(socioId, "{\"descuentos\":[{\"tipo\":\"CUOTA\",\"cuotaId\":" + cuotaId + "}]}", UUID.randomUUID().toString())
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select pagada_en is not null from cuotas_credito where id = ?", Boolean.class, cuotaId)).isTrue();
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo(new BigDecimal("900").subtract(cuota));
        assertThat(saldo(a)).isEqualByComparingTo(saldoAntes);
    }

    @Test
    void decidirRetirosExigePermiso() throws Exception {
        Persona a = socio("0");
        long socioId = resumen(a).get("socioId").asLong();
        String auth = bearer(a.token());
        mvc.perform(get("/api/v1/admin/socios/" + socioId + "/obligaciones").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/socios/" + socioId + "/autorizacion-retiro").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        conClave(a.token(), "/api/v1/admin/socios/" + socioId + "/descuentos", "{\"descuentos\":[]}").andExpect(status().isForbidden());
    }

// --- Compraventa

    @Test
    void compraventaMueveCapitalYPagaAlVendedorSinTocarLaCaja() throws Exception {
        Persona v = socio("100.00");
        Persona c = socio("150.00");
        aportar(v, "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        long vendedor = resumen(v).get("socioId").asLong();
        long comprador = resumen(c).get("socioId").asLong();
        BigDecimal caja = saldoDe("/api/v1/tesoreria");

        compraventa(vendedor, comprador, "100.01", "10.00").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAPITAL_INSUFICIENTE"));
        compraventa(vendedor, vendedor, "1.00", "1.00").andExpect(status().isBadRequest());
        compraventa(vendedor, comprador, "40.00", "150.01").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));

        compraventa(vendedor, comprador, "40.00", "50.00").andExpect(status().isOk());

        assertThat(resumen(v).get("valorActual").decimalValue()).isEqualByComparingTo("60");
        assertThat(resumen(c).get("valorActual").decimalValue()).isEqualByComparingTo("40");
        assertThat(resumen(c).get("compraventas").decimalValue()).isEqualByComparingTo("40");
        assertThat(resumen(c).get("ganancias").decimalValue()).isEqualByComparingTo("0");
        assertThat(saldo(v)).isEqualByComparingTo("50");
        assertThat(saldo(c)).isEqualByComparingTo("100");
        assertThat(saldoDe("/api/v1/tesoreria")).isEqualByComparingTo(caja);
    }

    @Test
    void compraventaExigePermiso() throws Exception {
        Persona a = socio("0");
        conClave(a.token(), "/api/v1/admin/socios/compraventas",
                "{\"vendedorId\":1,\"compradorId\":2,\"capital\":1.00,\"precio\":1.00}").andExpect(status().isForbidden());
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

    private ResultActions autorizar(Persona p) throws Exception {
        return mvc.perform(post("/api/v1/admin/socios/" + resumen(p).get("socioId").asLong() + "/autorizacion-retiro")
                .header(HttpHeaders.AUTHORIZATION, bearer(presidente())));
    }

    private JsonNode menu(long socioId) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/admin/socios/" + socioId + "/obligaciones").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions descontar(long socioId, String cuerpo, String clave) throws Exception {
        return mvc.perform(post("/api/v1/admin/socios/" + socioId + "/descuentos").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions retirarGanancia(Persona p, String importe) throws Exception {
        return conClave(p.token(), "/api/v1/socios/me/retiro-ganancias", "{\"importe\":" + importe + "}");
    }

    private ResultActions compraventa(long vendedor, long comprador, String capital, String precio) throws Exception {
        return conClave(presidente(), "/api/v1/admin/socios/compraventas", "{\"vendedorId\":" + vendedor + ",\"compradorId\":" + comprador
                + ",\"capital\":" + capital + ",\"precio\":" + precio + "}");
    }

    /** Fecha de entrada al primer día de hace N meses, para que esos meses cuenten como vencidos. */
    private void entroHaceMeses(long socioId, int meses) {
        jdbc.update("update socios set desde = date_trunc('month', now() AT TIME ZONE 'America/Guayaquil') AT TIME ZONE 'America/Guayaquil' - make_interval(months => ?) + interval '1 day' where id = ?", meses, socioId);
    }

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
                                "identificacion", String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)),
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
