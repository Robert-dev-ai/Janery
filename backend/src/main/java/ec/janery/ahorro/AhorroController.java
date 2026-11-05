package ec.janery.ahorro;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ahorros")
@RequiredArgsConstructor
public class AhorroController {

    public record NormalRequest(@NotBlank @Size(max = 100) String nombre) {
    }

    public record PlazoFijoRequest(@NotBlank @Size(max = 100) String nombre,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @Min(1) @Max(600) int plazoMeses) {
    }

    public record DepositoRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe) {
    }

    public record RetiroRequest(@Positive @Digits(integer = 12, fraction = 2) BigDecimal importe) {
    }

    public record OperacionResponse(Long operacionId) {
    }

    private final AhorroService service;

    @GetMapping("/productos")
    public AhorroService.Productos productos() {
        return service.productos();
    }

    @GetMapping("/me")
    public List<AhorroService.AhorroVista> misAhorros(@AuthenticationPrincipal Jwt jwt) {
        return service.misAhorros(id(jwt));
    }

    @PostMapping("/normales")
    @ResponseStatus(HttpStatus.CREATED)
    public AhorroService.AhorroVista crearNormal(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NormalRequest r) {
        return service.crearNormal(id(jwt), r.nombre().trim());
    }

    @PostMapping("/plazo-fijo")
    @ResponseStatus(HttpStatus.CREATED)
    public AhorroService.AhorroVista crearPlazoFijo(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody PlazoFijoRequest r) {
        return service.crearPlazoFijo(id(jwt), r.nombre().trim(), r.importe(), r.plazoMeses(), clave);
    }

    @GetMapping("/{ahorroId}/movimientos")
    public List<AhorroService.MovimientoVista> movimientos(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId) {
        return service.movimientos(id(jwt), ahorroId);
    }

    @PostMapping("/{ahorroId}/depositos")
    public OperacionResponse depositar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody DepositoRequest r) {
        return new OperacionResponse(service.depositar(id(jwt), ahorroId, r.importe(), clave));
    }

    @PostMapping("/{ahorroId}/retiros")
    public OperacionResponse retirar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody RetiroRequest r) {
        return new OperacionResponse(service.retirar(id(jwt), ahorroId, r.importe(), clave));
    }

    @PostMapping("/{ahorroId}/interes")
    public OperacionResponse retirarInteres(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave) {
        return new OperacionResponse(service.retirarInteres(id(jwt), ahorroId, clave));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{ahorroId}/nombre")
    public AhorroService.AhorroVista renombrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId,
            @Valid @RequestBody NormalRequest r) {
        return service.renombrar(id(jwt), ahorroId, r.nombre().trim());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{ahorroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId) {
        service.eliminar(id(jwt), ahorroId);
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
