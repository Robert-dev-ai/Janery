package ec.janery.pago;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.common.NegocioException;
import ec.janery.cuenta.RetiroService;
import ec.janery.pago.PagoController.DestinoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Ventanilla de tesorería: cobrar y entregar dinero por cualquier persona con cuenta activa. */
@RestController
@RequestMapping("/api/v1/ventanilla")
@PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
@RequiredArgsConstructor
public class VentanillaController {

    /** origen: EFECTIVO o TRANSFERENCIA (entra dinero a la caja), CUENTA o AHORRO (se paga con su saldo). */
    public record CobroRequest(@NotNull Long pagadorId,
            @NotNull @Size(min = 1, max = 20) List<@Valid @NotNull DestinoRequest> destinos,
            @NotNull @Pattern(regexp = "EFECTIVO|TRANSFERENCIA|CUENTA|AHORRO") String origen, Long ahorroId,
            @Size(max = 100) String referencia) {
    }

    public record RetiroRequest(@NotNull Long usuarioId, @NotNull @Pattern(regexp = "CUENTA|AHORRO") String origen, Long ahorroId,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotNull @Pattern(regexp = "EFECTIVO|TRANSFERENCIA") String medio, @Size(max = 100) String banco,
            @Pattern(regexp = "AHORROS|CORRIENTE") String tipoCuenta, @Size(max = 40) String numeroCuenta,
            @Size(max = 150) String titularCuenta, @Size(max = 100) String referencia) {
    }

    private final VentanillaService service;

    @GetMapping("/personas")
    public List<VentanillaService.Persona> buscar(@RequestParam(required = false) String buscar) {
        return service.buscar(buscar);
    }

    @GetMapping("/personas/{usuarioId}")
    public VentanillaService.Ficha ficha(@PathVariable Long usuarioId) {
        return service.ficha(usuarioId);
    }

    /** Multiparte: «datos» con el cobro en JSON y «archivos» con el comprobante, obligatorio solo en transferencia. */
    @PostMapping(path = "/cobros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VentanillaService.Cobro cobrar(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 60) String clave,
            @Valid @RequestPart("datos") CobroRequest r, @RequestPart(name = "archivos", required = false) List<MultipartFile> archivos) {
        List<PagoService.Destino> destinos = r.destinos().stream()
                .map(d -> new PagoService.Destino(d.usuarioId(), d.tipo(), d.referenciaId(), d.periodos(), d.importe())).toList();
        return service.cobrar(id(jwt), r.pagadorId(), destinos, r.origen(), r.ahorroId(), r.referencia(), clave, archivos);
    }

    @PostMapping("/retiros")
    @ResponseStatus(HttpStatus.CREATED)
    public RetiroService.Vista retirar(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 60) String clave, @Valid @RequestBody RetiroRequest r) {
        boolean aBanco = "TRANSFERENCIA".equals(r.medio());
        if (aBanco && (vacio(r.banco()) || vacio(r.tipoCuenta()) || vacio(r.numeroCuenta()) || vacio(r.titularCuenta()))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DATOS_BANCO_REQUERIDOS", "Indique banco, tipo y número de cuenta, y titular");
        }
        RetiroService.DatosBanco banco = aBanco
                ? new RetiroService.DatosBanco(r.banco().trim(), r.tipoCuenta(), r.numeroCuenta().trim(), r.titularCuenta().trim())
                : null;
        return service.entregar(id(jwt), r.usuarioId(), r.origen(), r.ahorroId(), r.importe(), r.medio(), banco,
                vacio(r.referencia()) ? null : r.referencia().trim(), clave);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
