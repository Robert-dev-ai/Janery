package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Créditos que ya existían antes de usar Janery, contra PostgreSQL en el esquema aislado janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class CreditosExistentesIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private record Persona(String token, long cuenta, long usuarioId) {
    }

    @Test
    void registraConLetrasPagadasYSeCobraComoCualquierCredito() throws Exception {
        Persona p = persona();
        depositar(p, "1000.00");
        // 1000 a 10 meses, 2 % de interés y 0,5 % de desgravamen: letras de 100 + 20 + 5, más 1 de ahorro = 126.
        // La cuarta venció hace 5 días y no se pagó.
        LocalDate inicio = LocalDate.now().minusMonths(4).minusDays(5);
        String cuerpo = cuerpo("1000.00", 10, "2", "0.5", true, inicio, 3, "100.00");

        JsonNode vista = json.readTree(mvc.perform(post(ruta(p) + "/vista-previa").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(vista.get("capitalPendiente").decimalValue()).isEqualByComparingTo("700");
        assertThat(vista.get("ahorroAcumulado").decimalValue()).isEqualByComparingTo("3");
        assertThat(vista.get("totalPendiente").decimalValue()).isEqualByComparingTo("882");
        assertThat(vista.get("letrasAtrasadas").asInt()).isEqualTo(1);

        String clave = UUID.randomUUID().toString();
        long id = registrar(p, cuerpo, clave).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .transform(r -> leer(r).get("id").asLong());
        // Reintentar con la misma clave no crea otro.
        assertThat(leer(registrar(p, cuerpo, clave).andReturn().getResponse().getContentAsString()).get("id").asLong()).isEqualTo(id);

        // Nada entra ni sale de la cuenta: ese dinero se movió antes de Janery.
        assertThat(saldo(p)).isEqualByComparingTo("1000");
        JsonNode c = credito(p, id);
        assertThat(c.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(c.get("existente").asBoolean()).isTrue();
        assertThat(c.get("tasaInteres").decimalValue()).isEqualByComparingTo("2");
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("700");
        assertThat(c.get("encaje").decimalValue()).isEqualByComparingTo("100");
        assertThat(c.get("ahorroLetras").decimalValue()).isEqualByComparingTo("3");
        assertThat(c.get("cuotasPagadas").toString()).isEqualTo("[1,2,3]");
        assertThat(c.get("letrasAtrasadas").asInt()).isEqualTo(1);
        BigDecimal mora = c.get("moraPendiente").decimalValue();

        // En el balance solo entra lo pendiente; lo cobrado antes no vuelve a ser ganancia.
        assertThat(jdbc.queryForObject("SELECT SUM(importe) FROM desembolsos_credito WHERE credito_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("700");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM detalles_pago d JOIN pagos_credito g ON g.id = d.pago_id
                WHERE g.credito_id = ? AND d.concepto <> 'AHORRO_LETRAS'""", Long.class, id)).isZero();

        for (int i = 0; i < 7; i++) {
            pagar(p, id).andExpect(status().isOk());
        }
        c = credito(p, id);
        assertThat(c.get("estado").asText()).isEqualTo("FINALIZADO");
        // Pagó 7 letras de 126 más la mora; recibe el encaje y las 10 letras de ahorro.
        assertThat(saldo(p)).isEqualByComparingTo(new BigDecimal("1000").subtract(new BigDecimal("882")).subtract(mora)
                .add(new BigDecimal("110")));
    }

    @Test
    void sinAhorroLetrasLaLetraLlevaCentavos() throws Exception {
        Persona p = persona();
        String cuerpo = cuerpo("1000.00", 3, "1.5", "0", false, LocalDate.now(), 0, "0");
        mvc.perform(post(ruta(p) + "/vista-previa").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotas[0].total").value(348.33))
                .andExpect(jsonPath("$.cuotas[2].total").value(348.34))
                .andExpect(jsonPath("$.totalPendiente").value(1045.00))
                .andExpect(jsonPath("$.letrasAtrasadas").value(0));
        long id = leer(registrar(p, cuerpo, UUID.randomUUID().toString()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(credito(p, id).get("ahorroLetras").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void rechazaDatosImposiblesYAQuienNoTienePermiso() throws Exception {
        Persona p = persona();
        registrar(p, cuerpo("500.00", 5, "2", "0", true, LocalDate.now().minusMonths(6), 5, "0"), UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CREDITO_TERMINADO"));
        registrar(p, cuerpo("500.00", 5, "2", "0", true, LocalDate.now().plusDays(1), 0, "0"), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FECHA_FUTURA"));
        registrar(p, cuerpo("500.00", 5, "2", "0", true, LocalDate.now(), 0, "600.00"), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ENCAJE_INVALIDO"));
        mvc.perform(post(ruta(p)).header(HttpHeaders.AUTHORIZATION, bearer(p.token())).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("500.00", 5, "2", "0", true, LocalDate.now(), 0, "0")))
                .andExpect(status().isForbidden());
    }

    @Test
    void conLoQueDebeHoyLaDiferenciaQuedaComoCapitalAbonado() throws Exception {
        Persona p = persona();
        depositar(p, "1000.00");
        // 1000 a 10 meses: letras de 126 (100 de capital). Con 2 pagadas faltan 800 de capital, pero debe 650:
        // abonó 150, que cubren el capital de la letra 3 y la mitad del de la 4.
        LocalDate inicio = LocalDate.now().minusMonths(3).plusDays(5);
        String cuerpo = conDeuda(cuerpo("1000.00", 10, "2", "0.5", true, inicio, 2, "0"), "650.00");

        mvc.perform(post(ruta(p) + "/vista-previa").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalPendiente").value(650.00))
                .andExpect(jsonPath("$.abonoCapital").value(150.00))
                .andExpect(jsonPath("$.totalPendiente").value(858.00))
                .andExpect(jsonPath("$.letrasAtrasadas").value(0));

        // Debe más de lo que dicen sus letras: los datos no cuadran.
        registrar(p, conDeuda(cuerpo("1000.00", 10, "2", "0.5", true, inicio, 2, "0"), "900.00"), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DEUDA_MAYOR_QUE_LETRAS"));

        long id = leer(registrar(p, cuerpo, UUID.randomUUID().toString()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(credito(p, id).get("capitalPendiente").decimalValue()).isEqualByComparingTo("650");
        assertThat(jdbc.queryForObject("SELECT SUM(importe) FROM desembolsos_credito WHERE credito_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("650");

        // La letra 3 ya tiene su capital: solo le falta interés, desgravamen y ahorro (26).
        pagar(p, id).andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("974");
        assertThat(credito(p, id).get("capitalPendiente").decimalValue()).isEqualByComparingTo("650");
    }

    @Test
    void tesoreriaTambienRegistraCreditosAntiguos() throws Exception {
        Persona p = persona();
        Persona tesorero = persona();
        mvc.perform(post("/api/v1/admin/usuarios/" + tesorero.usuarioId() + "/cargos/TESORERIA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        String correo = jdbc.queryForObject("SELECT correo FROM usuarios WHERE id = ?", String.class, tesorero.usuarioId());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post(ruta(p)).header(HttpHeaders.AUTHORIZATION, bearer(token)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("500.00", 5, "2", "0", true, LocalDate.now(), 0, "0")))
                .andExpect(status().isCreated());
    }

    // --- Apoyo

    private String conDeuda(String cuerpo, String deuda) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode nodo = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(cuerpo);
        nodo.put("capitalDebe", new BigDecimal(deuda));
        return json.writeValueAsString(nodo);
    }

    private String cuerpo(String importe, int plazo, String interes, String desgravamen, boolean ahorro, LocalDate inicio,
            int pagadas, String encaje) throws Exception {
        return json.writeValueAsString(Map.of("modalidad", "PERSONALIZADO", "importe", new BigDecimal(importe), "plazoMeses", plazo,
                "tasaInteres", new BigDecimal(interes), "tasaDesgravamen", new BigDecimal(desgravamen), "conAhorroLetras", ahorro,
                "fechaInicio", inicio.toString(), "letrasPagadas", pagadas, "encaje", new BigDecimal(encaje), "nota", "Libreta de prueba"));
    }

    private static String ruta(Persona p) {
        return "/api/v1/admin/creditos/existentes/personas/" + p.usuarioId();
    }

    private ResultActions registrar(Persona p, String cuerpo, String clave) throws Exception {
        return mvc.perform(post(ruta(p)).header(HttpHeaders.AUTHORIZATION, bearer(presidente())).header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions pagar(Persona p, long credito) throws Exception {
        return mvc.perform(post("/api/v1/creditos/" + credito + "/pagos").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private JsonNode credito(Persona p, long id) throws Exception {
        return leer(mvc.perform(get("/api/v1/creditos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void depositar(Persona p, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/depositos").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return leer(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("saldoDisponible").decimalValue();
    }

    private JsonNode leer(String texto) {
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String correo, String contrasena) throws Exception {
        String r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return leer(r).get("accessToken").asText();
    }

    private String presidente() throws Exception {
        return login("presidente@janery.test", "Clave-Presidente-1");
    }

    /** Persona con perfil y cuenta activa: no hacen falta los datos para crédito, porque no está pidiendo uno. */
    private Persona persona() throws Exception {
        String correo = "e-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombres", "Eva", "apellidos", "Existente",
                                "identificacion", String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        String r = mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cuenta = leer(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        long usuario = jdbc.queryForObject("SELECT id FROM usuarios WHERE correo = ?", Long.class, correo);
        return new Persona(token, cuenta, usuario);
    }
}
