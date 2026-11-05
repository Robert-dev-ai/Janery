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

/** Pagar: varios destinos, de varias personas, con comprobante obligatorio y confirmación del personal. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class PagosIntegrationTest {

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
    void unPagoCubreLetrasAhorrosYAportesDeVariasPersonasAlConfirmarse() throws Exception {
        Persona quienPaga = persona();
        hacerSocio(quienPaga);
        Persona uno = persona();
        Persona dos = persona();
        Persona tres = persona();
        hacerSocio(tres);
        long creditoUno = creditoAprobado(uno);
        long creditoDos = creditoAprobado(dos);
        long ahorroUno = crearAhorro(uno);
        BigDecimal saldoUno = saldo(uno);
        JsonNode opcionesTres = opciones(quienPaga, tres);
        List<String> meses = List.of(opcionesTres.get("meses").get(0).get("periodo").asText(),
                opcionesTres.get("meses").get(1).get("periodo").asText());
        BigDecimal aporte = opcionesTres.get("aporteMensual").decimalValue();

        // Quien paga ve solo el nombre de la otra persona y lo que se le puede pagar.
        assertThat(opciones(quienPaga, uno).get("creditos").get(0).get("id").asLong()).isEqualTo(creditoUno);
        assertThat(opciones(quienPaga, uno).get("creditos").get(0).get("proximaLetra").decimalValue()).isPositive();

        List<Map<String, Object>> destinos = List.of(
                Map.of("usuarioId", uno.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", creditoUno, "importe", 30),
                Map.of("usuarioId", dos.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", creditoDos, "importe", 50),
                Map.of("usuarioId", uno.usuario(), "tipo", "AHORRO", "referenciaId", ahorroUno, "importe", 20),
                Map.of("usuarioId", tres.usuario(), "tipo", "APORTE_OBLIGATORIO", "periodos", meses));

        // Sin comprobante no hay pago.
        pagar(quienPaga, destinos, UUID.randomUUID().toString(), false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("COMPROBANTE_REQUERIDO"));

        String clave = UUID.randomUUID().toString();
        JsonNode creado = json.readTree(pagar(quienPaga, destinos, clave, true).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        BigDecimal total = new BigDecimal("100").add(aporte.multiply(BigDecimal.valueOf(2)));
        assertThat(creado.get("importe").decimalValue()).isEqualByComparingTo(total);
        // Reintentar con la misma clave no duplica.
        JsonNode otraVez = json.readTree(pagar(quienPaga, destinos, clave, true).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(otraVez.get("id").asLong()).isEqualTo(creado.get("id").asLong());
        assertThat(jdbc.queryForObject("select count(*) from destinos_pago where solicitud_id = ?", Long.class,
                creado.get("id").asLong())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("select count(*) from comprobantes where tipo = 'DEPOSITO' and referencia_id = ?",
                Long.class, creado.get("id").asLong())).isEqualTo(1L);

        // Hasta que el personal confirma, nada cambia.
        assertThat(pagadoPrimeraLetra(uno, creditoUno)).isEqualByComparingTo("0");
        JsonNode mio = json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me/depositos")
                .header(HttpHeaders.AUTHORIZATION, bearer(quienPaga.token()))).andReturn().getResponse().getContentAsString()).get(0);
        assertThat(mio.get("pago").asBoolean()).isTrue();
        assertThat(mio.get("destinos").size()).isEqualTo(4);
        assertThat(mio.get("destinos").get(1).get("descripcion").asText()).startsWith("Pago al crédito");

        BigDecimal cajaAntes = saldoCaja();
        mvc.perform(post("/api/v1/admin/solicitudes-deposito/" + creado.get("id").asLong() + "/confirmar")
                .header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().isNoContent());

        assertThat(pagadoPrimeraLetra(uno, creditoUno)).isEqualByComparingTo("30");
        assertThat(pagadoPrimeraLetra(dos, creditoDos)).isEqualByComparingTo("50");
        assertThat(saldoAhorro(uno, ahorroUno)).isEqualByComparingTo("20");
        JsonNode calendario = opciones(tres, tres).get("meses");
        assertThat(calendario.get(0).get("obligatorioPagado").asBoolean()).isTrue();
        assertThat(calendario.get(1).get("obligatorioPagado").asBoolean()).isTrue();
        // El dinero pasó por la cuenta de cada beneficiario y salió a su destino; quien paga no gastó su saldo.
        assertThat(saldo(uno)).isEqualByComparingTo(saldoUno);
        assertThat(saldo(quienPaga)).isEqualByComparingTo("0");
        assertThat(saldoCaja().subtract(cajaAntes)).isEqualByComparingTo(total);
    }

    @Test
    void unDestinoInvalidoRechazaTodoElPago() throws Exception {
        Persona quienPaga = persona();
        hacerSocio(quienPaga);
        Persona uno = persona();
        long credito = creditoAprobado(uno);
        Persona cliente = persona();

        pagar(quienPaga, List.of(Map.of("usuarioId", uno.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", credito, "importe", 9999)),
                UUID.randomUUID().toString(), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_EXCEDIDO"));
        pagar(quienPaga, List.of(Map.of("usuarioId", cliente.usuario(), "tipo", "APORTE_PERSONAL", "importe", 10)),
                UUID.randomUUID().toString(), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NO_ES_SOCIO"));
        // El crédito de otra persona no se paga como si fuera de esta.
        pagar(quienPaga, List.of(Map.of("usuarioId", cliente.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", credito, "importe", 10)),
                UUID.randomUUID().toString(), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DESTINO_INVALIDO"));
        assertThat(jdbc.queryForObject("select count(*) from solicitudes_deposito s join personas p on p.id = s.persona_id "
                + "join usuarios u on u.id = p.usuario_id where u.correo = ?", Long.class, quienPaga.correo())).isZero();
    }

    @Test
    void laBusquedaDevuelveSoloElNombre() throws Exception {
        Persona quienPaga = persona();
        hacerSocio(quienPaga);
        Persona otra = persona();
        JsonNode lista = json.readTree(mvc.perform(get("/api/v1/pagos/personas").param("buscar", "ana prueba")
                .header(HttpHeaders.AUTHORIZATION, bearer(quienPaga.token()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(lista.size()).isPositive();
        assertThat(lista.get(0).has("identificacion")).isFalse();
        assertThat(lista.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("usuarioId", "nombre");
        assertThat(otra.usuario()).isPositive();
    }

    @Test
    void unClienteSoloPagaLoSuyo() throws Exception {
        Persona cliente = persona();
        Persona otra = persona();
        long suyo = creditoAprobado(cliente);
        long ajeno = creditoAprobado(otra);

        // No busca ni ve lo de otras personas, ni lo paga.
        mvc.perform(get("/api/v1/pagos/personas").param("buscar", "ana prueba").header(HttpHeaders.AUTHORIZATION, bearer(cliente.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SOLO_SOCIOS"));
        mvc.perform(get("/api/v1/pagos/opciones/" + otra.usuario()).header(HttpHeaders.AUTHORIZATION, bearer(cliente.token())))
                .andExpect(status().isForbidden());
        pagar(cliente, List.of(Map.of("usuarioId", otra.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", ajeno, "importe", 10)),
                UUID.randomUUID().toString(), true)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SOLO_SOCIOS"));

        // Lo suyo sí, y ve su crédito con lo que falta.
        JsonNode mias = opciones(cliente, cliente);
        assertThat(mias.get("creditos").get(0).get("id").asLong()).isEqualTo(suyo);
        assertThat(mias.get("creditos").get(0).get("totalPendiente").decimalValue()).isPositive();
        pagar(cliente, List.of(Map.of("usuarioId", cliente.usuario(), "tipo", "CUOTA_CREDITO", "referenciaId", suyo, "importe", 10)),
                UUID.randomUUID().toString(), true).andExpect(status().isCreated());
    }

    // --- Apoyo

    private ResultActions pagar(Persona p, List<Map<String, Object>> destinos, String clave, boolean conComprobante) throws Exception {
        byte[] datos = json.writeValueAsBytes(Map.of("destinos", destinos, "medio", "EFECTIVO"));
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/pagos")
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, datos));
        if (conComprobante) {
            req.file(new MockMultipartFile("archivos", "recibo.pdf", "application/pdf", PDF));
        }
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, bearer(p.token())).header("Idempotency-Key", clave));
    }

    private JsonNode opciones(Persona quien, Persona de) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/pagos/opciones/" + de.usuario()).header(HttpHeaders.AUTHORIZATION, bearer(quien.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private BigDecimal pagadoPrimeraLetra(Persona p, long credito) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/creditos/" + credito).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("cuotas").get(0).get("pagado").decimalValue();
    }

    private long creditoAprobado(Persona p) throws Exception {
        String r = mvc.perform(post("/api/v1/creditos/letras/solicitudes").header("Idempotency-Key", UUID.randomUUID().toString()).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(r).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/" + id + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        return id;
    }

    private long crearAhorro(Persona p) throws Exception {
        String r = mvc.perform(post("/api/v1/ahorros/normales").header("Idempotency-Key", UUID.randomUUID().toString()).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Para la casa\"}"))
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
