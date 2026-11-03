package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

/** Retiros, área general de socios y balance contra PostgreSQL, en el esquema janery_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
class AreaSociosContabilidadIntegrationTest {

    private static final String CLAVE = "Clave-Segura-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private record Persona(String token, long cuenta, long usuario, long personaId) {
    }

    @Autowired
    private ec.janery.socio.ParticipacionService participacion;

    /** Aquí el reparto automático va encendido, como en producción. */
    @org.junit.jupiter.api.BeforeEach
    void encenderReparto() {
        reparto(true);
    }

    @org.junit.jupiter.api.AfterEach
    void apagarReparto() {
        reparto(false);
    }

    private void reparto(boolean activo) {
        Object objetivo = org.springframework.test.util.AopTestUtils.getTargetObject(participacion);
        org.springframework.test.util.ReflectionTestUtils.setField(objetivo, "repartoAutomatico", activo);
    }

    @Test
    void cadaIngresoSeReparteAlMomentoYLosGastosBajanLosFondos() throws Exception {
        jdbc.update("update socios set estado = 'INACTIVO'");
        Persona cliente = persona("500.00");
        Persona a = persona("200.00");
        Persona b = persona("400.00");
        for (Persona s : new Persona[] { a, b }) {
            mvc.perform(post("/api/v1/admin/usuarios/" + s.usuario() + "/socio").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                    .andExpect(status().isNoContent());
        }
        conClave(bearer(a.token()), "/api/v1/socios/me/aportes", "{\"tipo\":\"PERSONAL\",\"importe\":100.00}").andExpect(status().isOk());
        conClave(bearer(b.token()), "/api/v1/socios/me/aportes", "{\"tipo\":\"PERSONAL\",\"importe\":300.00}").andExpect(status().isOk());
        BigDecimal reservaAntes = reserva();

        // Suministros al aprobar e interés de una letra: el 10 % a reservas y el resto 1:3, según los fondos.
        long credito = json.readTree(mvc.perform(post("/api/v1/creditos/letras/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer(cliente.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modalidad\":\"NORMAL\",\"importe\":3000.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/" + credito + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().is2xxSuccessful());
        conClave(bearer(cliente.token()), "/api/v1/creditos/" + credito + "/pagos", "{}").andExpect(status().isOk());
        BigDecimal interes = jdbc.queryForObject("select interes from cuotas_credito where credito_id = ? and numero = 1", BigDecimal.class, credito);
        BigDecimal suministros = jdbc.queryForObject("select importe from suministros_credito where credito_id = ?", BigDecimal.class, credito);
        assertThat(suministros).isEqualByComparingTo("45");
        BigDecimal aReserva = interes.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP)
                .add(suministros.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP));
        BigDecimal aSocios = interes.add(suministros).subtract(aReserva);

        assertThat(reserva().subtract(reservaAntes)).isEqualByComparingTo(aReserva);
        JsonNode ra = resumen(a);
        JsonNode rb = resumen(b);
        // Los centavos que no se dividen exacto esperan al siguiente reparto, sin perderse.
        BigDecimal enEspera = json.readTree(mvc.perform(get("/api/v1/admin/resultados/en-espera").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("ganancia").decimalValue();
        assertThat(ra.get("ganancias").decimalValue().add(rb.get("ganancias").decimalValue()).add(enEspera)).isEqualByComparingTo(aSocios);
        assertThat(rb.get("ganancias").decimalValue().doubleValue()).isCloseTo(ra.get("ganancias").decimalValue().doubleValue() * 3, org.assertj.core.data.Offset.offset(0.05));
        // Se ve al momento y sube el rendimiento, pero no se puede retirar hasta el año siguiente.
        assertThat(ra.get("rendimientoMes").decimalValue().signum()).isPositive();
        assertThat(ra.get("gananciasDisponibles").decimalValue()).isEqualByComparingTo("0");

        // Abrir una cuenta también es ganancia; dentro del mes, la proporción no cambia por lo ganado.
        BigDecimal gananciaA = ra.get("ganancias").decimalValue();
        persona("1.00");
        assertThat(resumen(a).get("ganancias").decimalValue().subtract(gananciaA)).isEqualByComparingTo("3.37");

        // Un gasto baja los fondos, sin tocar la reserva; su reverso los devuelve.
        BigDecimal reservaAhora = reserva();
        BigDecimal fondoA = resumen(a).get("valorActual").decimalValue();
        long gasto = asiento("{\"linea\":\"5.4\",\"importe\":40.00,\"descripcion\":\"Sueldo\"}").andExpect(status().isCreated()).andReturn2().get("id").asLong();
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(fondoA.subtract(new BigDecimal("10")));
        assertThat(reserva()).isEqualByComparingTo(reservaAhora);
        mvc.perform(post("/api/v1/admin/contabilidad/asientos/" + gasto + "/reverso").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error\"}")).andExpect(status().isCreated());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(fondoA);

        // Un ingreso manual se reparte; su reverso devuelve también la reserva.
        long multa = asiento("{\"linea\":\"4.3\",\"importe\":20.00,\"descripcion\":\"Multa\"}").andExpect(status().isCreated()).andReturn2().get("id").asLong();
        assertThat(reserva()).isEqualByComparingTo(reservaAhora.add(new BigDecimal("2")));
        mvc.perform(post("/api/v1/admin/contabilidad/asientos/" + multa + "/reverso").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error\"}")).andExpect(status().isCreated());
        assertThat(reserva()).isEqualByComparingTo(reservaAhora);
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(fondoA);
        // Comprar un bien no es gasto: el fondo no baja.
        asiento("{\"linea\":\"1.7\",\"importe\":200.00,\"descripcion\":\"Computadora\"}").andExpect(status().isCreated());
        assertThat(resumen(a).get("valorActual").decimalValue()).isEqualByComparingTo(fondoA);
    }

    @Test
    void elEstadoDeCajaTraeDoceMesesYLasTresCajas() throws Exception {
        JsonNode estado = json.readTree(mvc.perform(get("/api/v1/tesoreria/estado").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(estado.get("caja").get("meses")).hasSize(12);
        assertThat(estado.get("caja").get("resultados")).hasSize(12);
        assertThat(estado.get("caja").get("saldos")).hasSize(3);
        assertThat(estado.get("enEspera").has("ganancia")).isTrue();
        // Sin permiso de caja, no se consulta.
        mvc.perform(get("/api/v1/tesoreria/estado").header(HttpHeaders.AUTHORIZATION, bearer(persona("100.00").token())))
                .andExpect(status().isForbidden());
    }

    private JsonNode resumen(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/socios/me").header(HttpHeaders.AUTHORIZATION, bearer(login(correoDe(p)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private BigDecimal reserva() {
        return jdbc.queryForObject("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from movimientos_caja m join cajas c on c.id = m.caja_id where c.codigo = 'FONDO_RESERVA'
                """, BigDecimal.class);
    }

    @Test
    void retiroApartaElSaldoYLaCajaSoloCambiaAlEntregar() throws Exception {
        Persona p = persona("100.00");
        BigDecimal caja = saldoCaja();

        JsonNode solicitud = solicitar(p, "{\"importe\":40.00,\"medio\":\"EFECTIVO\"}").andExpect(status().isCreated()).andReturn2();
        assertThat(saldo(p)).isEqualByComparingTo("60");
        assertThat(saldoCaja()).isEqualByComparingTo(caja);

        entregar(solicitud.get("id").asLong()).andExpect(status().isNoContent());
        assertThat(saldo(p)).isEqualByComparingTo("60");
        assertThat(saldoCaja()).isEqualByComparingTo(caja.subtract(new BigDecimal("40")));
        entregar(solicitud.get("id").asLong()).andExpect(status().isConflict());
    }

    @Test
    void rechazarYCancelarDevuelvenLoApartado() throws Exception {
        Persona p = persona("100.00");
        long a = solicitar(p, "{\"importe\":30.00,\"medio\":\"EFECTIVO\"}").andReturn2().get("id").asLong();
        mvc.perform(post("/api/v1/admin/solicitudes-retiro/" + a + "/rechazar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Datos incorrectos\"}")).andExpect(status().isNoContent());
        assertThat(saldo(p)).isEqualByComparingTo("100");

        long b = solicitar(p, "{\"importe\":30.00,\"medio\":\"EFECTIVO\"}").andReturn2().get("id").asLong();
        mvc.perform(post("/api/v1/cuentas-principales/me/retiros/" + b + "/cancelar").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isNoContent());
        assertThat(saldo(p)).isEqualByComparingTo("100");
    }

    @Test
    void retiroValidaBancoSaldoYNoSeDuplica() throws Exception {
        Persona p = persona("50.00");
        solicitar(p, "{\"importe\":10.00,\"medio\":\"TRANSFERENCIA\"}").resultado().andExpect(status().isBadRequest());
        solicitar(p, "{\"importe\":99.00,\"medio\":\"EFECTIVO\"}").resultado().andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));

        String clave = UUID.randomUUID().toString();
        String banco = "{\"importe\":10.00,\"medio\":\"TRANSFERENCIA\",\"banco\":\"Pichincha\",\"tipoCuenta\":\"AHORROS\",\"numeroCuenta\":\"123\",\"titularCuenta\":\"Ana Paz\"}";
        solicitarCon(p, banco, clave).andExpect(status().isCreated()).andExpect(jsonPath("$.banco.banco").value("Pichincha"));
        solicitarCon(p, banco, clave).andExpect(status().isCreated());
        assertThat(saldo(p)).isEqualByComparingTo("40");

        // A otro banco o cooperativa se aparta también el costo de la transferencia; cancelar lo devuelve todo.
        String otro = banco.replace("Pichincha", "Cooperativa Andina");
        long id = solicitar(p, otro).andExpect(status().isCreated()).andExpect(jsonPath("$.costo").value(0.41)).andReturn2().get("id").asLong();
        assertThat(saldo(p)).isEqualByComparingTo("29.59");
        mvc.perform(post("/api/v1/cuentas-principales/me/retiros/" + id + "/cancelar").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isNoContent());
        assertThat(saldo(p)).isEqualByComparingTo("40");
    }

    @Test
    void depositoSoloCambiaSaldoYCajaAlConfirmarlo() throws Exception {
        Persona p = persona("10.00");
        BigDecimal caja = saldoCaja();
        long a = deposito(p, "{\"importe\":25.00,\"medio\":\"EFECTIVO\"}").andExpect(status().isCreated()).andReturn2().get("id").asLong();
        assertThat(saldo(p)).isEqualByComparingTo("10");
        assertThat(saldoCaja()).isEqualByComparingTo(caja);

        confirmarDeposito(a).andExpect(status().isNoContent());
        assertThat(saldo(p)).isEqualByComparingTo("35");
        assertThat(saldoCaja()).isEqualByComparingTo(caja.add(new BigDecimal("25")));
        confirmarDeposito(a).andExpect(status().isConflict());
        assertThat(saldo(p)).isEqualByComparingTo("35");

        deposito(p, "{\"importe\":5.00,\"medio\":\"TRANSFERENCIA\"}").resultado().andExpect(status().isBadRequest());
        long b = deposito(p, "{\"importe\":5.00,\"medio\":\"TRANSFERENCIA\",\"referencia\":\"ABC\"}").andExpect(status().isCreated()).andReturn2().get("id").asLong();
        mvc.perform(post("/api/v1/admin/solicitudes-deposito/" + b + "/rechazar").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/solicitudes-deposito/" + b + "/rechazar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"No llegó\"}")).andExpect(status().isNoContent());
        assertThat(saldo(p)).isEqualByComparingTo("35");
    }

    @Test
    void noSePuedeCerrarLaCuentaConUnDepositoPendiente() throws Exception {
        Persona p = persona("0.01");
        solicitar(p, "{\"importe\":0.01,\"medio\":\"EFECTIVO\"}").andExpect(status().isCreated());
        Persona q = persona("0.01");
        entregar(solicitar(q, "{\"importe\":0.01,\"medio\":\"EFECTIVO\"}").andReturn2().get("id").asLong()).andExpect(status().isNoContent());
        deposito(q, "{\"importe\":5.00,\"medio\":\"EFECTIVO\"}").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(q.token())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DEPOSITO_PENDIENTE"));
    }

    @Test
    void noSePuedeCerrarLaCuentaConUnRetiroPendiente() throws Exception {
        Persona p = persona("20.00");
        solicitar(p, "{\"importe\":20.00,\"medio\":\"EFECTIVO\"}").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/cuentas-principales/me/cierre").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RETIRO_PENDIENTE"));
    }

    @Test
    void areaGeneralSoloParaSociosYSinDatosDeContacto() throws Exception {
        Persona noSocio = persona("10.00");
        Persona socio = persona("10.00");
        mvc.perform(post("/api/v1/admin/usuarios/" + socio.usuario() + "/socio").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isNoContent());
        String socioAuth = bearer(login(correoDe(socio)));
        String otroAuth = bearer(noSocio.token());

        for (String ruta : new String[] { "resumen", "personas", "movimientos", "personas/" + noSocio.personaId() }) {
            mvc.perform(get("/api/v1/socios/general/" + ruta).header(HttpHeaders.AUTHORIZATION, otroAuth))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SOLO_SOCIOS"));
            mvc.perform(get("/api/v1/socios/general/" + ruta).header(HttpHeaders.AUTHORIZATION, socioAuth)).andExpect(status().isOk());
        }
        String cuerpo = mvc.perform(get("/api/v1/socios/general/personas/" + noSocio.personaId()).header(HttpHeaders.AUTHORIZATION, socioAuth))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("identificacion", "telefono", "direccion", "correo");
    }

    @Test
    void personasEHistorialExigenSuPermiso() throws Exception {
        Persona p = persona("1.00");
        mvc.perform(get("/api/v1/admin/personas").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/historial-crediticio").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/contabilidad/estado").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/personas").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/historial-crediticio").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().isOk());
    }

    @Test
    void registroCompletoCreaLaPersonaYPermiteEntrarConLaCedula() throws Exception {
        String correo = "c" + UUID.randomUUID() + "@janery.test";
        String cedula = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        String cuerpo = "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\",\"nombres\":\"Ana\",\"apellidos\":\"Paz\",\"identificacion\":\"" + cedula + "\",\"telefono\":\"099\"}";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());

        String token = loginCon(cedula, CLAVE);
        mvc.perform(get("/api/v1/personas/me").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Ana")).andExpect(jsonPath("$.datosCreditoCompletos").value(false));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + cedula + "\",\"contrasena\":\"Otra-Clave-9\"}")).andExpect(status().isUnauthorized());

        // Si la identificación ya existe no queda un usuario suelto.
        String otro = "c" + UUID.randomUUID() + "@janery.test";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo.replace(correo, otro)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("IDENTIFICACION_REGISTRADA"));
        assertThat(jdbc.queryForObject("select count(*) from usuarios where correo = ?", Long.class, otro)).isZero();
    }

    /** El invariante de todo: tras operar con todos los productos, el balance cuadra y el debe iguala al haber. */
    @Test
    void elBalanceCuadraTrasOperarConTodosLosProductos() throws Exception {
        // Otras pruebas vacían tablas de socios en este mismo esquema: se compara contra el punto de partida.
        JsonNode inicio = estado();
        JsonNode diarioInicio = diario();
        Persona p = persona("500.00");
        String auth = bearer(p.token());
        // Ahorro normal y plazo fijo.
        JsonNode ahorro = json.readTree(mvc.perform(post("/api/v1/ahorros/normales").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Meta\"}")).andReturn().getResponse().getContentAsString());
        conClave(auth, "/api/v1/ahorros/" + ahorro.get("id").asLong() + "/depositos", "{\"importe\":50.00}").andExpect(status().isOk());
        conClave(auth, "/api/v1/ahorros/plazo-fijo", "{\"nombre\":\"Fijo\",\"importe\":40.00,\"plazoMeses\":6}").andExpect(status().is2xxSuccessful());
        // Crédito a letras aprobado y una letra pagada.
        long credito = json.readTree(mvc.perform(post("/api/v1/creditos/letras/solicitudes").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":6,\"origenEncaje\":\"CREDITO\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/" + credito + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().is2xxSuccessful());
        conClave(auth, "/api/v1/creditos/" + credito + "/pagos", "{}").andExpect(status().isOk());
        // Pago adelantado y precancelación de otro crédito: deben dejar el balance cuadrado.
        long otro = json.readTree(mvc.perform(post("/api/v1/creditos/letras/solicitudes").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"modalidad\":\"NORMAL\",\"importe\":300.00,\"plazoMeses\":3,\"origenEncaje\":\"CREDITO\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/creditos/" + otro + "/aprobar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))).andExpect(status().is2xxSuccessful());
        // Dos letras de 300 a 3 meses, con su ahorro letras mínimo de 0,50 cada una, caben en 220.
        conClave(auth, "/api/v1/creditos/" + otro + "/pagos", "{\"importe\":220.00}").andExpect(status().isOk());
        conClave(auth, "/api/v1/creditos/" + otro + "/precancelacion", "{}").andExpect(status().isOk());
        // Retiro apartado sin entregar, otro entregado, y registro manual.
        solicitar(p, "{\"importe\":10.00,\"medio\":\"EFECTIVO\"}").andExpect(status().isCreated());
        entregar(solicitar(p, "{\"importe\":5.00,\"medio\":\"EFECTIVO\"}").andReturn2().get("id").asLong()).andExpect(status().isNoContent());
        confirmarDeposito(deposito(p, "{\"importe\":15.00,\"medio\":\"EFECTIVO\"}").andReturn2().get("id").asLong()).andExpect(status().isNoContent());
        long gasto = asiento("{\"linea\":\"5.4\",\"importe\":10.00,\"descripcion\":\"Sueldo\"}").andExpect(status().isCreated()).andReturn2().get("id").asLong();
        asiento("{\"linea\":\"1.7\",\"importe\":7.00,\"descripcion\":\"Silla\"}").andExpect(status().isCreated());
        asiento("{\"linea\":\"5.9\",\"importe\":3.00,\"descripcion\":\"Provisión\"}").andExpect(status().isCreated());
        asiento("{\"linea\":\"3.4\",\"importe\":2.00,\"descripcion\":\"Donación\"}").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/contabilidad/asientos/" + gasto + "/reverso").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/contabilidad/asientos/" + gasto + "/reverso").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Otra vez\"}")).andExpect(status().isConflict());
        asiento("{\"linea\":\"1.1\",\"importe\":1.00,\"descripcion\":\"No\"}").resultado().andExpect(status().isBadRequest());

        JsonNode estado = estado();
        assertThat(estado.get("diferencia").decimalValue()).isEqualByComparingTo(inicio.get("diferencia").decimalValue());
        assertThat(linea(estado.get("activoCorriente"), "1.99")).isEqualByComparingTo(linea(inicio.get("activoCorriente"), "1.99").subtract(new BigDecimal("3")));
        assertThat(linea(estado.get("ganancias"), "4.1").signum()).isPositive();
        assertThat(linea(estado.get("pasivos"), "2.2")).isEqualByComparingTo(linea(inicio.get("pasivos"), "2.2").add(new BigDecimal("40")));
        assertThat(linea(estado.get("activosFijos"), "1.7")).isEqualByComparingTo(linea(inicio.get("activosFijos"), "1.7").add(new BigDecimal("7")));

        JsonNode diario = diario();
        assertThat(diario.get("totalDebe").decimalValue().subtract(diarioInicio.get("totalDebe").decimalValue()))
                .isEqualByComparingTo(diario.get("totalHaber").decimalValue().subtract(diarioInicio.get("totalHaber").decimalValue()));

        mvc.perform(get("/api/v1/admin/contabilidad/mayor?linea=1.1").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Caja (dinero en efectivo)"));
        mvc.perform(get("/api/v1/admin/contabilidad/estado?periodo=2999-01").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isBadRequest());
    }

    // --- Apoyo

    private BigDecimal linea(JsonNode seccion, String codigo) {
        for (JsonNode l : seccion.get("lineas")) {
            if (codigo.equals(l.get("codigo").asText())) {
                return l.get("valor").decimalValue();
            }
        }
        throw new AssertionError("Sin línea " + codigo);
    }

    private JsonNode estado() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/admin/contabilidad/estado").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode diario() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/admin/contabilidad/diario").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Pedido asiento(String cuerpo) throws Exception {
        return new Pedido(conClave(bearer(presidente()), "/api/v1/admin/contabilidad/asientos", cuerpo));
    }

    private Pedido solicitar(Persona p, String cuerpo) throws Exception {
        return new Pedido(solicitarCon(p, cuerpo, UUID.randomUUID().toString()));
    }

    private ResultActions solicitarCon(Persona p, String cuerpo, String clave) throws Exception {
        return mvc.perform(post("/api/v1/cuentas-principales/me/retiros").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Pedido deposito(Persona p, String cuerpo) throws Exception {
        return new Pedido(mvc.perform(post("/api/v1/cuentas-principales/me/depositos").header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
    }

    private ResultActions confirmarDeposito(long id) throws Exception {
        return mvc.perform(post("/api/v1/admin/solicitudes-deposito/" + id + "/confirmar").header(HttpHeaders.AUTHORIZATION, bearer(presidente())));
    }

    private ResultActions entregar(long id) throws Exception {
        return mvc.perform(post("/api/v1/admin/solicitudes-retiro/" + id + "/entregar").header(HttpHeaders.AUTHORIZATION, bearer(presidente()))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions conClave(String auth, String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, auth).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private BigDecimal saldo(Persona p) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/cuentas-principales/me").header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andReturn().getResponse().getContentAsString()).get("saldoDisponible").decimalValue();
    }

    private BigDecimal saldoCaja() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/tesoreria").header(HttpHeaders.AUTHORIZATION, bearer(presidente())))
                .andReturn().getResponse().getContentAsString()).get("saldo").decimalValue();
    }

    private String correoDe(Persona p) {
        return jdbc.queryForObject("select correo from usuarios where id = ?", String.class, p.usuario());
    }

    private String presidente() throws Exception {
        return loginCon("presidente@janery.test", "Clave-Presidente-1");
    }

    private String login(String correo) throws Exception {
        return loginCon(correo, CLAVE);
    }

    private String loginCon(String correo, String clave) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Persona persona(String saldoInicial) throws Exception {
        String correo = "p" + UUID.randomUUID() + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().is2xxSuccessful());
        String token = login(correo);
        String auth = bearer(token);
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombres\":\"Ana\",\"apellidos\":\"Paz\",\"identificacion\":\"" + String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L))
                        + "\",\"telefono\":\"099\",\"direccion\":\"Quito\"}")).andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/personas/me/datos-credito").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lugarTrabajo\":\"Tienda\",\"ocupacion\":\"Vendedora\",\"antiguedadTrabajo\":\"2 años\",\"ingresosMensuales\":500.00,\"fechaNacimiento\":\"1990-01-01\",\"estadoCivil\":\"SOLTERO\",\"referenciaNombre\":\"Luis\",\"referenciaTelefono\":\"099\",\"banco\":\"Pichincha\",\"tipoCuentaBanco\":\"AHORROS\",\"numeroCuentaBanco\":\"123\"}"))
                .andExpect(status().isOk());
        long cuenta = json.readTree(mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String admin = bearer(presidente());
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        conClave(admin, "/api/v1/admin/cuentas/" + cuenta + "/depositos", "{\"importe\":" + saldoInicial + ",\"metodoPago\":\"EFECTIVO\"}")
                .andExpect(status().isOk());
        long usuario = jdbc.queryForObject("select id from usuarios where correo = ?", Long.class, correo);
        long personaId = jdbc.queryForObject("select id from personas where usuario_id = ?", Long.class, usuario);
        return new Persona(token, cuenta, usuario, personaId);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Respuesta que aún se puede comprobar y leer como JSON. */
    private final class Pedido {
        private final ResultActions accion;

        Pedido(ResultActions accion) {
            this.accion = accion;
        }

        ResultActions resultado() {
            return accion;
        }

        Pedido andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            accion.andExpect(m);
            return this;
        }

        JsonNode andReturn2() throws Exception {
            return json.readTree(accion.andReturn().getResponse().getContentAsString());
        }
    }
}
