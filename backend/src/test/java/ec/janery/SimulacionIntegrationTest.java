package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

import ec.janery.simulacion.RelojDePrueba;

/** El reloj simulado sobre los flujos reales, en un esquema propio para no alterar el de las demás pruebas. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://${JANERY_DB_HOST:localhost}:${JANERY_DB_PORT:5432}/${JANERY_DB_NAME:janery}?currentSchema=janery_simulacion_test&sslmode=${JANERY_DB_SSLMODE:prefer}",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO janery_simulacion_test",
        "spring.jpa.properties.hibernate.default_schema=janery_simulacion_test",
        "spring.flyway.schemas=janery_simulacion_test",
        "spring.flyway.default-schema=janery_simulacion_test" })
@AutoConfigureMockMvc
@ActiveProfiles({ "simulacion", "test" })
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class SimulacionIntegrationTest {

    private record Persona(String token, long cuenta, String correo) {
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE variaciones_participacion, aportes_socios, aportes_caja_chica, resultados, cierres_anuales, compraventas_capital, autorizaciones_retiro");
        jdbc.update("update socios set estado = 'INACTIVO'");
    }

    @Test
    void quinceDiasAumentanElInteresDelCupoDeSocio() throws Exception {
        Persona p = socio("0");
        long solicitud = json.readTree(conClave(p.token(), "/api/v1/creditos/emergentes/disposiciones", "{\"importe\":100.00}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/disposiciones/" + solicitud + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        JsonNode lista = json.readTree(mvc.perform(get("/api/v1/creditos/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString());
        String ruta = "/api/v1/creditos/" + lista.get(0).get("id").asLong();

        avanzar("{\"dias\":15}").andExpect(status().isOk());

        // Quince días sobre USD 100: 0,12 % y 0,08 % diarios.
        JsonNode c = json.readTree(mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(c.get("interes").decimalValue()).isEqualByComparingTo("1.80");
        assertThat(c.get("desgravamen").decimalValue()).isEqualByComparingTo("1.20");
    }

    @Test
    void laGananciaSeLiberaElUnoDeEneroYNoElTreintaYUnoDeDiciembre() throws Exception {
        Persona a = socio("100.00");
        conClave(a.token(), "/api/v1/socios/me/aportes", "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        resultadoGanancia("100.00");

        LocalDate hoy = LocalDate.now(reloj);
        LocalDate fin = LocalDate.of(hoy.getYear(), 12, 31);
        if (hoy.isBefore(fin)) {
            avanzar("{\"fecha\":\"" + fin + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.fecha").value(fin.toString()));
        }
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("0");

        avanzar("{\"fecha\":\"" + fin.plusDays(1) + "\"}").andExpect(status().isOk());
        assertThat(resumen(a).get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("90");
    }

    @Test
    void cruzarUnDiaUnoLiberaElInteresDelAhorroSinTocarSuSaldo() throws Exception {
        Persona p = persona("1200.00");
        String r = mvc.perform(post("/api/v1/ahorros/normales").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Vacaciones\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ahorro = json.readTree(r).get("id").asLong();
        conClave(p.token(), "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":1200.00}").andExpect(status().isOk());

        LocalDate destino = YearMonth.now(reloj).plusMonths(2).atDay(1);
        avanzar("{\"fecha\":\"" + destino + "\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select saldo from cuentas_ahorro where id = ?", BigDecimal.class, ahorro)).isEqualByComparingTo("1200");
        JsonNode a = json.readTree(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get(0);
        // Hoy es día 1: todo lo corrido ya está liberado. 1200 * 5 % / 365 por cada día.
        long dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(a.get("proximaLiberacion").asText()).minusMonths(3), destino);
        assertThat(a.get("interesAcumulado").decimalValue()).isEqualByComparingTo("0");
        assertThat(a.get("interesDisponible").decimalValue().signum()).isPositive();
        assertThat(dias).isPositive();
    }

    @Test
    void cruzarUnDiaUnoConservaLaHoraYCuentaLosDiasPorFecha() throws Exception {
        java.time.LocalDateTime antes = java.time.LocalDateTime.now(reloj);
        long diasAntes = json.readTree(mvc.perform(get("/api/v1/simulacion").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andReturn().getResponse().getContentAsString()).get("diasAdelantados").asLong();
        LocalDate destino = YearMonth.from(antes).plusMonths(1).atDay(15);

        String r = avanzar("{\"fecha\":\"" + destino + "\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(r).get("diasAdelantados").asLong())
                .isEqualTo(diasAntes + java.time.temporal.ChronoUnit.DAYS.between(antes.toLocalDate(), destino));
        assertThat(java.time.Duration.between(destino.atTime(antes.toLocalTime()), java.time.LocalDateTime.now(reloj)).abs().toMinutes()).isZero();
    }

    @Test
    void laFechaSoloAvanzaYSeConservaAlReiniciar() throws Exception {
        LocalDate hoy = LocalDate.now(reloj);
        avanzar("{\"fecha\":\"" + hoy + "\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FECHA_NO_FUTURA"));
        avanzar("{\"fecha\":\"" + hoy.minusDays(1) + "\"}").andExpect(status().isConflict());
        avanzar("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DESTINO_INVALIDO"));
        avanzar("{\"dias\":1,\"fecha\":\"" + hoy.plusDays(1) + "\"}").andExpect(status().isBadRequest());
        avanzar("{\"dias\":0}").andExpect(status().isBadRequest());
        avanzar("{\"dias\":3661}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALTO_EXCESIVO"));
        avanzar("{\"dias\":2147483647}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALTO_EXCESIVO"));

        avanzar("{\"dias\":2}").andExpect(status().isOk()).andExpect(jsonPath("$.fecha").value(hoy.plusDays(2).toString()));

        // Otro reloj leyendo la misma tabla equivale a reiniciar el backend.
        Clock reiniciado = RelojDePrueba.nuevo(Clock.fixed(Instant.now(), ZoneOffset.UTC), jdbc);
        assertThat(java.time.Duration.between(Instant.now(), reiniciado.instant()).toDays()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void soloQuienAdministraParametrosAvanzaYQuedaAuditado() throws Exception {
        Persona p = persona("0");
        mvc.perform(get("/api/v1/simulacion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/simulacion").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/simulacion/avanzar").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":1}"))
                .andExpect(status().isForbidden());

        Integer antes = jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'RELOJ_AVANZADO'", Integer.class);
        avanzar("{\"dias\":1}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'RELOJ_AVANZADO'", Integer.class)).isEqualTo(antes + 1);
    }

    @Test
    void elPerfilRechazaUnaBaseQueNoSeaDeSimulacion() {
        assertThatThrownBy(() -> RelojDePrueba.validarBase("jdbc:postgresql://localhost:5432/janery?currentSchema=janery"))
                .isInstanceOf(IllegalStateException.class);
        RelojDePrueba.validarBase("jdbc:postgresql://localhost:5432/janery_simulacion?currentSchema=janery");
    }

    // --- Ayudas

    private ResultActions avanzar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/simulacion/avanzar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void resultadoGanancia(String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/resultados").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"GANANCIA\",\"importe\":" + importe + ",\"concepto\":\"Prueba\"}"))
                .andExpect(status().isNoContent());
    }

    private JsonNode resumen(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/socios/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions conClave(String token, String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
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

    private Persona persona(String saldoInicial) throws Exception {
        String correo = "sim-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
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
