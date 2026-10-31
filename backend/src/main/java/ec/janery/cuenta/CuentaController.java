package ec.janery.cuenta;

import java.math.BigDecimal;
import java.time.Instant;

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
public class CuentaController {

    private static final String METODOS = "EFECTIVO|TRANSFERENCIA";

    public record AperturaRequest(@NotNull @Pattern(regexp = METODOS) String metodoPago,
            @Size(max = 100) String referenciaPago) {
    }

    public record MotivoRequest(@NotBlank @Size(max = 300) String motivo) {
    }

    public record MovimientoRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotNull @Pattern(regexp = METODOS) String metodoPago, @Size(max = 100) String referencia) {
    }

    public record CuentaResponse(Long id, String numero, String titular, String estado, BigDecimal saldoDisponible,
            BigDecimal depositoReembolsable, String moneda, String metodoPago, String referenciaPago,
            Instant abiertaEn) {
        static CuentaResponse de(CuentaService.CuentaVista v) {
            CuentaPrincipal c = v.cuenta();
            return new CuentaResponse(c.getId(), c.getNumero(), v.titular(), c.getEstado(), c.getSaldoDisponible(),
                    v.depositoReembolsable(), "USD", c.getMetodoPago(), c.getReferenciaPago(), c.getAbiertaEn());
        }
    }

    public record MovimientoResponse(Long id, String sentido, BigDecimal importe, String concepto,
            BigDecimal saldoResultante, Instant fecha) {
    }

    public record OperacionResponse(Long operacionId) {
    }

    private final CuentaService service;

    @PostMapping("/cuentas-principales/apertura")
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaResponse solicitarApertura(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AperturaRequest request) {
        if ("TRANSFERENCIA".equals(request.metodoPago())
                && (request.referenciaPago() == null || request.referenciaPago().isBlank())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "REFERENCIA_REQUERIDA",
                    "Indique el número de la transferencia");
        }
        return CuentaResponse.de(service.solicitarApertura(id(jwt), request.metodoPago(), request.referenciaPago()));
    }

    @GetMapping("/cuentas-principales/me")
    public CuentaResponse miCuenta(@AuthenticationPrincipal Jwt jwt) {
        return CuentaResponse.de(service.miCuenta(id(jwt)));
    }

    @GetMapping("/cuentas-principales/me/movimientos")
    public Pagina<MovimientoResponse> misMovimientos(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.misMovimientos(id(jwt), Math.max(pagina, 0), Pagina.limitar(tamano)),
                m -> new MovimientoResponse(m.getId(), m.getSentido(), m.getImporte(), m.getConcepto(),
                        m.getSaldoResultante(), m.getCreadoEn()));
    }

    @PostMapping("/cuentas-principales/me/cierre")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void solicitarCierre(@AuthenticationPrincipal Jwt jwt) {
        service.solicitarCierre(id(jwt));
    }

    @GetMapping("/admin/cuentas")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public Pagina<CuentaResponse> listar(@RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.listar(estado, Math.max(pagina, 0), Pagina.limitar(tamano)), CuentaResponse::de);
    }

    @PostMapping("/admin/cuentas/{id}/confirmar-apertura")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public CuentaResponse confirmarApertura(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return CuentaResponse.de(service.confirmarApertura(id(jwt), id));
    }

    @PostMapping("/admin/cuentas/{id}/rechazar-apertura")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazarApertura(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MotivoRequest request) {
        service.rechazarApertura(id(jwt), id, request.motivo().trim());
    }

    @PostMapping("/admin/cuentas/{id}/confirmar-cierre")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmarCierre(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.confirmarCierre(id(jwt), id);
    }

    @PostMapping("/admin/cuentas/{id}/depositos")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public OperacionResponse depositar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String clave,
            @Valid @RequestBody MovimientoRequest r) {
        return new OperacionResponse(service.depositar(id(jwt), id, r.importe(), r.metodoPago(), r.referencia(), clave));
    }

    @PostMapping("/admin/cuentas/{id}/retiros")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public OperacionResponse retirar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String clave,
            @Valid @RequestBody MovimientoRequest r) {
        return new OperacionResponse(service.retirar(id(jwt), id, r.importe(), r.metodoPago(), r.referencia(), clave));
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
