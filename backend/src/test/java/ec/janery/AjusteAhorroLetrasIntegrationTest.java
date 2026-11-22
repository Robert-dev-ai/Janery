package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** TEMPORAL: se borra junto con AjusteAhorroLetrasController. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class AjusteAhorroLetrasIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void corrigeElAhorroCargadoSinTocarLoPagadoEnJanery() throws Exception {
        String presidente = login("presidente@janery.test", "Clave-Presidente-1");
        long[] p = persona(presidente);
        depositar(presidente, p[1], "500.00");
        // 1000 a 10 meses con ahorro: 3 letras pagadas dejan 3 USD de ahorro, que en realidad no tiene.
        String cuerpo = json.writeValueAsString(Map.of("modalidad", "PERSONALIZADO", "importe", new BigDecimal("1000.00"),
                "plazoMeses", 10, "tasaInteres", new BigDecimal("2"), "tasaDesgravamen", new BigDecimal("0.5"),
                "conAhorroLetras", true, "fechaInicio", LocalDate.now().minusMonths(3).plusDays(5).toString(), "letrasPagadas", 3,
                "encaje", BigDecimal.ZERO, "nota", "Prueba"));
        long id = leer(mvc.perform(post("/api/v1/admin/creditos/existentes/personas/" + p[0]).header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/v1/admin/creditos/existentes/personas/" + p[0] + "/ahorro-letras").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ahorroActual").value(3.00))
                .andExpect(jsonPath("$[0].ahorroInicial").value(3.00));

        corregir(presidente, id, "1").andExpect(status().isOk()).andExpect(jsonPath("$.ahorroActual").value(1.00));
        assertThat(ahorroEnLibro(id)).isEqualByComparingTo("1");

        corregir(presidente, id, "0").andExpect(status().isOk()).andExpect(jsonPath("$.ahorroInicial").value(0));
        assertThat(ahorroEnLibro(id)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagos_credito WHERE credito_id = ?", Long.class, id)).isZero();

        corregir(presidente, id, "2").andExpect(status().isOk()).andExpect(jsonPath("$.ahorroActual").value(2.00));
        assertThat(ahorroEnLibro(id)).isEqualByComparingTo("2");

        // Paga una letra en Janery: suma 1 de ahorro, que ya no se puede quitar desde aquí.
        mvc.perform(post("/api/v1/creditos/" + id + "/pagos").header(HttpHeaders.AUTHORIZATION, "Bearer " + login(correo(p[0]), "Clave-Segura-1"))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        corregir(presidente, id, "0").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AHORRO_MENOR_QUE_LO_PAGADO_EN_JANERY"));
        corregir(presidente, id, "1").andExpect(status().isOk()).andExpect(jsonPath("$.ahorroActual").value(1.00))
                .andExpect(jsonPath("$.ahorroInicial").value(0));
        assertThat(ahorroEnLibro(id)).isEqualByComparingTo("1");

        // Quien no tiene el permiso no puede.
        corregir(login(correo(p[0]), "Clave-Segura-1"), id, "5").andExpect(status().isForbidden());
    }

    private BigDecimal ahorroEnLibro(long credito) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(d.importe), 0) FROM detalles_pago d JOIN pagos_credito g ON g.id = d.pago_id
                WHERE g.credito_id = ? AND d.concepto = 'AHORRO_LETRAS'""", BigDecimal.class, credito);
    }

    private ResultActions corregir(String token, long credito, String ahorro) throws Exception {
        return mvc.perform(put("/api/v1/admin/creditos/existentes/creditos/" + credito + "/ahorro-letras")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ahorro\":" + ahorro + ",\"motivo\":\"Prueba\"}"));
    }

    private void depositar(String presidente, long cuenta, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/depositos").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private String correo(long usuarioId) {
        return jdbc.queryForObject("SELECT correo FROM usuarios WHERE id = ?", String.class, usuarioId);
    }

    private JsonNode leer(String texto) throws Exception {
        return json.readTree(texto);
    }

    private String login(String correo, String contrasena) throws Exception {
        return leer(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** Usuario y cuenta activa. */
    private long[] persona(String presidente) throws Exception {
        String correo = "a-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombres", "Ana", "apellidos", "Ajuste",
                                "identificacion", String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        long cuenta = leer(mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente))
                .andExpect(status().isOk());
        return new long[] { jdbc.queryForObject("SELECT id FROM usuarios WHERE correo = ?", Long.class, correo), cuenta };
    }
}
