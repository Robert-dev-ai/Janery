package ec.janery.cuenta;

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

import ec.janery.common.NegocioException;
import ec.janery.common.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RetiroController {

    public record SolicitudRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotNull @Pattern(regexp = "EFECTIVO|TRANSFERENCIA") String medio, @Size(max = 100) String banco,
            @Pattern(regexp = "AHORROS|CORRIENTE") String tipoCuenta, @Size(max = 40) String numeroCuenta,
            @Size(max = 150) String titularCuenta) {
    }

    public record EntregaRequest(@Size(max = 100) String referencia) {
    }

    public record MotivoRequest(@NotBlank @Size(max = 300) String motivo) {
    }

    private final RetiroService service;

    @PostMapping("/cuentas-principales/me/retiros")
    @ResponseStatus(HttpStatus.CREATED)
    public RetiroService.Vista solicitar(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String clave, @Valid @RequestBody SolicitudRequest r) {
        if ("TRANSFERENCIA".equals(r.medio()) && (vacio(r.banco()) || vacio(r.tipoCuenta()) || vacio(r.numeroCuenta()) || vacio(r.titularCuenta()))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DATOS_BANCO_REQUERIDOS", "Indique banco, tipo y número de cuenta, y titular");
        }
        return service.solicitar(id(jwt), r.importe(), r.medio(), new RetiroService.DatosBanco(limpio(r.banco()), r.tipoCuenta(),
                limpio(r.numeroCuenta()), limpio(r.titularCuenta())), clave);
    }

    @GetMapping("/cuentas-principales/me/retiros")
    public List<RetiroService.Vista> misSolicitudes(@AuthenticationPrincipal Jwt jwt) {
        return service.misSolicitudes(id(jwt));
    }

    @PostMapping("/cuentas-principales/me/retiros/{solicitud}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long solicitud) {
        service.cancelar(id(jwt), solicitud);
    }

    @GetMapping("/admin/solicitudes-retiro")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public Pagina<RetiroService.Vista> listar(@RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.listar(estado, Math.max(pagina, 0), Pagina.limitar(tamano)), v -> v);
    }

    @PostMapping("/admin/solicitudes-retiro/{solicitud}/entregar")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void entregar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long solicitud, @Valid @RequestBody EntregaRequest r) {
        service.confirmarEntrega(id(jwt), solicitud, limpio(r.referencia()));
    }

    @PostMapping("/admin/solicitudes-retiro/{solicitud}/rechazar")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long solicitud, @Valid @RequestBody MotivoRequest r) {
        service.rechazar(id(jwt), solicitud, r.motivo().trim());
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String limpio(String texto) {
        return vacio(texto) ? null : texto.trim();
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
