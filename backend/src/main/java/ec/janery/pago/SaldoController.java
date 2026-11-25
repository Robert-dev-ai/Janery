package ec.janery.pago;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.NegocioException;
import ec.janery.cuenta.RetiroController;
import ec.janery.cuenta.RetiroService;
import ec.janery.pago.PagoController.DestinoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Decisión del propietario (24/11/2026): cada persona puede pagar y retirar dinero directo desde su ahorro, sin pasarlo
 * antes a la cuenta principal. Solo lo suyo; el tesorero hace lo mismo por cualquiera desde la ventanilla.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SaldoController {

    /** origen: CUENTA (saldo de la cuenta principal) o AHORRO (con ahorroId, un ahorro normal propio). */
    public record PagoConSaldoRequest(@NotNull @Size(min = 1, max = 20) List<@Valid @NotNull DestinoRequest> destinos,
            @NotNull @Pattern(regexp = "CUENTA|AHORRO") String origen, Long ahorroId) {
    }

    private final VentanillaService service;

    /** Se aplica al momento y sin comprobante: no entra dinero a la caja. */
    @PostMapping("/pagos/con-saldo")
    @ResponseStatus(HttpStatus.CREATED)
    public VentanillaService.Cobro pagarConSaldo(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 60) String clave, @Valid @RequestBody PagoConSaldoRequest r) {
        Long yo = id(jwt);
        List<PagoService.Destino> destinos = r.destinos().stream()
                .map(d -> new PagoService.Destino(d.usuarioId(), d.tipo(), d.referenciaId(), d.periodos(), d.importe())).toList();
        return service.pagarConSaldo(yo, yo, destinos, r.origen(), r.ahorroId(), clave);
    }

    /** Retiro de dinero que sale directo del ahorro: queda pendiente hasta que el personal lo entregue. */
    @PostMapping("/ahorros/{ahorroId}/retiros-dinero")
    @ResponseStatus(HttpStatus.CREATED)
    public RetiroService.Vista retirarDesdeAhorro(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ahorroId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 60) String clave,
            @Valid @RequestBody RetiroController.SolicitudRequest r) {
        boolean aBanco = "TRANSFERENCIA".equals(r.medio());
        if (aBanco && (vacio(r.banco()) || vacio(r.tipoCuenta()) || vacio(r.numeroCuenta()) || vacio(r.titularCuenta()))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DATOS_BANCO_REQUERIDOS", "Indique banco, tipo y número de cuenta, y titular");
        }
        RetiroService.DatosBanco banco = aBanco
                ? new RetiroService.DatosBanco(r.banco().trim(), r.tipoCuenta(), r.numeroCuenta().trim(), r.titularCuenta().trim())
                : null;
        return service.solicitarRetiroDesdeAhorro(id(jwt), ahorroId, r.importe(), r.medio(), banco, clave);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
