package ec.janery.usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegistroRequest(@NotBlank @Email @Size(max = 254) String correo,
            // BCrypt solo considera 72 bytes.
            @NotBlank @Size(min = 8, max = 72) String contrasena) {
    }

    public record RegistroCompletoRequest(@NotBlank @Email @Size(max = 254) String correo,
            @NotBlank @Size(min = 8, max = 72) String contrasena, @NotBlank @Size(max = 100) String nombres,
            @NotBlank @Size(max = 100) String apellidos,
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = ec.janery.persona.PersonaController.CEDULA,
                    message = "debe tener 10 números") String identificacion,
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = ec.janery.persona.PersonaController.TELEFONO,
                    message = "solo admite números") String telefono) {
    }

    /** El campo correo admite también la cédula o identificación. */
    public record LoginRequest(@NotBlank @Size(max = 254) String correo, @NotBlank @Size(max = 72) String contrasena) {
    }

    public record CambioContrasenaRequest(@NotBlank @Size(max = 72) String actual,
            @NotBlank @Size(min = 8, max = 72) String nueva) {
    }

    public record CambioCorreoRequest(@NotBlank @Email @Size(max = 254) String correo,
            @NotBlank @Size(max = 72) String contrasena) {
    }

    public record TokenResponse(String accessToken, Instant expiraEn) {
    }

    public record YoResponse(Long id, String correo, List<String> cargos, List<String> permisos,
            boolean tienePerfil, String estadoCuenta, boolean esSocio, String nombre, boolean tieneFoto,
            boolean debeCambiarContrasena) {
    }

    public record RecuperarRequest(@NotBlank @Size(max = 254) String identificador) {
    }

    private static final String COOKIE = "janery_refresh";
    private static final String RUTA_COOKIE = "/api/v1/auth";

    private final AuthService service;
    private final FotoService fotos;
    private final JdbcClient jdbc;
    private final boolean cookieSegura;

    AuthController(AuthService service, FotoService fotos, JdbcClient jdbc,
            @Value("${janery.cookie-segura}") boolean cookieSegura) {
        this.service = service;
        this.fotos = fotos;
        this.jdbc = jdbc;
        this.cookieSegura = cookieSegura;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registrar(@Valid @RequestBody RegistroRequest request) {
        service.registrar(request.correo(), request.contrasena());
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public void registrarCompleto(@Valid @RequestBody RegistroCompletoRequest r) {
        service.registrarConPerfil(r.correo(), r.contrasena(), new ec.janery.persona.PersonaController.PerfilRequest(
                r.nombres(), r.apellidos(), r.identificacion(), r.telefono(), null));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respuesta(service.iniciarSesion(request.correo(), request.contrasena()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refrescar(@CookieValue(name = COOKIE, required = false) String refresh) {
        return respuesta(service.refrescar(refresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String refresh) {
        service.cerrarSesion(refresh);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarContrasena(@AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = COOKIE, required = false) String refresh,
            @Valid @RequestBody CambioContrasenaRequest request) {
        service.cambiarContrasena(Long.valueOf(jwt.getSubject()), request.actual(), request.nueva(), refresh);
    }

    @PutMapping("/correo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarCorreo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CambioCorreoRequest request) {
        service.cambiarMiCorreo(Long.valueOf(jwt.getSubject()), request.correo(), request.contrasena());
    }

    @PostMapping("/sesiones/cerrar-otras")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarOtrasSesiones(@AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = COOKIE, required = false) String refresh) {
        service.cerrarOtrasSesiones(Long.valueOf(jwt.getSubject()), refresh);
    }

    @PostMapping("/recuperar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recuperar(@Valid @RequestBody RecuperarRequest r) {
        service.solicitarRestablecimiento(r.identificador());
    }

    @GetMapping("/me")
    public YoResponse yo(@AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        // Una sola consulta: se pide al entrar y con cada aviso en vivo, y cada viaje a la base cuesta.
        return jdbc.sql("""
                SELECT u.correo, u.debe_cambiar_contrasena, u.foto IS NOT NULL AS tiene_foto, p.id AS persona_id, p.nombres,
                       (SELECT c.estado FROM cuentas_principales c
                        WHERE c.persona_id = p.id AND c.estado NOT IN ('CERRADA', 'RECHAZADA') LIMIT 1) AS estado_cuenta,
                       EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO') AS es_socio,
                       ARRAY(SELECT c.codigo FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                             WHERE uc.usuario_id = u.id AND uc.hasta IS NULL ORDER BY c.codigo) AS cargos,
                       ARRAY(SELECT DISTINCT pe.codigo FROM usuario_cargos uc
                             JOIN cargo_permisos cp ON cp.cargo_id = uc.cargo_id JOIN permisos pe ON pe.id = cp.permiso_id
                             WHERE uc.usuario_id = u.id AND uc.hasta IS NULL ORDER BY pe.codigo) AS permisos
                FROM usuarios u LEFT JOIN personas p ON p.usuario_id = u.id WHERE u.id = :id
                """).param("id", id).query((rs, n) -> new YoResponse(id, rs.getString("correo"),
                        List.of((String[]) rs.getArray("cargos").getArray()),
                        List.of((String[]) rs.getArray("permisos").getArray()),
                        rs.getObject("persona_id") != null, rs.getString("estado_cuenta"), rs.getBoolean("es_socio"),
                        primerNombre(rs.getString("nombres")), rs.getBoolean("tiene_foto"), rs.getBoolean("debe_cambiar_contrasena")))
                .optional().orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
    }

    /** Primer nombre, con mayúscula inicial aunque se haya registrado todo en mayúsculas. */
    private static String primerNombre(String nombres) {
        if (nombres == null) {
            return null;
        }
        String n = nombres.trim().split("\\s+")[0];
        return n.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + n.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    @GetMapping("/foto")
    public ResponseEntity<byte[]> foto(@AuthenticationPrincipal Jwt jwt) {
        return fotos.de(Long.valueOf(jwt.getSubject()))
                .map(f -> ResponseEntity.ok().contentType(MediaType.parseMediaType(f.tipo()))
                        .header(HttpHeaders.CACHE_CONTROL, "no-store").body(f.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarFoto(@AuthenticationPrincipal Jwt jwt, @RequestParam("archivo") MultipartFile archivo) {
        fotos.guardar(Long.valueOf(jwt.getSubject()), archivo);
    }

    @DeleteMapping("/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarFoto(@AuthenticationPrincipal Jwt jwt) {
        fotos.quitar(Long.valueOf(jwt.getSubject()));
    }

    private ResponseEntity<TokenResponse> respuesta(AuthService.Sesion sesion) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(sesion.refreshToken(), sesion.duracionRefresh()).toString())
                .body(new TokenResponse(sesion.accessToken(), sesion.expiraEn()));
    }

    private ResponseCookie cookie(String valor, Duration duracion) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(cookieSegura)
                .sameSite("Strict")
                .path(RUTA_COOKIE)
                .maxAge(duracion)
                .build();
    }
}
