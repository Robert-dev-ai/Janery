package ec.janery.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

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

    @GetMapping("/anuncio")
    @Transactional(readOnly = true)
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
