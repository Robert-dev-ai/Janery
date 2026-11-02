package ec.janery.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.auditoria.AuditoriaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos generales de la aplicación: versión y contacto, el aviso de la cabecera y el uso de cada pantalla. */
@RestController
@RequestMapping("/api/v1")
public class AppController {

    public record InfoResponse(String version, String telefono, String whatsapp, String direccion, String simuladorUrl,
            boolean esSimulador) {
    }

    public record AnuncioResponse(String texto, Instant fecha) {
    }

    public record AnuncioRequest(@Size(max = 300) String texto) {
    }

    /** Solo la ruta de la pantalla: sin parámetros ni datos de la persona. */
    public record VisitaRequest(@NotBlank @Size(max = 120) @Pattern(regexp = "/[a-z0-9/:_-]*") String ruta) {
    }

    public record UsoPantalla(String ruta, long visitas, long personas) {
    }

    public record UsoDia(LocalDate dia, long visitas, long personas) {
    }

    public record UsoGrupo(String grupo, long visitas, long personas) {
    }

    public record UsoResponse(LocalDate desde, LocalDate hasta, long visitas, long personas, List<UsoPantalla> pantallas,
            List<UsoDia> dias, List<UsoGrupo> grupos) {
    }

    private static final ZoneId ECUADOR = ZoneId.of("America/Guayaquil");

    private final JdbcClient jdbc;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final InfoResponse info;

    AppController(JdbcClient jdbc, AuditoriaService auditoria, Clock reloj, @Value("${janery.version}") String version,
            @Value("${janery.contacto.telefono}") String telefono, @Value("${janery.contacto.direccion}") String direccion,
            @Value("${janery.simulador.url:}") String simuladorUrl, @Value("${janery.simulador.activo:false}") boolean esSimulador) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.info = new InfoResponse(version, telefono, telefono.replaceAll("\\D", ""), direccion,
                simuladorUrl.isBlank() ? null : simuladorUrl, esSimulador);
    }

    @GetMapping("/public/info")
    public InfoResponse info() {
        return info;
    }

    /** Lo que espera a alguien de la gestión. Lo que su cargo no puede atender sale en cero. */
    public record PendientesGestion(long aperturas, long cierres, long retiros, long depositos, long creditos, long cupos) {
    }

    /** Lo que atañe a la persona en su panel. */
    public record PendientesPersonales(long letrasAtrasadas, long retiros, long depositos) {
    }

    /**
     * Los números de la campana en una sola consulta: se piden con cada aviso en vivo. Estas lecturas de una sola
     * sentencia van sin transacción, que añadiría un viaje a la base para el COMMIT.
     */
    @GetMapping("/pendientes/gestion")
    public PendientesGestion pendientesGestion(org.springframework.security.core.Authentication auth) {
        boolean cuentas = tiene(auth, "GESTIONAR_CUENTAS");
        boolean creditos = tiene(auth, "APROBAR_CREDITOS");
        if (!cuentas && !creditos) {
            return new PendientesGestion(0, 0, 0, 0, 0, 0);
        }
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM cuentas_principales WHERE estado = 'PENDIENTE_PAGO'),
                       (SELECT count(*) FROM cuentas_principales WHERE estado = 'PENDIENTE_CIERRE'),
                       (SELECT count(*) FROM solicitudes_retiro WHERE estado = 'PENDIENTE'),
                       (SELECT count(*) FROM solicitudes_deposito WHERE estado = 'PENDIENTE'),
                       (SELECT count(*) FROM creditos WHERE estado = 'SOLICITADO'),
                       (SELECT count(*) FROM solicitudes_cupo WHERE estado = 'PENDIENTE')
                """).query((rs, n) -> new PendientesGestion(cuentas ? rs.getLong(1) : 0, cuentas ? rs.getLong(2) : 0,
                        cuentas ? rs.getLong(3) : 0, cuentas ? rs.getLong(4) : 0, creditos ? rs.getLong(5) : 0,
                        creditos ? rs.getLong(6) : 0)).single();
    }

    /** Una letra está atrasada como en CuotaCredito.diasAtraso: sin pagar y con la mora corriendo desde su vencimiento. */
    @GetMapping("/pendientes/me")
    public PendientesPersonales pendientesPersonales(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM cuotas_credito q JOIN creditos c ON c.id = q.credito_id
                        WHERE c.persona_id = p.id AND c.estado = 'ACTIVO' AND q.pagada_en IS NULL
                        AND COALESCE(q.mora_hasta, :hoy) > q.vencimiento),
                       (SELECT count(*) FROM solicitudes_retiro r WHERE r.persona_id = p.id AND r.estado = 'PENDIENTE'),
                       (SELECT count(*) FROM solicitudes_deposito d WHERE d.persona_id = p.id AND d.estado = 'PENDIENTE')
                FROM personas p WHERE p.usuario_id = :usuario
                """).param("hoy", LocalDate.now(reloj)).param("usuario", Long.valueOf(jwt.getSubject()))
                .query((rs, n) -> new PendientesPersonales(rs.getLong(1), rs.getLong(2), rs.getLong(3))).optional()
                .orElse(new PendientesPersonales(0, 0, 0));
    }

    /** Cuántos créditos y cuentas hay en cada estado, y los retiros y depósitos por atender. Lo no autorizado va vacío. */
    public record ConteosPanel(Map<String, Long> creditos, Map<String, Long> cuentas, long retiros, long depositos) {
    }

    /** Los totales del panel en una consulta, en vez de pedir una página de cada listado solo para leer su total. */
    @GetMapping("/admin/panel/conteos")
    public ConteosPanel conteosPanel(org.springframework.security.core.Authentication auth) {
        boolean creditos = tiene(auth, "APROBAR_CREDITOS");
        boolean cuentas = tiene(auth, "GESTIONAR_CUENTAS");
        Map<String, Long> porCredito = new java.util.HashMap<>();
        Map<String, Long> porCuenta = new java.util.HashMap<>();
        long[] solicitudes = new long[2];
        if (creditos || cuentas) {
            jdbc.sql("""
                    SELECT 'CREDITO' AS tabla, estado, count(*) AS total FROM creditos GROUP BY estado
                    UNION ALL SELECT 'CUENTA', estado, count(*) FROM cuentas_principales GROUP BY estado
                    UNION ALL SELECT 'RETIRO', estado, count(*) FROM solicitudes_retiro WHERE estado = 'PENDIENTE' GROUP BY estado
                    UNION ALL SELECT 'DEPOSITO', estado, count(*) FROM solicitudes_deposito WHERE estado = 'PENDIENTE' GROUP BY estado
                    """).query(rs -> {
                        long total = rs.getLong("total");
                        switch (rs.getString("tabla")) {
                            case "CREDITO" -> porCredito.put(rs.getString("estado"), total);
                            case "CUENTA" -> porCuenta.put(rs.getString("estado"), total);
                            case "RETIRO" -> solicitudes[0] = total;
                            default -> solicitudes[1] = total;
                        }
                    });
        }
        return new ConteosPanel(creditos ? porCredito : Map.of(), cuentas ? porCuenta : Map.of(),
                cuentas ? solicitudes[0] : 0, cuentas ? solicitudes[1] : 0);
    }

    private static boolean tiene(org.springframework.security.core.Authentication auth, String permiso) {
        return auth.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + permiso).equals(a.getAuthority()));
    }

    @GetMapping("/anuncio")
    // Una sola sentencia: sin transacción se ahorra el viaje del COMMIT.
    public AnuncioResponse anuncio() {
        return jdbc.sql("SELECT texto, creado_en FROM anuncios ORDER BY id DESC LIMIT 1")
                .query((rs, n) -> new AnuncioResponse(rs.getString(1), rs.getTimestamp(2).toInstant())).optional()
                .filter(a -> a.texto() != null && !a.texto().isBlank())
                .orElse(new AnuncioResponse(null, null));
    }

    /** Un texto vacío quita el aviso. Se guarda el historial: cada publicación es una fila nueva. */
    @PutMapping("/admin/anuncio")
    @PreAuthorize("hasAuthority('SCOPE_PUBLICAR_ANUNCIOS')")
    @Transactional
    public AnuncioResponse publicar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnuncioRequest r) {
        String texto = r.texto() == null || r.texto().isBlank() ? null : r.texto().trim();
        Long actor = Long.valueOf(jwt.getSubject());
        jdbc.sql("INSERT INTO anuncios (texto, publicado_por, creado_en) VALUES (:texto, :actor, :ahora)")
                .param("texto", texto).param("actor", actor).param("ahora", java.sql.Timestamp.from(Instant.now(reloj))).update();
        auditoria.registrar(actor, texto == null ? "ANUNCIO_QUITADO" : "ANUNCIO_PUBLICADO", "ANUNCIO", "anuncio", null, null, texto, null);
        return anuncio();
    }

    @PostMapping("/uso/visitas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void visita(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VisitaRequest r) {
        jdbc.sql("INSERT INTO visitas (usuario_id, ruta, creado_en) VALUES (:usuario, :ruta, :ahora)")
                .param("usuario", Long.valueOf(jwt.getSubject())).param("ruta", r.ruta())
                .param("ahora", java.sql.Timestamp.from(Instant.now(reloj))).update();
    }

    /** Qué pantallas se abren más y quiénes: clientes, socios o personal con cargo. */
    @GetMapping("/admin/uso")
    @PreAuthorize("hasAuthority('SCOPE_VER_USO_APP')")
    @Transactional(readOnly = true)
    public UsoResponse uso(@RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        LocalDate fin = hasta == null ? LocalDate.now(reloj.withZone(ECUADOR)) : hasta;
        LocalDate inicio = desde == null ? fin.minusDays(29) : desde;
        String filtro = " WHERE CAST(v.creado_en AT TIME ZONE 'America/Guayaquil' AS date) BETWEEN :desde AND :hasta ";
        var totales = jdbc.sql("SELECT count(*), count(DISTINCT v.usuario_id) FROM visitas v" + filtro)
                .param("desde", inicio).param("hasta", fin).query((rs, n) -> new long[] { rs.getLong(1), rs.getLong(2) }).single();
        List<UsoPantalla> pantallas = jdbc.sql("SELECT v.ruta, count(*), count(DISTINCT v.usuario_id) FROM visitas v" + filtro
                + "GROUP BY v.ruta ORDER BY 2 DESC LIMIT 40")
                .param("desde", inicio).param("hasta", fin)
                .query((rs, n) -> new UsoPantalla(rs.getString(1), rs.getLong(2), rs.getLong(3))).list();
        List<UsoDia> dias = jdbc.sql("SELECT CAST(v.creado_en AT TIME ZONE 'America/Guayaquil' AS date), count(*), count(DISTINCT v.usuario_id)"
                + " FROM visitas v" + filtro + "GROUP BY 1 ORDER BY 1")
                .param("desde", inicio).param("hasta", fin)
                .query((rs, n) -> new UsoDia(rs.getObject(1, LocalDate.class), rs.getLong(2), rs.getLong(3))).list();
        List<UsoGrupo> grupos = jdbc.sql("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM usuario_cargos uc WHERE uc.usuario_id = v.usuario_id AND uc.hasta IS NULL) THEN 'Personal con cargo'
                            WHEN EXISTS (SELECT 1 FROM socios s JOIN personas p ON p.id = s.persona_id
                                         WHERE p.usuario_id = v.usuario_id AND s.estado = 'ACTIVO') THEN 'Socios'
                            ELSE 'Clientes' END, count(*), count(DISTINCT v.usuario_id)
                FROM visitas v""" + filtro + "GROUP BY 1 ORDER BY 2 DESC")
                .param("desde", inicio).param("hasta", fin)
                .query((rs, n) -> new UsoGrupo(rs.getString(1), rs.getLong(2), rs.getLong(3))).list();
        return new UsoResponse(inicio, fin, totales[0], totales[1], pantallas, dias, grupos);
    }
}
