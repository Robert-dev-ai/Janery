package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Emergentes, cupos de socio, ahorros y aportes que ya existían antes de usar Janery, contra PostgreSQL en el esquema
 * aislado janery_test. Entran los saldos de hoy sin mover dinero; desde ahí funcionan como cualquier otro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class DatosAnterioresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private record Persona(String token, long cuenta, long usuarioId) {
    }

    // --- Emergentes

    @Test
    void emergenteDeClienteSeRegistraYSeCobraComoCualquierOtro() throws Exception {
        Persona p = persona();
        depositar(p, "500.00");
        // 200 al 3,5 % y 0,25 %: un pago de 200 + 7 + 0,50, un mes después de recibir el dinero.
        Map<String, Object> cuerpo = Map.of("importe", 200, "fechaInicio", LocalDate.now().minusDays(10).toString(),
                "tasaInteres", 3.5, "tasaDesgravamen", 0.25, "nota", "Cuaderno");
        String clave = UUID.randomUUID().toString();
        long id = leer(registrar(p, "emergente-cliente", cuerpo, clave).andExpect(status().isCreated())).get("id").asLong();
        assertThat(leer(registrar(p, "emergente-cliente", cuerpo, clave)).get("id").asLong()).isEqualTo(id);

        // No entra dinero a su cuenta, y no puede tener dos emergentes abiertos.
        assertThat(saldo(p)).isEqualByComparingTo("500");
        registrar(p, "emergente-cliente", cuerpo, UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EMERGENTE_ABIERTO"));

        JsonNode c = credito(p, id);
        assertThat(c.get("familia").asText()).isEqualTo("EMERGENTE_CLIENTE");
        assertThat(c.get("existente").asBoolean()).isTrue();
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("SELECT SUM(importe) FROM desembolsos_credito WHERE credito_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("200");

        pagar(p, id).andExpect(status().isOk());
        assertThat(credito(p, id).get("estado").asText()).isEqualTo("FINALIZADO");
        assertThat(saldo(p)).isEqualByComparingTo("292.50");
    }

    @Test
    void emergenteConAbonoYCargosPagadosSoloCobraElCapitalQueDebe() throws Exception {
        Persona p = persona();
        depositar(p, "500.00");
        long id = leer(registrar(p, "emergente-cliente", Map.of("importe", 300, "fechaInicio", LocalDate.now().minusDays(3).toString(),
                "tasaInteres", 3.5, "tasaDesgravamen", 0.25, "capitalDebe", 100, "cargosPagados", true), UUID.randomUUID().toString())
                .andExpect(status().isCreated())).get("id").asLong();
        assertThat(credito(p, id).get("capitalPendiente").decimalValue()).isEqualByComparingTo("100");
        pagar(p, id).andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("400");

        registrar(p, "emergente-cliente", Map.of("importe", 300, "fechaInicio", LocalDate.now().toString(), "tasaInteres", 3.5,
                "tasaDesgravamen", 0.25, "capitalDebe", 400), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DEUDA_MAYOR_QUE_IMPORTE"));
    }

    @Test
    void cupoDeSocioSoloParaSociosYOcupaSuCupo() throws Exception {
        Persona p = persona();
        Map<String, Object> cupo = Map.of("capitalDebe", 150, "interesPendiente", 2.5);
        registrar(p, "cupo-socio", cupo, UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOLO_SOCIOS"));

        registrar(p, "aportes", Map.of("socioDesde", LocalDate.now().toString()), UUID.randomUUID().toString())
                .andExpect(status().isCreated());
        String clave = UUID.randomUUID().toString();
        long id = leer(registrar(p, "cupo-socio", cupo, clave).andExpect(status().isCreated())).get("id").asLong();
        assertThat(leer(registrar(p, "cupo-socio", cupo, clave)).get("id").asLong()).isEqualTo(id);

        Map<String, Object> fila = jdbc.queryForMap("SELECT familia, capital_pendiente, interes_acumulado, existente FROM creditos WHERE id = ?", id);
        assertThat(fila.get("familia")).isEqualTo("EMERGENTE_SOCIO");
        assertThat((BigDecimal) fila.get("capital_pendiente")).isEqualByComparingTo("150");
        assertThat((BigDecimal) fila.get("interes_acumulado")).isEqualByComparingTo("2.5");
        assertThat(fila.get("existente")).isEqualTo(true);
        assertThat(saldo(p)).isEqualByComparingTo("0");

        // Un segundo registro se suma al mismo cupo.
        long otro = leer(registrar(p, "cupo-socio", Map.of("capitalDebe", 50), UUID.randomUUID().toString())
                .andExpect(status().isCreated())).get("id").asLong();
        assertThat(otro).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT capital_pendiente FROM creditos WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("200");
    }

    // --- Ahorros

    @Test
    void ahorroNormalYPlazoFijoEntranConSuSaldoSinMoverDinero() throws Exception {
        Persona p = persona();
        String clave = UUID.randomUUID().toString();
        Map<String, Object> normal = Map.of("tipo", "NORMAL", "nombre", "Ahorro de la libreta", "saldo", 120);
        JsonNode a = leer(registrar(p, "ahorros", normal, clave).andExpect(status().isCreated()));
        assertThat(leer(registrar(p, "ahorros", normal, clave)).get("id").asLong()).isEqualTo(a.get("id").asLong());
        assertThat(a.get("saldo").decimalValue()).isEqualByComparingTo("120");
        assertThat(a.get("retirable").asBoolean()).isTrue();

        LocalDate apertura = LocalDate.now().minusMonths(2);
        JsonNode plazo = leer(registrar(p, "ahorros", Map.of("tipo", "PLAZO_FIJO", "nombre", "Plazo de enero", "saldo", 1000,
                "fechaApertura", apertura.toString(), "plazoMeses", 6, "tasaAnual", 6), UUID.randomUUID().toString())
                .andExpect(status().isCreated()));
        assertThat(plazo.get("venceEn").asText()).isEqualTo(apertura.plusMonths(6).toString());
        // 1000 al 6 % anual por 6 meses.
        assertThat(plazo.get("interesAlVencimiento").decimalValue()).isEqualByComparingTo("30");
        assertThat(plazo.get("retirable").asBoolean()).isFalse();

        JsonNode mios = leer(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()));
        assertThat(mios.size()).isEqualTo(2);
        assertThat(saldo(p)).isEqualByComparingTo("0");
        // En el balance: 120 en ahorros captados (2.1) y 1000 en plazo fijo (2.2), sin pasar por la caja.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM movimientos_ahorro m JOIN cuentas_ahorro a ON a.id = m.cuenta_ahorro_id
                WHERE a.persona_id = (SELECT id FROM personas WHERE usuario_id = ?) AND m.concepto = 'SALDO_ANTERIOR'""",
                Long.class, p.usuarioId())).isEqualTo(2);

        registrar(p, "ahorros", Map.of("tipo", "PLAZO_FIJO", "nombre", "Sin tasa", "saldo", 100), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_PLAZO_FIJO"));
    }

    // --- Aportes

    @Test
    void aportesDeUnSocioAnteriorDejanSusMesesAlDia() throws Exception {
        Persona p = persona();
        YearMonth actual = YearMonth.now();
        LocalDate desde = actual.minusMonths(4).atDay(1);
        Map<String, Object> cuerpo = new HashMap<>(Map.of("socioDesde", desde.toString(), "aporteInicial", 50,
                "mensualesHasta", actual.minusMonths(1).toString(), "personales", 20, "cajaChicaHasta", actual.minusMonths(1).toString()));
        String clave = UUID.randomUUID().toString();
        JsonNode r = leer(registrar(p, "aportes", cuerpo, clave).andExpect(status().isCreated()));
        assertThat(r.get("mesesAporte").asInt()).isEqualTo(4);
        assertThat(r.get("mesesCajaChica").asInt()).isEqualTo(4);
        BigDecimal mensual = r.get("totalMensual").decimalValue();

        // Reintentar con la misma clave no duplica nada.
        assertThat(leer(registrar(p, "aportes", cuerpo, clave)).get("mesesAporte").asInt()).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM aportes_socios a JOIN socios s ON s.id = a.socio_id
                JOIN personas p ON p.id = s.persona_id WHERE p.usuario_id = ?""", Long.class, p.usuarioId())).isEqualTo(6);

        // Es socio desde su fecha real, con su fondo y sin meses pendientes.
        JsonNode socio = leer(mvc.perform(get("/api/v1/socios/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()));
        assertThat(socio.get("totalInicial").decimalValue()).isEqualByComparingTo("50");
        assertThat(socio.get("totalObligatorio").decimalValue()).isEqualByComparingTo(mensual);
        assertThat(socio.get("totalPersonal").decimalValue()).isEqualByComparingTo("20");
        assertThat(socio.get("obligacionesPendientes").decimalValue()).isEqualByComparingTo("0");
        assertThat(saldo(p)).isEqualByComparingTo("0");

        // El aporte inicial no se registra dos veces; los meses ya pagados se saltan.
        registrar(p, "aportes", Map.of("aporteInicial", 50), UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("APORTE_INICIAL_REGISTRADO"));
        assertThat(leer(registrar(p, "aportes", Map.of("mensualesHasta", actual.toString()), UUID.randomUUID().toString())
                .andExpect(status().isCreated())).get("mesesAporte").asInt()).isEqualTo(1);
    }

    @Test
    void rechazaDatosImposiblesYAQuienNoTienePermiso() throws Exception {
        Persona p = persona();
        registrar(p, "aportes", Map.of("aporteInicial", 50), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FECHA_INGRESO_REQUERIDA"));
        registrar(p, "aportes", Map.of("socioDesde", LocalDate.now().toString(), "mensualesHasta", YearMonth.now().plusMonths(1).toString()),
                UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PERIODO_FUTURO"));
        registrar(p, "emergente-cliente", Map.of("importe", 100, "fechaInicio", LocalDate.now().plusDays(1).toString(),
                "tasaInteres", 3.5, "tasaDesgravamen", 0.25), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FECHA_FUTURA"));
        mvc.perform(post(ruta(p, "ahorros")).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", "NORMAL", "nombre", "Mío", "saldo", 1000))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/anteriores/valores").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.APORTE_OBLIGATORIO").exists());
    }

    // --- Apoyo

    private static String ruta(Persona p, String que) {
        return "/api/v1/admin/anteriores/personas/" + p.usuarioId() + "/" + que;
    }

    private ResultActions registrar(Persona p, String que, Map<String, Object> cuerpo, String clave) throws Exception {
        return mvc.perform(post(ruta(p, que)).header(HttpHeaders.AUTHORIZATION, bearer(presidente())).header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions pagar(Persona p, long credito) throws Exception {
        return mvc.perform(post("/api/v1/creditos/" + credito + "/pagos").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private JsonNode credito(Persona p, long id) throws Exception {
        return leer(mvc.perform(get("/api/v1/creditos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()));
    }

    private void depositar(Persona p, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/depositos").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return leer(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))))
                .get("saldoDisponible").decimalValue();
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
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

    /** Persona con perfil y cuenta activa, como al crearla desde Usuarios. */
    private Persona persona() throws Exception {
        String correo = "ant-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombres", "Ana", "apellidos", "Anterior",
                                "identificacion", String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        String r = mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cuenta = json.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        long usuario = jdbc.queryForObject("SELECT id FROM usuarios WHERE correo = ?", Long.class, correo);
        return new Persona(token, cuenta, usuario);
    }
}
