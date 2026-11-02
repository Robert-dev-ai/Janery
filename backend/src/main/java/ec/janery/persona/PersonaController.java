package ec.janery.persona;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/personas")
@RequiredArgsConstructor
public class PersonaController {

    /** Cédula ecuatoriana: diez números. */
    public static final String CEDULA = "\\d{10}";
    /** Con prefijo de país opcional; los espacios de los extremos se recortan al guardar. */
    public static final String TELEFONO = "\\s*\\+?[0-9 ]{3,20}\\s*";

    public record PerfilRequest(@NotBlank @Size(max = 100) String nombres, @NotBlank @Size(max = 100) String apellidos,
            @NotBlank @Pattern(regexp = CEDULA, message = "debe tener 10 números") String identificacion,
            @NotBlank @Pattern(regexp = TELEFONO, message = "solo admite números") String telefono,
            @Size(max = 200) String direccion) {
    }

    public record DatosCreditoRequest(@NotBlank @Size(max = 150) String lugarTrabajo, @NotBlank @Size(max = 100) String ocupacion,
            @NotBlank @Size(max = 50) String antiguedadTrabajo,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal ingresosMensuales,
            @NotNull @Past LocalDate fechaNacimiento,
            @NotNull @Pattern(regexp = "SOLTERO|CASADO|UNION_LIBRE|DIVORCIADO|VIUDO") String estadoCivil,
            @NotBlank @Size(max = 150) String referenciaNombre, @NotBlank @Size(max = 20) String referenciaTelefono,
            @NotBlank @Size(max = 100) String banco, @NotNull @Pattern(regexp = "AHORROS|CORRIENTE") String tipoCuentaBanco,
            @NotBlank @Size(max = 40) String numeroCuentaBanco) {
    }

    public record ContactoRequest(@NotBlank @Pattern(regexp = TELEFONO, message = "solo admite números") String telefono,
            @NotBlank @Size(max = 200) String direccion) {
    }

    public record PerfilResponse(String nombres, String apellidos, String identificacion, String telefono,
            String direccion, String lugarTrabajo, String ocupacion, String antiguedadTrabajo, BigDecimal ingresosMensuales,
            LocalDate fechaNacimiento, String estadoCivil, String referenciaNombre, String referenciaTelefono, String banco,
            String tipoCuentaBanco, String numeroCuentaBanco, boolean datosCreditoCompletos) {
        public static PerfilResponse de(Persona p) {
            return new PerfilResponse(p.getNombres(), p.getApellidos(), p.getIdentificacion(), p.getTelefono(),
                    p.getDireccion(), p.getLugarTrabajo(), p.getOcupacion(), p.getAntiguedadTrabajo(), p.getIngresosMensuales(),
                    p.getFechaNacimiento(), p.getEstadoCivil(), p.getReferenciaNombre(), p.getReferenciaTelefono(), p.getBanco(),
                    p.getTipoCuentaBanco(), p.getNumeroCuentaBanco(), p.datosCreditoCompletos());
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

    @PutMapping("/me/datos-credito")
    public PerfilResponse actualizarDatosCredito(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DatosCreditoRequest request) {
        return PerfilResponse.de(service.actualizarDatosCredito(Long.valueOf(jwt.getSubject()), request));
    }

    @PutMapping("/me")
    public PerfilResponse actualizarContacto(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContactoRequest request) {
        return PerfilResponse.de(service.actualizarContacto(Long.valueOf(jwt.getSubject()), request));
    }
}
