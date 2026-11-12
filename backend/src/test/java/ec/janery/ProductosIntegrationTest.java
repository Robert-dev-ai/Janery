package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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


/** Créditos y ahorros de principio a fin contra PostgreSQL, en el esquema aislado janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class ProductosIntegrationTest {

    private static final String DATOS_CREDITO = """
            {"lugarTrabajo":"Tienda","ocupacion":"Vendedora","antiguedadTrabajo":"2 años","ingresosMensuales":500.00,
             "fechaNacimiento":"1990-01-01","estadoCivil":"SOLTERO","referenciaNombre":"Luis","referenciaTelefono":"099",
             "banco":"Pichincha","tipoCuentaBanco":"AHORROS","numeroCuentaBanco":"123"}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

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
        // Recibe 1000 y paga 10 % de encaje y 1,5 % de suministros (cliente, normal).
        assertThat(saldo(p)).isEqualByComparingTo("1085");
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
        // Al final solo cuestan los cargos y los suministros: encaje y ahorro letras regresaron.
        assertThat(saldo(p)).isEqualByComparingTo(new BigDecimal("200").subtract(cargos).subtract(new BigDecimal("15")));
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

        pagar(p, credito, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_CUENTA_INSUFICIENTE"));
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

    /** Antes devolvía la operación del primer crédito como si hubiera pagado el segundo. */
    @Test
    void laClaveDeUnPagoNoSirveParaOtroCredito() throws Exception {
        Persona p = persona();
        depositar(p, "300.00");
        long primero = solicitarLetras(p, "NORMAL", "300.00", 3);
        long segundo = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(primero);
        aprobar(segundo);
        String clave = UUID.randomUUID().toString();
        pagarCon(p, primero, "{}", clave).andExpect(status().isOk());
        BigDecimal tras = saldo(p);

        pagarCon(p, segundo, "{}", clave).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CLAVE_REUTILIZADA"));
        assertThat(credito(p, segundo).get("cuotasPagadas")).isEmpty();
        assertThat(saldo(p)).isEqualByComparingTo(tras);
    }

    /** Antes un doble clic creaba dos solicitudes: aprobadas las dos, la persona recibía el préstamo dos veces. */
    @Test
    void repetirLaSolicitudDeCreditoConLaMismaClaveNoCreaOtra() throws Exception {
        Persona p = persona();
        String clave = UUID.randomUUID().toString();
        String cuerpo = "{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}";
        long primera = json.readTree(conClave(p, "/api/v1/creditos/letras/solicitudes", cuerpo, clave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        long segunda = json.readTree(conClave(p, "/api/v1/creditos/letras/solicitudes", cuerpo, clave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        assertThat(segunda).isEqualTo(primera);
        assertThat(jdbc.queryForObject("select count(*) from creditos c join personas pe on pe.id = c.persona_id"
                + " join usuarios u on u.id = pe.usuario_id where u.correo = ?", Integer.class, p.correo())).isEqualTo(1);
        // La misma clave no sirve para un emergente.
        conClave(p, "/api/v1/creditos/emergentes/solicitudes", "{\"importe\":300.00}", clave)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLAVE_REUTILIZADA"));
    }

    @Test
    void repetirLaAperturaDeUnAhorroConLaMismaClaveNoAbreOtro() throws Exception {
        Persona p = persona();
        String clave = UUID.randomUUID().toString();
        long primero = json.readTree(conClave(p, "/api/v1/ahorros/normales", "{\"nombre\":\"Viaje\"}", clave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        long segundo = json.readTree(conClave(p, "/api/v1/ahorros/normales", "{\"nombre\":\"Viaje\"}", clave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        assertThat(segundo).isEqualTo(primero);
        assertThat(json.readTree(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString())).hasSize(1);
    }

    @Test
    void sinClaveDeIdempotenciaNoSeOpera() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);
        BigDecimal antes = saldo(p);
        pagarCon(p, credito, "{}", "   ").andExpect(status().isBadRequest());
        pagarCon(p, credito, "{}", "x".repeat(101)).andExpect(status().isBadRequest());
        assertThat(saldo(p)).isEqualByComparingTo(antes);
    }

    @Test
    void unClienteSoloPideElCreditoALetrasNormal() throws Exception {
        Persona p = persona();
        mvc.perform(get("/api/v1/creditos/productos").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(jsonPath("$.perfil").value("CLIENTE"))
                .andExpect(jsonPath("$.letras.length()").value(1))
                .andExpect(jsonPath("$.letras[0].modalidad").value("NORMAL"));
        for (String ruta : new String[] { "/api/v1/creditos/letras/simulacion", "/api/v1/creditos/letras/solicitudes" }) {
            mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(p.token())).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"modalidad\":\"INVERSION\",\"importe\":300.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MODALIDAD_SOLO_SOCIOS"));
        }
    }

    @Test
    void pagoParcialDeLetraCobraPrimeroElCapital() throws Exception {
        Persona p = persona();
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);
        // Letra fija: capital 100, interés 4,50, desgravamen 0,30 y ahorro 1,20 = 106 (cada letra ahorra al menos 0,50).
        JsonNode c = credito(p, credito);
        assertThat(c.get("cuotas")).allSatisfy(l -> assertThat(l.get("total").decimalValue()).isEqualByComparingTo("106"));

        pagar(p, credito, "{\"importe\":60.00}").andExpect(status().isOk());
        c = credito(p, credito);
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("240");
        assertThat(c.get("ahorroLetras").decimalValue()).isEqualByComparingTo("0");
        assertThat(c.get("cuotasPagadas")).isEmpty();
        assertThat(c.get("cuotas").get(0).get("pagado").decimalValue()).isEqualByComparingTo("60");
        assertThat(c.get("totalPendiente").decimalValue()).isEqualByComparingTo("258");

        pagar(p, credito, "{\"importe\":258.01}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_EXCEDIDO"));

        // 40 de capital; lo demás a interés, desgravamen y por último ahorro letras.
        pagar(p, credito, "{\"importe\":44.90}").andExpect(status().isOk());
        c = credito(p, credito);
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("200");
        assertThat(c.get("ahorroLetras").decimalValue()).isEqualByComparingTo("0.10");
        assertThat(c.get("cuotasPagadas")).isEmpty();

        pagar(p, credito, "{}").andExpect(status().isOk());
        c = credito(p, credito);
        assertThat(c.get("ahorroLetras").decimalValue()).isEqualByComparingTo("1.20");
        assertThat(c.get("cuotasPagadas")).hasSize(1);
    }

    @Test
    void pagarDeMasPasaALaSiguienteLetraYRegistraAQuePertenece() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);
        pagar(p, credito, "{\"importe\":150.00}").andExpect(status().isOk());

        JsonNode c = credito(p, credito);
        assertThat(c.get("cuotasPagadas")).hasSize(1);
        assertThat(c.get("cuotas").get(1).get("pagado").decimalValue()).isEqualByComparingTo("44");
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("156");

        JsonNode movimientos = json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me/movimientos").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("contenido");
        assertThat(movimientos.get(0).get("detalle").asText()).contains("Crédito #" + credito, "letras 1, 2 de 3");

        // Pagar todo lo que queda termina el crédito.
        pagar(p, credito, "{\"importe\":168.00}").andExpect(status().isOk());
        assertThat(credito(p, credito).get("estado").asText()).isEqualTo("FINALIZADO");
    }

    @Test
    void pagosSimultaneosNoPisanElCapitalNiElSaldo() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);
        BigDecimal antes = saldo(p);
        java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> pagos = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                pagos.add(hilos.submit(() -> pagar(p, credito, "{\"importe\":20.00}").andReturn().getResponse().getStatus()));
            }
            for (java.util.concurrent.Future<Integer> pago : pagos) {
                assertThat(pago.get()).isEqualTo(200);
            }
        } finally {
            hilos.shutdown();
        }
        assertThat(credito(p, credito).get("capitalPendiente").decimalValue()).isEqualByComparingTo("240");
        assertThat(saldo(p)).isEqualByComparingTo(antes.subtract(new BigDecimal("60")));
    }

    @Test
    void precancelarExigeLaMitadMasUnaYCobraSoloElCapital() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        long credito = solicitarLetras(p, "NORMAL", "500.00", 5);
        aprobar(credito);
        pagar(p, credito, "{}").andExpect(status().isOk());
        pagar(p, credito, "{}").andExpect(status().isOk());
        precancelar(p, credito).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PRECANCELACION_NO_DISPONIBLE"));
        assertThat(credito(p, credito).get("precancelable").asBoolean()).isFalse();

        pagar(p, credito, "{}").andExpect(status().isOk());
        JsonNode c = credito(p, credito);
        assertThat(c.get("letrasParaPrecancelar").asInt()).isEqualTo(3);
        assertThat(c.get("precancelable").asBoolean()).isTrue();
        BigDecimal capital = c.get("capitalPendiente").decimalValue();
        assertThat(capital).isEqualByComparingTo("200");
        BigDecimal encaje = c.get("encaje").decimalValue();
        BigDecimal ahorro = c.get("ahorroLetras").decimalValue();
        BigDecimal antes = saldo(p);

        precancelar(p, credito).andExpect(status().isOk());

        c = credito(p, credito);
        assertThat(c.get("estado").asText()).isEqualTo("FINALIZADO");
        assertThat(c.get("capitalPendiente").decimalValue()).isEqualByComparingTo("0");
        assertThat(c.get("totalPendiente").decimalValue()).isEqualByComparingTo("0");
        assertThat(c.get("encajeDevuelto").asBoolean()).isTrue();
        assertThat(saldo(p)).isEqualByComparingTo(antes.subtract(capital).add(encaje).add(ahorro));
    }

    @Test
    void creditoALetrasExigeDatosDeCreditoALosClientes() throws Exception {
        Persona p = personaSinDatosCredito();
        enviar(p, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 300, "plazoMeses", 3, "origenEncaje", "CREDITO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DATOS_CREDITO_INCOMPLETOS"));
        completarDatosCredito(p.token());
        enviar(p, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 300, "plazoMeses", 3, "origenEncaje", "CREDITO"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void aprobarExigePermisoYLimitesSeValidan() throws Exception {
        Persona p = persona();
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        mvc.perform(post("/api/v1/admin/creditos/" + credito + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isForbidden());
        enviar(p, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 99, "plazoMeses", 3, "origenEncaje", "CREDITO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("IMPORTE_FUERA_DE_RANGO"));
        enviar(p, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 300, "plazoMeses", 37, "origenEncaje", "CREDITO"))
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
        // No sale dinero hasta que se apruebe.
        assertThat(saldo(p)).isEqualByComparingTo("0");
        assertThat(primerCredito(p).get("estado").asText()).isEqualTo("SOLICITADO");
        aprobar(primerCredito(p).get("id").asLong());
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

    @Test
    void suministrosSeCobranAlInicioComoGananciaYNoSeDevuelven() throws Exception {
        // Cliente, normal: 1,5 %, descontado del crédito junto con el encaje.
        Persona p = persona();
        long normal = solicitarLetras(p, "NORMAL", "1000.00", 3);
        assertThat(credito(p, normal).get("suministros").decimalValue()).isEqualByComparingTo("15");
        aprobar(normal);
        assertThat(saldo(p)).isEqualByComparingTo("885");
        assertThat(jdbc.queryForObject("""
                SELECT SUM(l.importe) FROM libro_contable l JOIN suministros_credito s ON s.operacion_id = l.operacion_id
                WHERE s.credito_id = ? AND l.linea = '4.8'""", BigDecimal.class, normal)).isEqualByComparingTo("15");

        // Socio, inversión: 0,75 %, en efectivo: entra a la caja junto con el encaje.
        Persona socio = persona();
        hacerSocio(socio);
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header("Idempotency-Key", UUID.randomUUID().toString()).header(HttpHeaders.AUTHORIZATION, bearer(socio.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modalidad\":\"INVERSION\",\"importe\":1000.00,\"plazoMeses\":3,\"origenEncaje\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long inversion = json.readTree(r).get("id").asLong();
        JsonNode antes = credito(socio, inversion);
        assertThat(antes.get("suministros").decimalValue()).isEqualByComparingTo("7.50");
        aprobar(inversion);
        JsonNode c = credito(socio, inversion);
        assertThat(saldo(socio)).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("""
                SELECT SUM(m.importe) FROM movimientos_caja m JOIN suministros_credito s ON s.operacion_id = m.operacion_id
                WHERE s.credito_id = ? AND m.sentido = 'ENTRADA' AND m.concepto IN ('ENCAJE_EFECTIVO', 'SUMINISTROS_EFECTIVO')""",
                BigDecimal.class, inversion)).isEqualByComparingTo(c.get("encaje").decimalValue().add(new BigDecimal("7.50")));
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
    void ahorroNormalCorreInteresDiarioAparteYSeLiberaElDiaUno() throws Exception {
        Persona p = persona();
        depositar(p, "3650.00");
        long ahorro = crearNormal(p, "Vacaciones");
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":3650.00}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("0");

        // Sin un día 1 de por medio, corre pero no se puede retirar. 3650 * 5 % / 365 = 0,50 al día.
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Guayaquil"));
        LocalDate primero = hoy.withDayOfMonth(1);
        jdbc.update("update cuentas_ahorro set ultimo_devengo = ? where id = ?", primero.minusDays(10), ahorro);
        JsonNode a = ahorro(p, ahorro);
        assertThat(a.get("saldo").decimalValue()).isEqualByComparingTo("3650");
        // Diez días del mes anterior ya liberados, y los días de este mes acumulándose.
        assertThat(a.get("interesDisponible").decimalValue()).isEqualByComparingTo("5");
        assertThat(a.get("interesAcumulado").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.50").multiply(BigDecimal.valueOf(hoy.getDayOfMonth() - 1)));
        assertThat(a.get("proximaLiberacion").asText()).isEqualTo(primero.plusMonths(1).toString());

        // Solo sale lo liberado, a la cuenta principal; el saldo no cambia.
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/interes", "{}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("5");
        a = ahorro(p, ahorro);
        assertThat(a.get("saldo").decimalValue()).isEqualByComparingTo("3650");
        assertThat(a.get("interesDisponible").decimalValue()).isEqualByComparingTo("0");
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/interes", "{}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("INTERES_NO_DISPONIBLE"));

        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/retiros", "{\"importe\":3650.01}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        enviarConClave(p, "/api/v1/ahorros/" + ahorro + "/retiros", "{\"importe\":3650.00}").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualByComparingTo("3655");
    }

    // --- Atraso y mora

    @Test
    void laMoraCorreSoloAClientesPorLetraAtrasadaYSeCobraAlFinal() throws Exception {
        Persona cliente = persona();
        long credito = solicitarLetras(cliente, "NORMAL", "300.00", 3);
        aprobar(credito);
        atrasarDiezDias(credito);

        JsonNode c = credito(cliente, credito);
        BigDecimal letra = c.get("cuotas").get(0).get("total").decimalValue();
        assertThat(c.get("letrasAtrasadas").asInt()).isEqualTo(1);
        assertThat(c.get("cuotas").get(0).get("diasAtraso").asLong()).isEqualTo(10);
        BigDecimal mora = c.get("moraPendiente").decimalValue();
        assertThat(mora).isEqualByComparingTo("0.10");
        assertThat(c.get("totalPendiente").decimalValue()).isEqualByComparingTo(letra.multiply(BigDecimal.valueOf(3)).add(mora));

        // Pagar solo la letra deja la mora: la letra sigue abierta, pero la mora ya no crece.
        depositar(cliente, letra.add(mora).toPlainString());
        pagar(cliente, credito, "{\"importe\":" + letra + "}").andExpect(status().isOk());
        c = credito(cliente, credito);
        assertThat(c.get("cuotasPagadas")).isEmpty();
        assertThat(c.get("moraPendiente").decimalValue()).isEqualByComparingTo(mora);
        assertThat(jdbc.queryForObject("select mora_hasta is not null from cuotas_credito where credito_id = ? and numero = 1", Boolean.class, credito)).isTrue();
        pagar(cliente, credito, "{\"importe\":" + mora + "}").andExpect(status().isOk());
        c = credito(cliente, credito);
        assertThat(c.get("cuotasPagadas")).hasSize(1);
        assertThat(c.get("moraPendiente").decimalValue()).isEqualByComparingTo("0");
        assertThat(c.get("letrasAtrasadas").asInt()).isZero();

        Persona socio = persona();
        hacerSocio(socio);
        long deSocio = solicitarLetras(socio, "NORMAL", "300.00", 3);
        aprobar(deSocio);
        atrasarDiezDias(deSocio);
        c = credito(socio, deSocio);
        assertThat(c.get("letrasAtrasadas").asInt()).isEqualTo(1);
        assertThat(c.get("moraPendiente").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void unaLetraIncompletaCobraCapitalDesgravamenInteresYAhorro() throws Exception {
        Persona p = persona();
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        aprobar(credito);
        JsonNode q = credito(p, credito).get("cuotas").get(0);
        BigDecimal capital = q.get("capital").decimalValue();
        BigDecimal desgravamen = q.get("fondoDesgravamen").decimalValue();
        depositar(p, "300.00");
        // Cubre capital y desgravamen, y un centavo de interés.
        pagar(p, credito, "{\"importe\":" + capital.add(desgravamen).add(new BigDecimal("0.01")) + "}").andExpect(status().isOk());
        java.util.Map<String, Object> fila = jdbc.queryForMap("""
                select capital_pagado, desgravamen_pagado, interes_pagado, ahorro_pagado
                from cuotas_credito where credito_id = ? and numero = 1
                """, credito);
        assertThat((BigDecimal) fila.get("capital_pagado")).isEqualByComparingTo(capital);
        assertThat((BigDecimal) fila.get("desgravamen_pagado")).isEqualByComparingTo(desgravamen);
        assertThat((BigDecimal) fila.get("interes_pagado")).isEqualByComparingTo("0.01");
        assertThat((BigDecimal) fila.get("ahorro_pagado")).isEqualByComparingTo("0");
    }

    // --- Encaje y documentos

    @Test
    void elEncajeSalDeDondeElSolicitanteEligio() throws Exception {
        Persona p = persona();
        // De la cuenta: debe tenerlo antes de recibir el crédito.
        long deCuenta = solicitarLetrasCon(p, "\"origenEncaje\":\"CUENTA\"");
        mvc.perform(post("/api/v1/admin/creditos/" + deCuenta + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        mvc.perform(post("/api/v1/admin/creditos/" + deCuenta + "/rechazar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Sin saldo\"}")).andExpect(status().isNoContent());

        // En efectivo: encaje y suministros (1,5 % de 300) entran a la caja y el desembolso llega completo.
        BigDecimal cajaAntes = saldoCaja();
        long enEfectivo = solicitarLetrasCon(p, "\"origenEncaje\":\"EFECTIVO\"");
        aprobar(enEfectivo);
        BigDecimal encaje = credito(p, enEfectivo).get("encaje").decimalValue();
        assertThat(encaje.signum()).isPositive();
        assertThat(saldo(p)).isEqualByComparingTo("300");
        assertThat(saldoCaja()).isEqualByComparingTo(cajaAntes.add(encaje).add(new BigDecimal("4.50")));

        // De un ahorro: baja el ahorro, no la cuenta, también por los suministros.
        Persona otra = persona();
        depositar(otra, "100.00");
        long ahorro = crearNormal(otra, "Reserva");
        enviarConClave(otra, "/api/v1/ahorros/" + ahorro + "/depositos", "{\"importe\":100.00}").andExpect(status().isOk());
        long deAhorro = solicitarLetrasCon(otra, "\"origenEncaje\":\"AHORROS\",\"ahorroEncajeId\":" + ahorro);
        aprobar(deAhorro);
        assertThat(saldo(otra)).isEqualByComparingTo("300");
        assertThat(saldoAhorro(otra, ahorro)).isEqualByComparingTo(new BigDecimal("100").subtract(credito(otra, deAhorro).get("encaje").decimalValue()).subtract(new BigDecimal("4.50")));

        // Sin origen no hay solicitud.
        Persona sinOrigen = persona();
        enviar(sinOrigen, "/api/v1/creditos/letras/solicitudes", Map.of("modalidad", "NORMAL", "importe", 300, "plazoMeses", 3))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ORIGEN_ENCAJE_REQUERIDO"));
    }

    @Test
    void laSolicitudAdmiteVariosPdfYRechazaLoDemas() throws Exception {
        Persona p = persona();
        long credito = solicitarLetras(p, "NORMAL", "300.00", 3);
        byte[] pdf = "%PDF-1.7 prueba".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String ruta = "/api/v1/creditos/" + credito + "/documentos";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(ruta)
                .file(new org.springframework.mock.web.MockMultipartFile("archivos", "falso.pdf", "application/pdf", "no es pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DOCUMENTO_NO_PDF"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(ruta)
                .file(new org.springframework.mock.web.MockMultipartFile("archivos", "rol.pdf", "application/pdf", pdf))
                .file(new org.springframework.mock.web.MockMultipartFile("archivos", "cedula.pdf", "application/pdf", pdf))
                .header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        // Otra persona no los ve; quien aprueba, sí.
        mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(persona().token()))).andExpect(status().isNotFound());
        JsonNode lista = json.readTree(mvc.perform(get("/api/v1/admin/creditos/" + credito + "/documentos").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(lista.get(0).get("nombre").asText()).isEqualTo("rol.pdf");
        byte[] bajado = mvc.perform(get("/api/v1/admin/creditos/" + credito + "/documentos/" + lista.get(0).get("id").asLong())
                .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bajado).isEqualTo(pdf);
    }

    /** Con la fecha de la aplicación: la de la base de datos está en UTC y de noche ya es el día siguiente. */
    private void atrasarDiezDias(long credito) {
        jdbc.update("update cuotas_credito set vencimiento = ? where credito_id = ? and numero = 1",
                LocalDate.now(java.time.ZoneId.of("America/Guayaquil")).minusDays(10), credito);
    }

    private long solicitarLetrasCon(Persona p, String origen) throws Exception {
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header("Idempotency-Key", UUID.randomUUID().toString()).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":3," + origen + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    private BigDecimal saldoCaja() {
        return jdbc.queryForObject("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from movimientos_caja m join cajas c on c.id = m.caja_id where c.codigo = 'PRINCIPAL'
                """, BigDecimal.class);
    }

    private JsonNode ahorro(Persona p, long id) throws Exception {
        for (JsonNode a : json.readTree(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString())) {
            if (a.get("id").asLong() == id) {
                return a;
            }
        }
        throw new AssertionError("Ahorro no encontrado: " + id);
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
        // 1000 * 7 % * 12/12, la tasa del plazo de 12 meses.
        assertThat(saldo(p)).isEqualByComparingTo("1070");
        assertThat(jdbc.queryForObject("select estado from cuentas_ahorro where id = ?", String.class, id)).isEqualTo("CERRADA");
    }

    /** Antes respondía 409 al reintento: la pantalla mostraba un error aunque el plazo fijo sí se había abierto. */
    @Test
    void reintentoDePlazoFijoDevuelveElMismoAhorroSinCobrarDosVeces() throws Exception {
        Persona p = persona();
        depositar(p, "800.00");
        String clave = UUID.randomUUID().toString();
        String cuerpo = "{\"nombre\":\"Fijo\",\"importe\":500.00,\"plazoMeses\":12}";
        long primero = json.readTree(plazoFijo(p, cuerpo, clave).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long reintento = json.readTree(plazoFijo(p, cuerpo, clave).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        assertThat(reintento).isEqualTo(primero);
        assertThat(saldo(p)).isEqualByComparingTo("300");
    }

    @Test
    void laClaveDeUnDepositoAAhorroNoSirveParaOtroAhorro() throws Exception {
        Persona p = persona();
        depositar(p, "100.00");
        long uno = crearNormal(p, "Uno");
        long dos = crearNormal(p, "Dos");
        String clave = UUID.randomUUID().toString();
        conClave(p, "/api/v1/ahorros/" + uno + "/depositos", "{\"importe\":10.00}", clave).andExpect(status().isOk());

        conClave(p, "/api/v1/ahorros/" + dos + "/depositos", "{\"importe\":10.00}", clave)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLAVE_REUTILIZADA"));
        assertThat(saldoAhorro(p, dos)).isEqualByComparingTo("0");
        assertThat(saldo(p)).isEqualByComparingTo("90");
    }

    private ResultActions plazoFijo(Persona p, String cuerpo, String clave) throws Exception {
        return conClave(p, "/api/v1/ahorros/plazo-fijo", cuerpo, clave);
    }

    private ResultActions conClave(Persona p, String ruta, String cuerpo, String clave) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
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
        ResultActions r = enviarConClave(p, "/api/v1/creditos/emergentes/disposiciones", cuerpo);
        // Toda disposición se aprueba antes de salir; aquí se prueba lo que ocurre después.
        if (r.andReturn().getResponse().getStatus() == 200) {
            long id = json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/v1/admin/creditos/disposiciones/" + id + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                    .andExpect(status().isNoContent());
        }
        return r;
    }

    private long solicitarLetras(Persona p, String modalidad, String importe, int plazo) throws Exception {
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header("Idempotency-Key", UUID.randomUUID().toString()).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modalidad\":\"" + modalidad + "\",\"importe\":" + importe + ",\"plazoMeses\":" + plazo + ",\"origenEncaje\":\"CREDITO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    private ResultActions precancelar(Persona p, long credito) throws Exception {
        return mvc.perform(post("/api/v1/creditos/" + credito + "/precancelacion").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private void completarDatosCredito(String token) throws Exception {
        mvc.perform(put("/api/v1/personas/me/datos-credito").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(DATOS_CREDITO)).andExpect(status().isOk());
    }

    private Persona persona() throws Exception {
        Persona p = personaSinDatosCredito();
        completarDatosCredito(p.token());
        return p;
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
        String r = mvc.perform(post("/api/v1/ahorros/normales").header("Idempotency-Key", UUID.randomUUID().toString()).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
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
                .header("Idempotency-Key", UUID.randomUUID().toString())
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
    private Persona personaSinDatosCredito() throws Exception {
        String correo = "p-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
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
        return new Persona(token, cuenta, correo);
    }
}
