package ec.janery.persona;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/personas")
@RequiredArgsConstructor
public class PersonaController {

    public record PerfilRequest(@NotBlank @Size(max = 100) String nombres, @NotBlank @Size(max = 100) String apellidos,
            @NotBlank @Size(max = 20) String identificacion, @NotBlank @Size(max = 20) String telefono,
            @NotBlank @Size(max = 200) String direccion) {
    }

    public record PerfilResponse(String nombres, String apellidos, String identificacion, String telefono,
            String direccion) {
        static PerfilResponse de(Persona p) {
            return new PerfilResponse(p.getNombres(), p.getApellidos(), p.getIdentificacion(), p.getTelefono(),
                    p.getDireccion());
        }
    }

    private final PersonaService service;

    @PostMapping("/perfil")
    @ResponseStatus(HttpStatus.CREATED)
    public PerfilResponse completar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PerfilRequest request) {
        return PerfilResponse.de(service.completarPerfil(Long.valueOf(jwt.getSubject()), request));
    }

    @GetMapping("/me")
    public PerfilResponse yo(@AuthenticationPrincipal Jwt jwt) {
        return service.dePorUsuario(Long.valueOf(jwt.getSubject())).map(PerfilResponse::de)
                .orElseThrow(() -> NegocioException.noEncontrado("Perfil no completado"));
    }
}
