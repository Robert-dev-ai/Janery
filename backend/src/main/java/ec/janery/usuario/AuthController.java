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
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.NegocioException;
import ec.janery.cuenta.CuentaService;
import ec.janery.persona.PersonaService;
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

    public record LoginRequest(@NotBlank @Size(max = 254) String correo, @NotBlank @Size(max = 72) String contrasena) {
    }

    public record TokenResponse(String accessToken, Instant expiraEn) {
    }

    public record YoResponse(Long id, String correo, List<String> cargos, List<String> permisos,
            boolean tienePerfil, String estadoCuenta, boolean esSocio) {
    }

    private static final String COOKIE = "janery_refresh";
    private static final String RUTA_COOKIE = "/api/v1/auth";

    private final AuthService service;
    private final Repositorios.UsuarioRepository usuarios;
    private final PersonaService personas;
    private final CuentaService cuentas;
    private final ec.janery.socio.SocioService socios;
    private final boolean cookieSegura;

    AuthController(AuthService service, Repositorios.UsuarioRepository usuarios, PersonaService personas,
            CuentaService cuentas, ec.janery.socio.SocioService socios,
            @Value("${janery.cookie-segura}") boolean cookieSegura) {
        this.socios = socios;
        this.service = service;
        this.usuarios = usuarios;
        this.personas = personas;
        this.cuentas = cuentas;
        this.cookieSegura = cookieSegura;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registrar(@Valid @RequestBody RegistroRequest request) {
        service.registrar(request.correo(), request.contrasena());
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

    @GetMapping("/me")
    public YoResponse yo(@AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        return new YoResponse(id, usuario.getCorreo(), usuarios.cargosVigentes(id), usuarios.permisosVigentes(id),
                personas.tienePerfil(id), cuentas.estadoCuentaVigente(id).orElse(null), socios.usuarioEsSocio(id));
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
