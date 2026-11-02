package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.janery.cuenta.CuentaService;
import jakarta.servlet.http.Cookie;

/** Flujos completos contra PostgreSQL, en el esquema aislado janery_test, que se recrea en cada ejecución. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class EsqueletoIntegrationTest {

    @TestConfiguration
    static class EsquemaLimpio {
        @Bean
        FlywayMigrationStrategy recrear() {
            return (Flyway flyway) -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CuentaService cuentas;

    // --- Base

    @Test
    void healthYEstadoSonPublicos() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/estado")).andExpect(jsonPath("$.servicio").value("janery"));
    }

    @Test
    void sinElPerfilDeSimulacionNoExisteLaRuta() throws Exception {
        mvc.perform(post("/api/v1/simulacion/avanzar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsSoloAceptaOrigenesExplicitos() throws Exception {
        mvc.perform(options("/api/v1/public/estado")
                        .header(HttpHeaders.ORIGIN, "http://origen-permitido.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, containsString("origen-permitido")));
        mvc.perform(options("/api/v1/public/estado")
                        .header(HttpHeaders.ORIGIN, "http://origen-ajeno.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    // --- Autenticación

    @Test
    void rutaProtegidaSinTokenOConTokenFalsoDevuelve401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registroGuardaHashYRechazaDuplicado() throws Exception {
        String correo = correoNuevo();
        registrar(correo);
        assertThat(jdbc.queryForObject("select hash_contrasena from usuarios where correo = ?", String.class, correo))
                .startsWith("$2").doesNotContain("Clave-Segura-1");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo.toUpperCase() + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_REGISTRADO"));
    }

    @Test
    void loginFallidoSeAuditaYNoRevelaSiElCorreoExiste() throws Exception {
        String correo = correoNuevo();
        registrar(correo);
        Integer antes = jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'LOGIN_FALLIDO'", Integer.class);
        for (String c : new String[] { correo, "nadie@janery.test" }) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\":\"" + c + "\",\"contrasena\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES"));
        }
        assertThat(jdbc.queryForObject("select count(*) from auditoria_eventos where accion = 'LOGIN_FALLIDO'", Integer.class))
                .isEqualTo(antes + 2);
    }

    @Test
    void refreshRotaElTokenYLogoutLoRevoca() throws Exception {
        String correo = correoNuevo();
        registrar(correo);
        MvcResult login = loginResult(correo, "Clave-Segura-1");
        Cookie original = login.getResponse().getCookie("janery_refresh");
        assertThat(original.isHttpOnly()).isTrue();

        MvcResult refrescado = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk()).andExpect(cookie().exists("janery_refresh")).andReturn();
        // El token usado ya no sirve.
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());

        Cookie nuevo = refrescado.getResponse().getCookie("janery_refresh");
        mvc.perform(post("/api/v1/auth/logout").cookie(nuevo)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(nuevo)).andExpect(status().isUnauthorized());
    }

    // --- Cuenta principal

    @Test
    void aperturaSepara15DeIngresoY5ReembolsablesFueraDelSaldo() throws Exception {
        BigDecimal cajaAntes = saldoCaja();
        String token = personaConPerfil();
        long cuenta = solicitarApertura(token);

        mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"));
        assertThat(saldoCaja()).isEqualByComparingTo(cajaAntes);

        confirmar(cuenta);

        mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.saldoDisponible").value(0))
                .andExpect(jsonPath("$.depositoReembolsable").value(5));
        // Los USD 5 reembolsables también están en la caja, aunque sigan siendo del cliente.
        assertThat(saldoCaja()).isEqualByComparingTo(cajaAntes.add(new BigDecimal("20")));
    }

    @Test
    void noSeConfirmaDosVecesLaMismaApertura() throws Exception {
        long cuenta = solicitarApertura(personaConPerfil());
        confirmar(cuenta);
        BigDecimal caja = saldoCaja();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isConflict());
        assertThat(saldoCaja()).isEqualByComparingTo(caja);
    }

    @Test
    void aperturaExigePerfilYReferenciaEnTransferencia() throws Exception {
        String correo = correoNuevo();
        registrar(correo);
        mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(login(correo)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));
        mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(personaConPerfil()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"TRANSFERENCIA\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("REFERENCIA_REQUERIDA"));
    }

    @Test
    void depositoYRetiroActualizanLibroSaldoYCajaSinExcederse() throws Exception {
        String token = personaConPerfil();
        long cuenta = solicitarApertura(token);
        confirmar(cuenta);
        BigDecimal caja = saldoCaja();

        movimiento(cuenta, "depositos", "100.50", UUID.randomUUID().toString()).andExpect(status().isOk());
        movimiento(cuenta, "retiros", "40.25", UUID.randomUUID().toString()).andExpect(status().isOk());
        movimiento(cuenta, "retiros", "60.26", UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));

        mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.saldoDisponible").value(60.25))
                .andExpect(jsonPath("$.depositoReembolsable").value(5));
        mvc.perform(get("/api/v1/cuentas-principales/me/movimientos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.total").value(2));
        assertThat(saldoCaja()).isEqualByComparingTo(caja.add(new BigDecimal("60.25")));
        var conciliacion = cuentas.conciliar(cuenta);
        assertThat(conciliacion.get(0)).isEqualByComparingTo(conciliacion.get(1));
    }

    @Test
    void reintentoConLaMismaClaveNoDuplicaElDeposito() throws Exception {
        String token = personaConPerfil();
        long cuenta = solicitarApertura(token);
        confirmar(cuenta);
        String clave = UUID.randomUUID().toString();

        JsonNode primero = json.readTree(movimiento(cuenta, "depositos", "10.00", clave)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode segundo = json.readTree(movimiento(cuenta, "depositos", "10.00", clave)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(segundo.get("operacionId").asLong()).isEqualTo(primero.get("operacionId").asLong());
        mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.saldoDisponible").value(10));
        movimiento(cuenta, "depositos", "11.00", clave)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLAVE_REUTILIZADA"));
    }

    @Test
    void cierreExigeSaldoCeroYDevuelveElDeposito() throws Exception {
        String token = personaConPerfil();
        long cuenta = solicitarApertura(token);
        confirmar(cuenta);
        movimiento(cuenta, "depositos", "1.00", UUID.randomUUID().toString()).andExpect(status().isOk());

        mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_PENDIENTE"));

        movimiento(cuenta, "retiros", "1.00", UUID.randomUUID().toString()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-cierre")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select estado from cuentas_principales where id = ?", String.class, cuenta))
                .isEqualTo("CERRADA");
        assertThat(jdbc.queryForObject("select devuelto_en is not null from depositos_apertura where cuenta_id = ?",
                Boolean.class, cuenta)).isTrue();
    }

    @Test
    void elLibroYLaAuditoriaNoSePuedenModificar() {
        assertThatThrownBy(() -> jdbc.update("update auditoria_eventos set accion = 'x'")).hasMessageContaining("solo inserción");
        assertThatThrownBy(() -> jdbc.update("delete from auditoria_eventos")).hasMessageContaining("solo inserción");
    }

    // --- Autorización

    @Test
    void usuarioComunNoAccedeARutasAdministrativas() throws Exception {
        String token = personaConPerfil();
        String auth = bearer(token);
        mvc.perform(get("/api/v1/admin/parametros").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(get("/api/v1/admin/cuentas").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/auditoria").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/usuarios").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tesoreria").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/cuentas/1/depositos").header(HttpHeaders.AUTHORIZATION, auth)
                        .header("Idempotency-Key", "x").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":1,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaPersonaSoloVeSuPropiaCuenta() throws Exception {
        String tokenA = personaConPerfil();
        String tokenB = personaConPerfil();
        long cuentaA = solicitarApertura(tokenA);
        long cuentaB = solicitarApertura(tokenB);
        JsonNode vistaB = json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andReturn().getResponse().getContentAsString());
        assertThat(vistaB.get("id").asLong()).isEqualTo(cuentaB).isNotEqualTo(cuentaA);
    }

    @Test
    void asignarCargoConcedeSusPermisosYRevocarLosRetira() throws Exception {
        String correo = correoNuevo();
        registrar(correo);
        Long id = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, correo);
        mvc.perform(post("/api/v1/admin/usuarios/" + id + "/cargos/PRESIDENCIA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/parametros").header(HttpHeaders.AUTHORIZATION, bearer(login(correo))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/usuarios/" + id + "/cargos/PRESIDENCIA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/parametros").header(HttpHeaders.AUTHORIZATION, bearer(login(correo))))
                .andExpect(status().isForbidden());
    }

    // --- CMS de parámetros

    @Test
    void cambiarParametroCreaVersionEHistorialYProgramaVigencia() throws Exception {
        String auth = bearer(presidente());
        Long id = jdbc.queryForObject("select id from parametros_sistema where codigo = 'FONDO_RESERVA'", Long.class);
        String futuro = java.time.Instant.now().plus(java.time.Duration.ofDays(30)).toString();

        mvc.perform(put("/api/v1/admin/parametros/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":12,\"vigenteDesde\":\"" + futuro + "\",\"motivo\":\"Prueba\"}"))
                .andExpect(status().isNoContent());

        // El valor futuro no reemplaza al vigente hasta su fecha.
        mvc.perform(get("/api/v1/admin/parametros").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$[?(@.codigo == 'FONDO_RESERVA')].vigente.valor").value(10.0))
                .andExpect(jsonPath("$[?(@.codigo == 'FONDO_RESERVA')].programado.valor").value(12.0));
        mvc.perform(get("/api/v1/admin/parametros/" + id + "/historial").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$[0].valorAnterior").value(10.0))
                .andExpect(jsonPath("$[0].valorNuevo").value(12.0))
                .andExpect(jsonPath("$[0].motivo").value("Prueba"));
    }

    @Test
    void parametroRechazaValoresInvalidos() throws Exception {
        String auth = bearer(presidente());
        Long pct = jdbc.queryForObject("select id from parametros_sistema where codigo = 'EMERGENTE_SOCIO_INTERES'", Long.class);
        Long mes = jdbc.queryForObject("select id from parametros_sistema where codigo = 'ESTACIONAL_MES_NAVIDENO'", Long.class);
        for (var caso : new Object[][] { { pct, "101" }, { pct, "-1" }, { mes, "13" }, { mes, "1.5" } }) {
            mvc.perform(put("/api/v1/admin/parametros/" + caso[0]).header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\":" + caso[1] + ",\"motivo\":\"Prueba\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/admin/parametros/" + pct).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":1,\"vigenteDesde\":\"2020-01-01T00:00:00Z\",\"motivo\":\"Prueba\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VIGENCIA_RETROACTIVA"));
    }

    // --- Ayudas

    private static String correoNuevo() {
        return "persona" + SECUENCIA.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@janery.test";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void registrar(String correo) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
    }

    private MvcResult loginResult(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    private String login(String correo) throws Exception {
        return json.readTree(loginResult(correo, "Clave-Segura-1").getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String presidente() throws Exception {
        return json.readTree(loginResult("presidente@janery.test", "Clave-Presidente-1").getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String personaConPerfil() throws Exception {
        String correo = correoNuevo();
        registrar(correo);
        String token = login(correo);
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("nombres", "Ana", "apellidos", "Prueba",
                                "identificacion", UUID.randomUUID().toString().substring(0, 18),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        return token;
    }

    private long solicitarApertura(String token) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private void confirmar(long cuenta) throws Exception {
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura")
                        .header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions movimiento(long cuenta, String tipo, String importe, String clave) throws Exception {
        return mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/" + tipo)
                .header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"));
    }

    private BigDecimal saldoCaja() throws Exception {
        String respuesta = mvc.perform(get("/api/v1/tesoreria").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("saldo").decimalValue();
    }
}
