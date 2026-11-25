package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Ventanilla de tesorería: cobra y entrega por cualquier persona, y se aplica al momento. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class VentanillaIntegrationTest {

    private static final String DATOS_CREDITO = """
            {"lugarTrabajo":"Tienda","ocupacion":"Vendedora","antiguedadTrabajo":"2 años","ingresosMensuales":500.00,
             "fechaNacimiento":"1990-01-01","estadoCivil":"SOLTERO","referenciaNombre":"Luis","referenciaTelefono":"099",
             "banco":"Pichincha","tipoCuentaBanco":"AHORROS","numeroCuentaBanco":"123"}""";
    private static final byte[] PDF = "%PDF-1.7 comprobante".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private record Persona(String token, long cuenta, String correo, long usuario) {
    }

    @Test
    void enEfectivoCobraLoDeVariasPersonasAlMomentoYSinFoto() throws Exception {
        Persona uno = persona();
        Persona dos = persona();
        hacerSocio(dos);
        long credito = creditoAprobado(uno);
        JsonNode ficha = ficha(dos);
        String mes = ficha.get("opciones").get("meses").get(0).get("periodo").asText();
        BigDecimal aporte = ficha.get("opciones").get("aporteMensual").decimalValue();
        assertThat(ficha.get("persona").get("identificacion").asText()).isNotBlank();
        assertThat(ficha.get("persona").get("numeroCuenta").asText()).isNotBlank();

        List<Map<String, Object>> destinos = List.of(
                Map.of("usuarioId", uno.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", credito, "importe", 40),
                Map.of("usuarioId", dos.usuario(), "tipo", "APORTE_OBLIGATORIO", "periodos", List.of(mes)),
                Map.of("usuarioId", dos.usuario(), "tipo", "CUENTA", "importe", 15));
        BigDecimal cajaAntes = saldoCaja();
        String clave = UUID.randomUUID().toString();
        // Quien entregó el dinero lo elige el tesorero: aquí, la segunda persona.
        JsonNode cobro = json.readTree(cobrar(dos, destinos, "EFECTIVO", null, null, clave, false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        BigDecimal total = new BigDecimal("55").add(aporte);
        assertThat(cobro.get("codigo").asText()).startsWith("DP");
        assertThat(cobro.get("importe").decimalValue()).isEqualByComparingTo(total);
        assertThat(cobro.get("detalle").size()).isEqualTo(3);
        // Aplicado al momento, sin que nadie más confirme.
        assertThat(saldoCaja().subtract(cajaAntes)).isEqualByComparingTo(total);
        assertThat(pagadoPrimeraLetra(uno, credito)).isEqualByComparingTo("40");
        assertThat(saldo(dos)).isEqualByComparingTo("15");
        assertThat(ficha(dos).get("opciones").get("meses").get(0).get("obligatorioPagado").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select estado from solicitudes_deposito where id = ?", String.class,
                cobro.get("id").asLong())).isEqualTo("CONFIRMADA");
        // Lo ve quien pagó entre sus pagos, ya confirmado.
        JsonNode suyo = json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me/depositos")
                .header(HttpHeaders.AUTHORIZATION, bearer(dos.token()))).andReturn().getResponse().getContentAsString()).get(0);
        assertThat(suyo.get("estado").asText()).isEqualTo("CONFIRMADA");

        // Reintentar con la misma clave no cobra dos veces, aunque el mes ya esté pagado.
        JsonNode otraVez = json.readTree(cobrar(dos, destinos, "EFECTIVO", null, null, clave, false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(otraVez.get("id").asLong()).isEqualTo(cobro.get("id").asLong());
        assertThat(saldoCaja().subtract(cajaAntes)).isEqualByComparingTo(total);
    }

    @Test
    void laTransferenciaExigeNumeroYComprobante() throws Exception {
        Persona uno = persona();
        List<Map<String, Object>> destinos = List.of(Map.of("usuarioId", uno.usuario(), "tipo", "CUENTA", "importe", 10));

        cobrar(uno, destinos, "TRANSFERENCIA", null, null, UUID.randomUUID().toString(), true)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("REFERENCIA_REQUERIDA"));
        cobrar(uno, destinos, "TRANSFERENCIA", null, "TR-1", UUID.randomUUID().toString(), false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("COMPROBANTE_REQUERIDO"));
        JsonNode cobro = json.readTree(cobrar(uno, destinos, "TRANSFERENCIA", null, "TR-1", UUID.randomUUID().toString(), true)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(saldo(uno)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select count(*) from comprobantes where tipo = 'DEPOSITO' and referencia_id = ?",
                Long.class, cobro.get("id").asLong())).isEqualTo(1L);
    }

    @Test
    void conElSaldoDeUnAhorroSePagaLoSuyoSinTocarLaCaja() throws Exception {
        Persona uno = persona();
        Persona otra = persona();
        long credito = creditoAprobado(uno);
        long creditoOtra = creditoAprobado(otra);
        long ahorro = crearAhorro(uno);
        cobrar(uno, List.of(Map.of("usuarioId", uno.usuario(), "tipo", "AHORRO", "referenciaId", ahorro, "importe", 100)),
                "EFECTIVO", null, null, UUID.randomUUID().toString(), false).andExpect(status().isCreated());
        assertThat(saldoAhorro(uno, ahorro)).isEqualByComparingTo("100");

        // Con el ahorro de una persona no se paga lo de otra.
        cobrar(uno, List.of(Map.of("usuarioId", otra.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", creditoOtra, "importe", 10)),
                "AHORRO", ahorro, null, UUID.randomUUID().toString(), false)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOLO_PROPIO"));

        BigDecimal cajaAntes = saldoCaja();
        BigDecimal cuentaAntes = saldo(uno);
        JsonNode cobro = json.readTree(cobrar(uno,
                List.of(Map.of("usuarioId", uno.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", credito, "importe", 30)),
                "AHORRO", ahorro, null, UUID.randomUUID().toString(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(cobro.get("codigo").isNull()).isTrue();
        assertThat(saldoAhorro(uno, ahorro)).isEqualByComparingTo("70");
        assertThat(pagadoPrimeraLetra(uno, credito)).isEqualByComparingTo("30");
        // Pasó por la cuenta principal y salió al crédito: su saldo queda igual.
        assertThat(saldo(uno)).isEqualByComparingTo(cuentaAntes);
        assertThat(saldoCaja()).isEqualByComparingTo(cajaAntes);

        // Más de lo que tiene el ahorro: no se paga nada.
        cobrar(uno, List.of(Map.of("usuarioId", uno.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", credito, "importe", 80)),
                "AHORRO", ahorro, null, UUID.randomUUID().toString(), false)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        assertThat(saldoAhorro(uno, ahorro)).isEqualByComparingTo("70");
    }

    @Test
    void entregaDineroDesdeLaCuentaODirectoDeUnAhorro() throws Exception {
        Persona uno = persona();
        long ahorro = crearAhorro(uno);
        cobrar(uno, List.of(Map.of("usuarioId", uno.usuario(), "tipo", "AHORRO", "referenciaId", ahorro, "importe", 60),
                Map.of("usuarioId", uno.usuario(), "tipo", "CUENTA", "importe", 25)),
                "EFECTIVO", null, null, UUID.randomUUID().toString(), false).andExpect(status().isCreated());

        BigDecimal cajaAntes = saldoCaja();
        retirar(uno, "AHORRO", ahorro, 20, "EFECTIVO", UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ENTREGADA"));
        assertThat(saldoAhorro(uno, ahorro)).isEqualByComparingTo("40");
        assertThat(saldo(uno)).isEqualByComparingTo("25");
        assertThat(cajaAntes.subtract(saldoCaja())).isEqualByComparingTo("20");

        String clave = UUID.randomUUID().toString();
        retirar(uno, "CUENTA", null, 25, "EFECTIVO", clave).andExpect(status().isCreated());
        retirar(uno, "CUENTA", null, 25, "EFECTIVO", clave).andExpect(status().isCreated());
        assertThat(saldo(uno)).isEqualByComparingTo("0");
        assertThat(cajaAntes.subtract(saldoCaja())).isEqualByComparingTo("45");

        retirar(uno, "CUENTA", null, 1, "EFECTIVO", UUID.randomUUID().toString()).andExpect(status().isConflict());
    }

    @Test
    void soloElPersonalConPermisoUsaLaVentanilla() throws Exception {
        Persona uno = persona();
        mvc.perform(get("/api/v1/ventanilla/personas").param("buscar", "ana prueba")
                .header(HttpHeaders.AUTHORIZATION, bearer(uno.token()))).andExpect(status().isForbidden());
        JsonNode lista = json.readTree(mvc.perform(get("/api/v1/ventanilla/personas").param("buscar", "ana prueba")
                .header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(lista.size()).isPositive();
        assertThat(lista.get(0).has("identificacion")).isTrue();
        // Tesorería tiene el permiso.
        assertThat(jdbc.queryForObject("""
                select count(*) from cargo_permisos cp join cargos c on c.id = cp.cargo_id join permisos p on p.id = cp.permiso_id
                where c.codigo = 'TESORERIA' and p.codigo = 'GESTIONAR_CUENTAS'
                """, Long.class)).isEqualTo(1L);
    }

    // --- Apoyo

    private ResultActions cobrar(Persona pagador, List<Map<String, Object>> destinos, String origen, Long ahorroId,
            String referencia, String clave, boolean conComprobante) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("pagadorId", pagador.usuario(), "destinos", destinos, "origen", origen));
        if (ahorroId != null) {
            cuerpo.put("ahorroId", ahorroId);
        }
        if (referencia != null) {
            cuerpo.put("referencia", referencia);
        }
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/ventanilla/cobros")
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(cuerpo)));
        if (conComprobante) {
            req.file(new MockMultipartFile("archivos", "recibo.pdf", "application/pdf", PDF));
        }
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, bearer(presidente())).header("Idempotency-Key", clave));
    }

    private ResultActions retirar(Persona p, String origen, Long ahorroId, int importe, String medio, String clave) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("usuarioId", p.usuario(), "origen", origen, "importe", importe, "medio", medio));
        if (ahorroId != null) {
            cuerpo.put("ahorroId", ahorroId);
        }
        return mvc.perform(post("/api/v1/ventanilla/retiros").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo)));
    }

    private JsonNode ficha(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/ventanilla/personas/" + p.usuario())
                .header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private BigDecimal pagadoPrimeraLetra(Persona p, long credito) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/creditos/" + credito).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("cuotas").get(0).get("pagado").decimalValue();
    }

    private long creditoAprobado(Persona p) throws Exception {
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header("Idempotency-Key", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(p.token())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/" + id + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        return id;
    }

    private long crearAhorro(Persona p) throws Exception {
        String r = mvc.perform(post("/api/v1/ahorros/normales").header("Idempotency-Key", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(p.token())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Para la casa\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    private BigDecimal saldoAhorro(Persona p, long id) throws Exception {
        for (JsonNode a : json.readTree(mvc.perform(get("/api/v1/ahorros/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString())) {
            if (a.get("id").asLong() == id) {
                return a.get("saldo").decimalValue();
            }
        }
        throw new AssertionError("Ahorro no encontrado");
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("saldoDisponible").decimalValue();
    }

    private BigDecimal saldoCaja() {
        return jdbc.queryForObject("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from movimientos_caja m join cajas c on c.id = m.caja_id where c.codigo = 'PRINCIPAL'
                """, BigDecimal.class);
    }

    private void hacerSocio(Persona p) throws Exception {
        mvc.perform(post("/api/v1/admin/usuarios/" + p.usuario() + "/socio").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
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

    /** Persona con perfil, datos de crédito y cuenta activa. */
    private Persona persona() throws Exception {
        String correo = "v-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
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
        mvc.perform(put("/api/v1/personas/me/datos-credito").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(DATOS_CREDITO)).andExpect(status().isOk());
        String r = mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cuenta = json.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
        Long usuario = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, correo);
        return new Persona(token, cuenta, correo, usuario);
    }
}
