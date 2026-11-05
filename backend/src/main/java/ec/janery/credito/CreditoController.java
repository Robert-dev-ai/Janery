package ec.janery.credito;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CreditoController {

    public record LetrasRequest(@NotNull @Pattern(regexp = "NORMAL|INVERSION|ESTUDIO|PERSONALIZADO") String modalidad,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @Min(1) @Max(600) int plazoMeses, @Pattern(regexp = "EFECTIVO|CUENTA|AHORROS|CREDITO") String origenEncaje,
            Long ahorroEncajeId) {
    }

    public record EmergenteRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe) {
    }

    public record DisposicionRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @Pattern(regexp = "ESTUDIANTIL|NAVIDENO") String promocion) {
    }

    public record PagoRequest(@Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            boolean usarAhorroLetras, boolean usarEncaje) {
    }

    public record MotivoRequest(@NotBlank @Size(max = 300) String motivo) {
    }

    public record CreadoResponse(Long id) {
    }

    public record OperacionResponse(Long operacionId) {
    }

    private final CreditoService service;
    private final PagoCreditoService pagos;

    @GetMapping("/creditos/productos")
    public CreditoService.Productos productos(@AuthenticationPrincipal Jwt jwt) {
        return service.productos(id(jwt));
    }

    @PostMapping("/creditos/letras/simulacion")
    public CreditoService.Simulacion simular(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LetrasRequest r) {
        return service.simular(id(jwt), r.modalidad(), r.importe(), r.plazoMeses());
    }

    @PostMapping("/creditos/letras/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    public CreadoResponse solicitar(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave,
            @Valid @RequestBody LetrasRequest r) {
        return new CreadoResponse(service.solicitarLetras(id(jwt), r.modalidad(), r.importe(), r.plazoMeses(), r.origenEncaje(), r.ahorroEncajeId(),
                clave));
    }

    @PostMapping("/creditos/emergentes/solicitudes")
    public CreadoResponse emergente(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave,
            @Valid @RequestBody EmergenteRequest r) {
        return new CreadoResponse(service.solicitarEmergenteCliente(id(jwt), r.importe(), clave));
    }

    @PostMapping("/creditos/emergentes/disposiciones")
    public CreadoResponse disponer(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave,
            @Valid @RequestBody DisposicionRequest r) {
        return new CreadoResponse(service.solicitarCupo(id(jwt), r.importe(), r.promocion(), clave));
    }

    @GetMapping("/creditos/emergentes/disposiciones/me")
    public List<CreditoService.SolicitudCupoVista> misSolicitudesCupo(@AuthenticationPrincipal Jwt jwt) {
        return service.misSolicitudesCupo(id(jwt));
    }

    @GetMapping("/admin/creditos/disposiciones")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public List<CreditoService.SolicitudCupoVista> solicitudesCupo() {
        return service.solicitudesCupoPendientes();
    }

    @PostMapping("/admin/creditos/disposiciones/{solicitudId}/aprobar")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aprobarCupo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long solicitudId) {
        service.aprobarCupo(id(jwt), solicitudId);
    }

    @PostMapping("/admin/creditos/disposiciones/{solicitudId}/rechazar")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazarCupo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long solicitudId, @Valid @RequestBody MotivoRequest r) {
        service.rechazarCupo(id(jwt), solicitudId, r.motivo().trim());
    }

    @GetMapping("/creditos/me")
    public List<CreditoService.CreditoVista> misCreditos(@AuthenticationPrincipal Jwt jwt) {
        return service.misCreditos(id(jwt));
    }

    @GetMapping("/creditos/{creditoId}")
    public CreditoService.CreditoVista detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId) {
        return service.detalle(id(jwt), creditoId);
    }

    @PostMapping("/creditos/{creditoId}/pagos")
    public OperacionResponse pagar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody PagoRequest r) {
        return new OperacionResponse(pagos.pagar(id(jwt), creditoId, r.importe(), r.usarAhorroLetras(), r.usarEncaje(), clave));
    }

    @PostMapping("/creditos/{creditoId}/precancelacion")
    public OperacionResponse precancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave) {
        return new OperacionResponse(pagos.precancelar(id(jwt), creditoId, clave));
    }

    @GetMapping("/admin/creditos")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public Pagina<CreditoService.CreditoVista> listar(@RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.listar(estado, Math.max(pagina, 0), Pagina.limitar(tamano)), v -> v);
    }

    @PostMapping("/admin/creditos/{creditoId}/aprobar")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId) {
        service.aprobar(id(jwt), creditoId);
    }

    @PostMapping("/admin/creditos/{creditoId}/rechazar")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId,
            @Valid @RequestBody MotivoRequest r) {
        service.rechazar(id(jwt), creditoId, r.motivo().trim());
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
