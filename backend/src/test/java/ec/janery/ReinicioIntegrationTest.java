package ec.janery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.janery.reinicio.ReinicioService;

/** TEMPORAL: el reinicio de datos. Vacía los datos financieros del esquema de pruebas, por eso recrea el contexto al final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EsqueletoIntegrationTest.EsquemaLimpio.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReinicioIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReinicioService reinicio;

    @AfterEach
    void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void borraElHistorialYCargaLosSaldosRealesConElBalanceCuadrado() throws Exception {
        String presidente = login("presidente@janery.test", "Clave-Presidente-1");
        long a = persona(presidente, true);
        long b = persona(presidente, true);
        long c = persona(presidente, false);
        // Datos con errores: un depósito y un aporte que no van.
        depositar(presidente, a, "500.00");
        mvc.perform(post("/api/v1/socios/me/aportes").header(HttpHeaders.AUTHORIZATION, "Bearer " + login(correo(a), "Clave-Segura-1"))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PERSONAL\",\"importe\":100.00}"))
                .andExpect(status().isOk());

        BigDecimal depositos = jdbc.queryForObject(
                "SELECT COALESCE(SUM(importe_reembolsable), 0) FROM depositos_apertura WHERE devuelto_en IS NULL", BigDecimal.class);
        // Lo que se debe y lo de los socios: cuentas 25, depósitos, encaje 50, ahorro letras 0,50, desgravamen 30,
        // aportes 50 + 1000 + 500, reserva 40 y ganancias 100. Los créditos suman 800 + 200 + 100.
        BigDecimal ladoDerecho = new BigDecimal("25").add(depositos).add(new BigDecimal("50.50")).add(new BigDecimal("30"))
                .add(new BigDecimal("1550")).add(new BigDecimal("140"));
        BigDecimal caja = ladoDerecho.subtract(new BigDecimal("1100"));
        ReinicioService.Plan plan = new ReinicioService.Plan(caja, new BigDecimal("40"), new BigDecimal("30"), new BigDecimal("100"),
                List.of(new ReinicioService.Cuenta(a, new BigDecimal("20")), new ReinicioService.Cuenta(c, new BigDecimal("5"))),
                List.of(new ReinicioService.Socio(a, new BigDecimal("50"), null, new BigDecimal("1000")),
                        new ReinicioService.Socio(b, null, null, new BigDecimal("500"))),
                List.of(new ReinicioService.Letras(c, "NORMAL", new BigDecimal("1000"), 10, new BigDecimal("2"), new BigDecimal("0.25"),
                        true, LocalDate.now().minusMonths(3), 2, new BigDecimal("50"), new BigDecimal("800"), new BigDecimal("0.50"))),
                List.of(new ReinicioService.Cupo(a, new BigDecimal("200"), new BigDecimal("10"), new BigDecimal("1"))),
                List.of(new ReinicioService.Emergente(c, new BigDecimal("100"), LocalDate.now(), new BigDecimal("3.5"),
                        new BigDecimal("0.25"), new BigDecimal("100"), false)),
                caja.add(new BigDecimal("1100")));

        // Con una cifra que no cuadra no queda nada hecho.
        autenticar();
        ReinicioService.Plan descuadrado = new ReinicioService.Plan(caja.add(BigDecimal.ONE), plan.reserva(), plan.fondoDesgravamen(),
                plan.gananciasPorRepartir(), plan.cuentas(), plan.socios(), plan.letras(), plan.cupos(), plan.emergentes(), null);
        assertThatThrownBy(() -> reinicio.ejecutar(actor(), descuadrado)).hasMessageContaining("no cuadra");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_cuenta", Long.class)).isPositive();

        ReinicioService.Resumen r = reinicio.ejecutar(actor(), plan);
        assertThat(r.diferencia()).isEqualByComparingTo("0");
        assertThat(r.activos()).isEqualByComparingTo(caja.add(new BigDecimal("1100")));

        // Sin historial: un solo evento, y cada saldo tiene un único registro de partida.
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria_eventos", String.class)).containsExactly("DATOS_REINICIADOS");
        assertThat(saldoCuenta(a)).isEqualByComparingTo("20");
        assertThat(saldoCuenta(b)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_cuenta", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0)
                FROM movimientos_caja m JOIN cajas k ON k.id = m.caja_id WHERE k.codigo = 'PRINCIPAL'""", BigDecimal.class))
                .isEqualByComparingTo(caja);
        assertThat(jdbc.queryForObject("SELECT SUM(saldo) FROM ahorro_letras_credito", BigDecimal.class)).isEqualByComparingTo("0.50");
        assertThat(jdbc.queryForObject("SELECT SUM(capital_pendiente) FROM creditos WHERE estado = 'ACTIVO'", BigDecimal.class))
                .isEqualByComparingTo("1100");
        assertThat(jdbc.queryForObject("SELECT MIN(id) FROM creditos", Long.class)).isEqualTo(1);

        // Reserva 40 + 10 % de 100; los 90 restantes según los aportes: 1050 y 500.
        assertThat(jdbc.queryForObject("SELECT SUM(importe) FROM movimientos_reserva", BigDecimal.class)).isEqualByComparingTo("50");
        BigDecimal gananciaA = ganancias(a);
        BigDecimal gananciaB = ganancias(b);
        assertThat(gananciaA).isEqualByComparingTo("60.96");
        assertThat(gananciaB).isEqualByComparingTo("29.03");
        assertThat(jdbc.queryForObject("SELECT SUM(importe) FROM variaciones_participacion WHERE tipo = 'APORTE_PERSONAL'", BigDecimal.class))
                .isEqualByComparingTo("1500");
    }

    // --- Apoyo

    private void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor().toString(), null,
                List.of(new SimpleGrantedAuthority("SCOPE_REGISTRAR_CREDITOS_EXISTENTES"))));
    }

    private Long actor() {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE correo = 'presidente@janery.test'", Long.class);
    }

    private BigDecimal ganancias(long usuario) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(v.importe), 0) FROM variaciones_participacion v JOIN socios s ON s.id = v.socio_id
                JOIN personas p ON p.id = s.persona_id WHERE p.usuario_id = ? AND v.tipo = 'GANANCIA'""", BigDecimal.class, usuario);
    }

    private BigDecimal saldoCuenta(long usuario) {
        return jdbc.queryForObject("""
                SELECT c.saldo_disponible FROM cuentas_principales c JOIN personas p ON p.id = c.persona_id WHERE p.usuario_id = ?""",
                BigDecimal.class, usuario);
    }

    private void depositar(String presidente, long usuario, String importe) throws Exception {
        long cuenta = jdbc.queryForObject("""
                SELECT c.id FROM cuentas_principales c JOIN personas p ON p.id = c.persona_id WHERE p.usuario_id = ?""", Long.class, usuario);
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/depositos").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importe\":" + importe + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());
    }

    private String correo(long usuarioId) {
        return jdbc.queryForObject("SELECT correo FROM usuarios WHERE id = ?", String.class, usuarioId);
    }

    private String login(String correo, String contrasena) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** Usuario con cuenta activa y, si se pide, socio. */
    private long persona(String presidente, boolean socio) throws Exception {
        String correo = "r-" + UUID.randomUUID().toString().substring(0, 12) + "@janery.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isCreated());
        String token = login(correo, "Clave-Segura-1");
        mvc.perform(post("/api/v1/personas/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombres", "Rita", "apellidos", "Reinicio",
                                "identificacion", String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L)),
                                "telefono", "0990000000", "direccion", "Quito"))))
                .andExpect(status().isCreated());
        long cuenta = json.readTree(mvc.perform(post("/api/v1/cuentas-principales/apertura").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/cuentas/" + cuenta + "/confirmar-apertura").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente))
                .andExpect(status().isOk());
        long usuario = jdbc.queryForObject("SELECT id FROM usuarios WHERE correo = ?", Long.class, correo);
        if (socio) {
            mvc.perform(post("/api/v1/admin/usuarios/" + usuario + "/socio").header(HttpHeaders.AUTHORIZATION, "Bearer " + presidente))
                    .andExpect(status().isNoContent());
        }
        return usuario;
    }
}
