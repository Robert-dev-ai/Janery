package ec.janery.simulacion;

import java.time.LocalDate;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/** Sin el perfil simulacion esta ruta no existe. */
@RestController
@RequestMapping("/api/v1/simulacion")
@Profile("simulacion")
@RequiredArgsConstructor
public class SimulacionController {

    public record AvanceRequest(@Positive Integer dias, LocalDate fecha) {
    }

    private final SimulacionService service;

    @GetMapping
    public SimulacionService.Estado estado() {
        return service.estado();
    }

    public record PersonaRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 60) String nombres,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero
            @jakarta.validation.constraints.Digits(integer = 9, fraction = 2) java.math.BigDecimal saldo, boolean socio) {
    }

    /** Copia la base real tal como está ahora. Si ya había una simulación, la reemplaza. */
    @PostMapping("/iniciar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PARAMETROS', 'SCOPE_USAR_SIMULADOR')")
    public SimulacionService.Estado iniciar(@AuthenticationPrincipal Jwt jwt) {
        return service.iniciar(Long.valueOf(jwt.getSubject()));
    }

    /** Descarta todo lo simulado. La base real nunca cambió. */
    @PostMapping("/finalizar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PARAMETROS', 'SCOPE_USAR_SIMULADOR')")
    public SimulacionService.Estado finalizar(@AuthenticationPrincipal Jwt jwt) {
        return service.finalizar(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/personas")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PARAMETROS', 'SCOPE_USAR_SIMULADOR')")
    public SimulacionService.PersonaPrueba crearPersona(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PersonaRequest r) {
        return service.crearPersona(Long.valueOf(jwt.getSubject()), r.nombres().trim(), r.saldo(), r.socio());
    }

    @PostMapping("/avanzar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PARAMETROS', 'SCOPE_USAR_SIMULADOR')")
    public SimulacionService.Estado avanzar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvanceRequest r) {
        return service.avanzar(Long.valueOf(jwt.getSubject()), r.dias(), r.fecha());
    }
}
