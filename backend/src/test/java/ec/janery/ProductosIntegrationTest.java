package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
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

import ec.janery.ahorro.AhorroService;

/** Créditos y ahorros de principio a fin contra PostgreSQL, en el esquema aislado janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class ProductosIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AhorroService ahorros;

    private record Persona(String token, long cuenta, String correo) {
    }

    // --- Crédito a letras

    @Test
    void letrasCompletoDevuelveEncajeYAhorroAlTerminar() throws Exception {
        Persona p = persona();
        depositar(p, "200.00");
        long credito = solicitarLetras(p, "NORMAL", "1000.00", 3);
        assertThat(saldo(p)).isEqualByComparingTo("200");

        aprobar(credito);
        // Recibe 1000 y paga 10 % de encaje (cliente, normal).
        assertThat(saldo(p)).isEqualByComparingTo("1100");
        JsonNode c = credito(p, credito);
        assertThat(c.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(c.get("encaje").decimalValue()).isEqualByComparingTo("100");

        BigDecimal pagado = BigDecimal.ZERO;
        BigDecimal cargos = BigDecimal.ZERO;
        BigDecimal ahorro = BigDecimal.ZERO;
        for (JsonNode cuota : c.get("cuotas")) {
            pagar(p, credito, "{}").andExpect(status().isOk());
            pagado = pagado.add(cuota.get("total").decimalValue());
            cargos = cargos.add(cuota.get("interes").decimalValue()).add(cuota.get("fondoDesgravamen").decimalValue());
            ahorro = ahorro.add(cuota.get("ahorroLetras").decimalValue());
        }
        JsonNode fin = credito(p, credito);
        assertThat(fin.get("estado").asText()).isEqualTo("FINALIZADO");
        assertThat(fin.get("encajeDevuelto").asBoolean()).isTrue();
        assertThat(fin.get("ahorroLetras").decimalValue()).isEqualByComparingTo("0");
        assertThat(fin.get("capitalPendiente").decimalValue()).isEqualByComparingTo("0");
        // Al final solo cuestan los cargos: encaje y ahorro letras regresaron.
        assertThat(saldo(p)).isEqualByComparingTo(new BigDecimal("200").subtract(cargos));
        assertThat(pagado).isEqualByComparingTo(new BigDecimal("1000").add(cargos).add(ahorro));
    }

    @Test
    void lasTasasDependenDelPerfilYQuedanFijadasAlSolicitar() throws Exception {
        Persona cliente = persona();
        Persona socio = persona();
        hacerSocio(socio);
        long deCliente = solicitarLetras(cliente, "NORMAL", "500.00", 5);
        long deSocio = solicitarLetras(socio, "NORMAL", "500.00", 5);

        assertThat(credito(cliente, deCliente).get("tasaInteres").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(credito(socio, deSocio).get("tasaInteres").decimalValue()).isEqualByComparingTo("1");

        cambiarParametro("LETRAS_INTERES_NORMAL_CLIENTE", "9");
        aprobar(deCliente);
        assertThat(credito(cliente, deCliente).get("tasaInteres").decimalValue()).isEqualByComparingTo("1.5");
        cambiarParametro("LETRAS_INTERES_NORMAL_CLIENTE", "1.5");
    }

    @Test
    void encajeYAhorroSoloCubrenLaUltimaLetraYElRemanenteVaALaCuenta() throws Exception {
        Persona p = persona();
        depositar(p, "50.00");
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);

        pagar(p, credito, "{\"usarEncaje\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOLO_ULTIMA_LETRA"));
        pagar(p, credito, "{}").andExpect(status().isOk());
        pagar(p, credito, "{}").andExpect(status().isOk());

        // Se deja la cuenta con menos que la letra: solo alcanza sumando encaje y ahorro letras, con 5 de sobra.
        JsonNode c = credito(p, credito);
        BigDecimal ultima = c.get("cuotas").get(2).get("total").decimalValue();
        BigDecimal encaje = c.get("encaje").decimalValue();
        BigDecimal ahorroPrevio = c.get("ahorroLetras").decimalValue();
        BigDecimal ahorroUltima = c.get("cuotas").get(2).get("ahorroLetras").decimalValue();
        BigDecimal aDejar = ultima.subtract(encaje).subtract(ahorroPrevio).add(new BigDecimal("5"));
        retirar(p, saldo(p).subtract(aDejar).toPlainString());

        pagar(p, credito, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        assertThat(credito(p, credito).get("estado").asText()).isEqualTo("ACTIVO");

        pagar(p, credito, "{\"usarEncaje\":true,\"usarAhorroLetras\":true}").andExpect(status().isOk());
        assertThat(credito(p, credito).get("estado").asText()).isEqualTo("FINALIZADO");
        assertThat(saldo(p)).isEqualByComparingTo(new BigDecimal("5").add(ahorroUltima));
    }

    @Test
    void reintentoDePagoNoCobraDosVeces() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);
        BigDecimal antes = saldo(p);
        String clave = UUID.randomUUID().toString();
        pagarCon(p, credito, "{}", clave).andExpect(status().isOk());
        BigDecimal tras = saldo(p);
        pagarCon(p, credito, "{}", clave).andExpect(status().isOk());
        assertThat(tras).isLessThan(antes);
        assertThat(saldo(p)).isEqualByComparingTo(tras);
        assertThat(credito(p, credito).get("cuotasPagadas")).hasSize(1);
    }

    @Test
    void aprobarExigePermisoYLimitesSeValidan() throws Exception {
        Persona p = persona();
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        mvc.perform(post("/api/v1/admin/creditos/" + credito + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isForbidden());
        enviar(p, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 99, "plazoMeses", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("IMPORTE_FUERA_DE_RANGO"));
        enviar(p, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 300, "plazoMeses", 37))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAZO_FUERA_DE_RANGO"));
    }

    @Test
    void creditoAjenoNoSeVeNiSePaga() throws Exception {
        Persona duena = persona();
        Persona otra = persona();
        long credito = solicitarLetras(duena, "NORMAL", "300.00", 3);
        aprobar(credito);
        mvc.perform(get("/api/v1/creditos/" + credito).header(HttpHeaders.AUTHORIZATION, bearer(otra.token())))
                .andExpect(status().isNotFound());
        pagar(otra, credito, "{}").andExpect(status().isNotFound());
    }

    // --- Emergente de cliente

    @Test
    void emergenteClienteCobraInteresYDesgravamenDeUnMesYSoloUnoAlMes() throws Exception {
        Persona p = persona();
        enviarConClave(p, "/api/v1/creditos/emergentes/solicitudes", "{\"importe\":300.01}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));

        enviarConClave(p, "/api/v1/creditos/emergentes/solicitudes", "{\"importe\":100.00}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("100");
        JsonNode c = primerCredito(p);
        // 100 + 3,5 % + 0,25 %
        assertThat(c.get("totalPendiente").decimalValue()).isEqualByComparingTo("103.75");

        enviarConClave(p, "/api/v1/creditos/emergentes/solicitudes", "{\"importe\":10.00}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EMERGENTE_ABIERTO"));

        depositar(p, "3.75");
        pagar(p, c.get("id").asLong(), "{}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("0");
        assertThat(primerCredito(p).get("estado").asText()).isEqualTo("FINALIZADO");

        enviarConClave(p, "/api/v1/creditos/emergentes/solicitudes", "{\"importe\":10.00}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FRECUENCIA_EXCEDIDA"));
    }

    // --- Cupo de socio

    @Test
    void cupoSocioDevengaDiarioSobreLoUtilizadoYPagarLoReduce() throws Exception {
        Persona p = persona();
        hacerSocio(p);
        disponer(p, "100.00", null).andExpect(status().isOk());
        JsonNode linea = primerCredito(p);
        long id = linea.get("id").asLong();
        retrocederDevengo(id, 10);

        // Diez días sobre USD 100: 0,12 % y 0,08 % diarios.
        JsonNode c = credito(p, id);
        assertThat(c.get("interes").decimalValue()).isEqualByComparingTo("1.20");
        assertThat(c.get("desgravamen").decimalValue()).isEqualByComparingTo("0.80");

        disponer(p, "50.00", null).andExpect(status().isOk());
        retrocederDevengo(id, 1);
        c = credito(p, id);
        // Un día más, ahora sobre USD 150.
        assertThat(c.get("interes").decimalValue()).isEqualByComparingTo("1.38");
        assertThat(c.get("desgravamen").decimalValue()).isEqualByComparingTo("0.92");

        // Primero cargos, luego capital.
        pagar(p, id, "{\"importe\":52.30}").andExpect(status().isOk());
        c = credito(p, id);
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("100");
        assertThat(c.get("interes").decimalValue()).isEqualByComparingTo("0");

        depositar(p, "10.00");
        pagar(p, id, "{\"importe\":100.01}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_EXCEDIDO"));
        pagar(p, id, "{\"importe\":100.00}").andExpect(status().isOk());
        assertThat(credito(p, id).get("totalPendiente").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void cupoNoSeExcedeYEsExclusivoDeSocios() throws Exception {
        Persona cliente = persona();
        disponer(cliente, "10.00", null).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOLO_SOCIOS"));
        Persona socio = persona();
        hacerSocio(socio);
        disponer(socio, "300.00", null).andExpect(status().isOk());
        disponer(socio, "0.01", null).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CUPO_EXCEDIDO"));
        enviarConClave(socio, "/api/v1/creditos/emergentes/solicitudes", "{\"importe\":10.00}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PERFIL_INCORRECTO"));
    }

    @Test
    void promocionSoloEnSuMesConMaximoYDentroDelCupoGeneral() throws Exception {
        int mesActual = YearMonth.now(java.time.ZoneId.of("America/Guayaquil")).getMonthValue();
        int otroMes = mesActual == 1 ? 2 : 1;
        String navideno = "ESTACIONAL_MES_NAVIDENO";
        String estudiantil = "ESTACIONAL_MES_ESTUDIANTIL";
        cambiarParametro(navideno, String.valueOf(otroMes));
        cambiarParametro(estudiantil, String.valueOf(otroMes));
        Persona p = persona();
        hacerSocio(p);
        disponer(p, "10.00", "NAVIDENO").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PROMOCION_CERRADA"));

        cambiarParametro(navideno, String.valueOf(mesActual));
        disponer(p, "50.01", "NAVIDENO").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));
        disponer(p, "30.00", "NAVIDENO").andExpect(status().isOk());
        disponer(p, "20.01", "NAVIDENO").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));

        // La promoción consume el cupo general: 30 + 270 = 300.
        disponer(p, "270.00", null).andExpect(status().isOk());
        disponer(p, "20.00", "NAVIDENO").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CUPO_EXCEDIDO"));

        long promocion = jdbc.queryForObject("""
                select c.id from creditos c join personas pe on pe.id = c.persona_id
                join usuarios u on u.id = pe.usuario_id where u.correo = ? and c.familia = 'EMERGENTE_ESTACIONAL'
                """, Long.class, p.correo());
        retrocederDevengo(promocion, 10);
        JsonNode c = credito(p, promocion);
        // 30 * 0,05 % * 10 días; sin fondo de desgravamen.
        assertThat(c.get("interes").decimalValue()).isEqualByComparingTo("0.15");
        assertThat(c.get("desgravamen").decimalValue()).isEqualByComparingTo("0");
        cambiarParametro(navideno, "12");
        cambiarParametro(estudiantil, "8");
    }

    // --- Ahorros

    @Test
    void ahorroNormalDepositaRetiraYAbonaInteresUnaVezAlMes() throws Exception {
        Persona p = persona();
        depositar(p, "1200.00");
        long ahorro = crearNormal(p, "Vacaciones");
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":1200.00}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("0");

        // 1200 * 2 % / 12
        YearMonth periodo = YearMonth.of(2026, 9);
        assertThat(ahorros.abonarInteres(ahorro, periodo)).isTrue();
        assertThat(ahorros.abonarInteres(ahorro, periodo)).isFalse();
        assertThat(saldoAhorro(p, ahorro)).isEqualByComparingTo("1202");

        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/retiros", "{\"importe\":1202.01}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/retiros", "{\"importe\":1202.00}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("1202");
    }

    @Test
    void plazoFijoNoSeRetiraAntesYAlVencerPagaSuInteres() throws Exception {
        Persona p = persona();
        depositar(p, "1000.00");
        enviarConClave(p, "/api/v1/ahorros/plazo-fijo", "{\"nombre\":\"Largo\",\"importe\":1000.00,\"plazoMeses\":2}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAZO_FUERA_DE_RANGO"));
        String r = enviarConClave(p, "/api/v1/ahorros/plazo-fijo", "{\"nombre\":\"Largo\",\"importe\":1000.00,\"plazoMeses\":12}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(r).get("id").asLong();
        assertThat(saldo(p)).isEqualByComparingTo("0");

        enviarConClave(p, "/api/v1/ahorros/" + id + "/depositos", "{\"importe\":1.00}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAZO_FIJO_SIN_DEPOSITOS"));
        enviarConClave(p, "/api/v1/ahorros/" + id + "/retiros", "{}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAZO_NO_VENCIDO"));

        jdbc.update("update cuentas_ahorro set vence_en = current_date - 1 where id = ?", id);
        enviarConClave(p, "/api/v1/ahorros/" + id + "/retiros", "{}").andExpect(status().isOk());
        // 1000 * 5 % * 12/12
        assertThat(saldo(p)).isEqualByComparingTo("1050");
        assertThat(jdbc.queryForObject("select estado from cuentas_ahorro where id = ?", String.class, id)).isEqualTo("CERRADA");
    }

    @Test
    void ahorroAjenoNoSeTocaYLaCuentaNoCierraConProductosPendientes() throws Exception {
        Persona p = persona();
        Persona otra = persona();
        depositar(otra, "10.00");
        long ahorro = crearNormal(p, "Mío");
        enviarConClave(otra, "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":1.00}").andExpect(status().isNotFound());

        depositar(p, "5.00");
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":5.00}").andExpect(status().isOk());
        cerrar(p).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AHORROS_CON_SALDO"));
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/retiros", "{\"importe\":5.00}").andExpect(status().isOk());
        retirar(p, "5.00");

        solicitarLetras(p, "NORMAL", "300.00", 3);
        cerrar(p).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CREDITOS_PENDIENTES"));
    }

    // --- Ayudas

    private ResultActions cerrar(Persona p) throws Exception {
        return mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(p.token())));
    }

    private void retrocederDevengo(long credito, int dias) {
        jdbc.update("update creditos set ultimo_devengo = ultimo_devengo - ? where id = ?", dias, credito);
    }

    private ResultActions disponer(Persona p, String importe, String promocion) throws Exception {
        String cuerpo = promocion == null ? "{\"importe\":" + importe + "}"
                : "{\"importe\":" + importe + ",\"promocion\":\"" + promocion + "\"}";
        return enviarConClave(p, "/api/v1/creditos/emergentes/disposiciones", cuerpo);
    }

    private long solicitarLetras(Persona p, String modalidad, String importe, int plazo) throws Exception {
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modalidad\":\"" + modalidad + "\",\"importe\":" + importe + ",\"plazoMeses\":" + plazo + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    private void aprobar(long credito) throws Exception {
        mvc.perform(post("/api/v1/admin/creditos/" + credito + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
    }

    private ResultActions pagar(Persona p, long credito, String cuerpo) throws Exception {
        return pagarCon(p, credito, cuerpo, UUID.randomUUID().toString());
    }

    private ResultActions pagarCon(Persona p, long credito, String cuerpo, String clave) throws Exception {
        return mvc.perform(post("/api/v1/creditos/" + credito + "/pagos").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode credito(Persona p, long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/creditos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode primerCredito(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/creditos/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get(0);
    }

    private long crearNormal(Persona p, String nombre) throws Exception {
        String r = mvc.perform(post("/api/v1/ahorros/normales").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"" + nombre + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    private BigDecimal saldoAhorro(Persona p, long id) throws Exception {
        JsonNode lista = json.readTree(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode a : lista) {
            if (a.get("id").asLong() == id) {
                return a.get("saldo").decimalValue();
            }
        }
        throw new AssertionError("Ahorro no encontrado");
    }

    private ResultActions enviar(Persona p, String ruta, Object cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions enviarConClave(Persona p, String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void cambiarParametro(String codigo, String valor) throws Exception {
        Long id = jdbc.queryForObject("select id from parametros_sistema where codigo = ?", Long.class, codigo);
        // La vigencia debe ser posterior a la anterior, y dos cambios seguidos pueden caer en el mismo instante.
        Thread.sleep(2);
        mvc.perform(put("/api/v1/admin/parametros/" + id).header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":" + valor + ",\"motivo\":\"Prueba\"}"))
                .andExpect(status().isNoContent());
    }

    private void hacerSocio(Persona p) throws Exception {
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, p.correo());
        mvc.perform(post("/api/v1/admin/usuarios/" + id + "/socio").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
    }

    private void depositar(Persona p, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/depositos").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private void retirar(Persona p, String importe) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + p.cuenta() + "/retiros").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("saldoDisponible").decimalValue();
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

    /** Persona con perfil y cuenta activa. */
    private Persona persona() throws Exception {
        String correo = "p-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
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
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        return new Persona(token, cuenta, correo);
    }
}
