package ec.janery.usuario;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.Pagina;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioAdminService service;
    private final ec.janery.socio.SocioService socios;

    public record CorreoRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 254) String correo) {
    }

    public record NombreRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String nombres,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String apellidos) {
    }

    public record TemporalResponse(String contrasenaTemporal) {
    }

    /** Los mismos datos que pide el registro, sin contraseña: se genera una temporal. */
    public record CrearRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 254) String correo,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String nombres,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String apellidos,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
                    regexp = ec.janery.persona.PersonaController.CEDULA, message = "debe tener 10 números") String identificacion,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
                    regexp = ec.janery.persona.PersonaController.TELEFONO, message = "solo admite números") String telefono,
            @jakarta.validation.constraints.Size(max = 200) String direccion) {
    }

    private final AuthService auth;
    private final AltaPersonaService altas;
    private final ec.janery.persona.PersonaService personas;

    /** Crea la cuenta de una persona con su cuenta principal activa. La contraseña temporal se muestra una sola vez. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AltaPersonaService.Alta crear(@AuthenticationPrincipal Jwt jwt,
            @jakarta.validation.Valid @RequestBody CrearRequest r) {
        return altas.crear(Long.valueOf(jwt.getSubject()), r.correo(), new ec.janery.persona.PersonaController.PerfilRequest(
                r.nombres(), r.apellidos(), r.identificacion(), r.telefono(), r.direccion()));
    }

    /** La contraseña temporal se muestra una sola vez: quien la genera se la entrega a la persona. */
    @PostMapping("/{id}/restablecer-contrasena")
    public TemporalResponse restablecer(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return new TemporalResponse(auth.restablecer(Long.valueOf(jwt.getSubject()), id));
    }

    @PutMapping("/{id}/correo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarCorreo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody CorreoRequest r) {
        auth.cambiarCorreo(Long.valueOf(jwt.getSubject()), id, r.correo());
    }

    @PutMapping("/{id}/nombre")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void corregirNombre(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody NombreRequest r) {
        personas.corregirNombre(Long.valueOf(jwt.getSubject()), id, r.nombres(), r.apellidos());
    }

    @PostMapping("/{id}/socio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarSocio(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        socios.registrar(Long.valueOf(jwt.getSubject()), id);
    }

    @GetMapping
    public Pagina<UsuarioAdminService.UsuarioResumen> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @PostMapping("/{id}/cargos/{cargo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void asignar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable String cargo) {
        service.asignarCargo(Long.valueOf(jwt.getSubject()), id, cargo);
    }

    @DeleteMapping("/{id}/cargos/{cargo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable String cargo) {
        service.revocarCargo(Long.valueOf(jwt.getSubject()), id, cargo);
    }
}
