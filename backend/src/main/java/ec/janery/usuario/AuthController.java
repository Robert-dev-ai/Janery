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
    private final Repositorios.UsuarioRepository usuarios;
    private final PersonaService personas;
    private final CuentaService cuentas;
    private final ec.janery.socio.SocioService socios;
    private final FotoService fotos;
    private final boolean cookieSegura;

    AuthController(AuthService service, Repositorios.UsuarioRepository usuarios, PersonaService personas,
            CuentaService cuentas, ec.janery.socio.SocioService socios, FotoService fotos,
            @Value("${janery.cookie-segura}") boolean cookieSegura) {
        this.socios = socios;
        this.fotos = fotos;
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
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        // Primer nombre, con mayúscula inicial aunque se haya registrado todo en mayúsculas.
        String nombre = personas.dePorUsuario(id).map(p -> p.getNombres().trim().split("\\s+")[0])
                .map(n -> n.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + n.substring(1).toLowerCase(java.util.Locale.ROOT))
                .orElse(null);
        return new YoResponse(id, usuario.getCorreo(), usuarios.cargosVigentes(id), usuarios.permisosVigentes(id),
                personas.tienePerfil(id), cuentas.estadoCuentaVigente(id).orElse(null), socios.usuarioEsSocio(id),
                nombre, fotos.tiene(id), usuario.isDebeCambiarContrasena());
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
