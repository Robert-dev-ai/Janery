package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
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
 * Interés de la cuenta principal, con el reloj simulado: corre cada día como el del ahorro normal, con su propia tasa.
 * En un esquema propio porque el reloj solo avanza. Con 3650 USD al 5 % anual, cada día da 0,50.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://${JANERY_DB_HOST:localhost}:${JANERY_DB_PORT:5432}/${JANERY_DB_NAME:janery}?currentSchema=janery_simulacion_interes_test&sslmode=${JANERY_DB_SSLMODE:prefer}",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO janery_simulacion_interes_test",
        "spring.jpa.properties.hibernate.default_schema=janery_simulacion_interes_test",
        "spring.flyway.schemas=janery_simulacion_interes_test",
        "spring.flyway.default-schema=janery_simulacion_interes_test" })
@AutoConfigureMockMvc
@ActiveProfiles({ "simulacion", "test" })
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class InteresCuentaPrincipalIntegrationTest {

    private record Persona(String token, long cuenta, long usuario) {
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    @Test
    void correCadaDiaYSeLiberaElDiaUnoParaRetirarloComoGasto() throws Exception {
        LocalDate inicio = alProximoDiaUno();
        Persona p = persona("3650.00");

        avanzar(inicio.plusDays(10));
        JsonNode i = interes(p);
        assertThat(i.get("acumulado").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(i.get("disponible").decimalValue()).isEqualByComparingTo("0");
        assertThat(i.get("tasaAnual").decimalValue()).isEqualByComparingTo("5");
        assertThat(i.get("proximaLiberacion").asText()).isEqualTo(inicio.plusMonths(1).toString());
        // Lo acumulado aún no se puede retirar.
        retirarInteres(p, UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("INTERES_NO_DISPONIBLE"));

        // El día 1 todo lo del mes pasa a disponible.
        LocalDate siguiente = inicio.plusMonths(1);
        avanzar(siguiente);
        BigDecimal delMes = new BigDecimal("0.50").multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(inicio, siguiente)));
        i = interes(p);
        assertThat(i.get("acumulado").decimalValue()).isEqualByComparingTo("0");
        assertThat(i.get("disponible").decimalValue()).isEqualByComparingTo(delMes);
        assertThat(saldo(p)).isEqualByComparingTo("3650");

        String clave = UUID.randomUUID().toString();
        long operacion = json.readTree(retirarInteres(p, clave).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("operacionId").asLong();
        // Reintentar con la misma clave no paga dos veces.
        assertThat(json.readTree(retirarInteres(p, clave).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("operacionId").asLong()).isEqualTo(operacion);
        assertThat(saldo(p)).isEqualByComparingTo(new BigDecimal("3650").add(delMes));
        assertThat(interes(p).get("disponible").decimalValue()).isEqualByComparingTo("0");
        // Es un gasto de Janery (5.1), y la deuda con la persona sube lo mismo (2.1).
        assertThat(libro(operacion, "5.1")).isEqualByComparingTo(delMes);
        assertThat(libro(operacion, "2.1")).isEqualByComparingTo(delMes);
        assertThat(jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'CUENTA_INTERES' and entidad_id = ?",
                Long.class, String.valueOf(p.cuenta()))).isEqualTo(1L);
        retirarInteres(p, UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("INTERES_NO_DISPONIBLE"));
    }

    @Test
    void seDevengaSobreElSaldoAnteriorAntesDeUnDepositoYDeUnPago() throws Exception {
        LocalDate inicio = alProximoDiaUno();
        Persona p = persona("3650.00");

        // Cuatro días con 3650 (0,50 al día) y luego entra otro tanto: lo corrido queda guardado con el saldo anterior.
        avanzar(inicio.plusDays(4));
        depositar(p, "3650.00");
        assertThat(guardado(p, "interes_acumulado")).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select ultimo_devengo from cuentas_principales where id = ?", LocalDate.class, p.cuenta()))
                .isEqualTo(inicio.plusDays(4));

        // Dos días con 7300 (1,00 al día) y luego paga un depósito a su ahorro: se devenga antes de que baje el saldo.
        avanzar(inicio.plusDays(6));
        long ahorro = json.readTree(conClave(p.token(), "/api/v1/ahorros/normales", "{\"nombre\":\"Casa\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        conClave(p.token(), "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":3650.00}").andExpect(status().isOk());
        assertThat(guardado(p, "interes_acumulado")).isEqualByComparingTo("4");
        assertThat(saldo(p)).isEqualByComparingTo("3650");

        // Dos días más con 3650 de nuevo.
        avanzar(inicio.plusDays(8));
        assertThat(interes(p).get("acumulado").decimalValue()).isEqualByComparingTo("5.00");
    }

    @Test
    void alCerrarSeLiquidaElInteresPendienteConElResto() throws Exception {
        LocalDate inicio = alProximoDiaUno();
        Persona p = persona("3650.00");
        avanzar(inicio.plusDays(10));

        // Retira todo su saldo; el interés (5,00 acumulados) no impide pedir el cierre.
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/retiros").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":3650.00,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("0");
        mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().is2xxSuccessful());

        BigDecimal caja = saldoCaja();
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/confirmar-cierre").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject("select estado from cuentas_principales where id = ?", String.class, p.cuenta())).isEqualTo("CERRADA");
        assertThat(guardado(p, "interes_acumulado")).isEqualByComparingTo("0");
        assertThat(guardado(p, "interes_disponible")).isEqualByComparingTo("0");
        // Entró como interés y salió con el depósito reembolsable; la cuenta queda en cero.
        assertThat(movimiento(p, "INTERES_PRINCIPAL", "ENTRADA")).isEqualByComparingTo("5");
        assertThat(movimiento(p, "LIQUIDACION_CIERRE", "SALIDA")).isEqualByComparingTo("5");
        assertThat(saldo(p.cuenta())).isEqualByComparingTo("0");
        assertThat(caja.subtract(saldoCaja())).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("""
                select coalesce(sum(l.importe), 0) from libro_contable l join movimientos_cuenta m on m.operacion_id = l.operacion_id
                where m.cuenta_id = ? and l.linea = '5.1'
                """, BigDecimal.class, p.cuenta())).isEqualByComparingTo("5");
    }

    @Test
    void laTasaDeLaCuentaPrincipalYLaDelAhorroNormalSonIndependientes() throws Exception {
        LocalDate inicio = alProximoDiaUno();
        Persona p = persona("7300.00");
        long ahorro = json.readTree(conClave(p.token(), "/api/v1/ahorros/normales", "{\"nombre\":\"Casa\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        conClave(p.token(), "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":3650.00}").andExpect(status().isOk());
        try {
            // 3650 en cada una. La principal al 10 %: 1,00 al día; el ahorro sigue al 5 %: 0,50.
            cambiarParametro("PRINCIPAL_TASA", "10");
            avanzar(inicio.plusDays(10));
            assertThat(interes(p).get("acumulado").decimalValue()).isEqualByComparingTo("10.00");
            assertThat(interes(p).get("tasaAnual").decimalValue()).isEqualByComparingTo("10");
            assertThat(ahorro(p, ahorro).get("interesAcumulado").decimalValue()).isEqualByComparingTo("5.00");

            // Y al revés: el ahorro al 7,3 % (0,73 al día) no cambia la principal.
            cambiarParametro("AHORRO_NORMAL_TASA", "7.3");
            assertThat(ahorro(p, ahorro).get("interesAcumulado").decimalValue()).isEqualByComparingTo("7.30");
            assertThat(interes(p).get("acumulado").decimalValue()).isEqualByComparingTo("10.00");
        } finally {
            cambiarParametro("PRINCIPAL_TASA", "5");
            cambiarParametro("AHORRO_NORMAL_TASA", "5");
        }
    }

    // --- Apoyo

    /** Lleva el reloj al próximo día 1, para que los días de cada prueba no crucen un día 1 sin querer. */
    private LocalDate alProximoDiaUno() throws Exception {
        LocalDate destino = YearMonth.now(reloj).plusMonths(1).atDay(1);
        avanzar(destino);
        return destino;
    }

    private void avanzar(LocalDate fecha) throws Exception {
        mvc.perform(post("/api/v1/simulacion/avanzar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"" + fecha + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fecha").value(fecha.toString()));
    }

    private JsonNode interes(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me/interes").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions retirarInteres(Persona p, String clave) throws Exception {
        return mvc.perform(post("/api/v1/cuentas-principales/me/interes").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", clave));
    }

    private JsonNode ahorro(Persona p, long id) throws Exception {
        for (JsonNode a : json.readTree(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString())) {
            if (a.get("id").asLong() == id) {
                return a;
            }
        }
        throw new AssertionError("Ahorro no encontrado");
    }

    private BigDecimal guardado(Persona p, String columna) {
        return jdbc.queryForObject("select " + columna + " from cuentas_principales where id = ?", BigDecimal.class, p.cuenta());
    }

    private BigDecimal libro(long operacion, String linea) {
        return jdbc.queryForObject("select coalesce(sum(importe), 0) from libro_contable where operacion_id = ? and linea = ?",
                BigDecimal.class, operacion, linea);
    }

    private BigDecimal movimiento(Persona p, String concepto, String sentido) {
        return jdbc.queryForObject("select coalesce(sum(importe), 0) from movimientos_cuenta where cuenta_id = ? and concepto = ? and sentido = ?",
                BigDecimal.class, p.cuenta(), concepto, sentido);
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("saldoDisponible").decimalValue();
    }

    private BigDecimal saldo(long cuenta) {
        return jdbc.queryForObject("select saldo_disponible from cuentas_principales where id = ?", BigDecimal.class, cuenta);
    }

    private BigDecimal saldoCaja() {
        return jdbc.queryForObject("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from movimientos_caja m join cajas c on c.id = m.caja_id where c.codigo = 'PRINCIPAL'
                """, BigDecimal.class);
    }

    private void depositar(Persona p, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/depositos").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private void cambiarParametro(String codigo, String valor) throws Exception {
        Long id = jdbc.queryForObject("select id from parametros_sistema where codigo = ?", Long.class, codigo);
        // La vigencia debe ser posterior a la anterior, y dos cambios seguidos pueden caer en el mismo instante.
        Thread.sleep(2);
        mvc.perform(put("/api/v1/admin/parametros/" + id).header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":" + valor + ",\"motivo\":\"Prueba\"}"))
                .andExpect(status().isNoContent());
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

    private Persona persona(String saldoInicial) throws Exception {
        String correo = "int-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
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
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        Persona p = new Persona(token, cuenta, jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, correo));
        if (new BigDecimal(saldoInicial).signum() > 0) {
            depositar(p, saldoInicial);
        }
        return p;
    }
}
