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

    @PostMapping("/avanzar")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PARAMETROS')")
    public SimulacionService.Estado avanzar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvanceRequest r) {
        return service.avanzar(Long.valueOf(jwt.getSubject()), r.dias(), r.fecha());
    }
}
