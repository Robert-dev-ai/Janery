package ec.janery.contabilidad;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/contabilidad")
@RequiredArgsConstructor
public class ContabilidadController {

    public record AsientoRequest(@NotBlank @Size(max = 5) String linea, boolean disminuye,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotBlank @Size(max = 150) String descripcion) {
    }

    public record MotivoRequest(@NotBlank @Size(max = 150) String motivo) {
    }

    public record CreadoResponse(Long id) {
    }

    private final ContabilidadService service;

    @GetMapping("/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_CONSULTAR_CONTABILIDAD', 'SCOPE_SOCIO')")
    public ContabilidadService.Estado estado(@RequestParam(required = false) String periodo) {
        return service.estado(periodo);
    }

    @GetMapping("/evolucion")
    @PreAuthorize("hasAnyAuthority('SCOPE_CONSULTAR_CONTABILIDAD', 'SCOPE_SOCIO')")
    public List<ContabilidadService.MesResultado> evolucion() {
        return service.evolucion(6);
    }

    @GetMapping("/diario")
    @PreAuthorize("hasAnyAuthority('SCOPE_CONSULTAR_CONTABILIDAD', 'SCOPE_SOCIO')")
    public ContabilidadService.Diario diario(@RequestParam(required = false) String periodo,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamano) {
        return service.diario(periodo, Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @GetMapping("/mayor")
    @PreAuthorize("hasAnyAuthority('SCOPE_CONSULTAR_CONTABILIDAD', 'SCOPE_SOCIO')")
    public ContabilidadService.Mayor mayor(@RequestParam String linea, @RequestParam(required = false) String periodo,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamano) {
        return service.mayor(linea, periodo, Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @GetMapping("/lineas-manuales")
    @PreAuthorize("hasAuthority('SCOPE_REGISTRAR_ASIENTOS')")
    public List<ContabilidadService.LineaManual> lineasManuales() {
        return service.lineasManuales();
    }

    @GetMapping("/asientos")
    @PreAuthorize("hasAuthority('SCOPE_REGISTRAR_ASIENTOS')")
    public Pagina<ContabilidadService.AsientoVista> asientos(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.asientos(Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @PostMapping("/asientos")
    @PreAuthorize("hasAuthority('SCOPE_REGISTRAR_ASIENTOS')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreadoResponse registrar(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @Size(max = 100) String clave,
            @Valid @RequestBody AsientoRequest r) {
        return new CreadoResponse(service.registrar(Long.valueOf(jwt.getSubject()), r.linea(), !r.disminuye(), r.importe(),
                r.descripcion().trim(), clave));
    }

    @PostMapping("/asientos/{id}/reverso")
    @PreAuthorize("hasAuthority('SCOPE_REGISTRAR_ASIENTOS')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreadoResponse revertir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody MotivoRequest r) {
        return new CreadoResponse(service.revertir(Long.valueOf(jwt.getSubject()), id, r.motivo().trim()));
    }
}
