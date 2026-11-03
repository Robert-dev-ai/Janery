package ec.janery.pago;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {

    public record DestinoRequest(@NotNull Long usuarioId,
            @NotNull @Pattern(regexp = "CUENTA|CUOTA_CREDITO|AHORRO|APORTE_OBLIGATORIO|APORTE_PERSONAL|CAJA_CHICA") String tipo,
            Long referenciaId, @Size(max = 12) List<@Pattern(regexp = "\\d{4}-\\d{2}") String> periodos,
            @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe) {
    }

    public record PagoRequest(@NotNull @Size(min = 1, max = 20) List<@Valid @NotNull DestinoRequest> destinos,
            @NotNull @Pattern(regexp = "EFECTIVO|TRANSFERENCIA") String medio, @Size(max = 100) String referencia) {
    }

    private final PagoService service;

    @GetMapping("/personas")
    public List<PagoService.Persona> buscar(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String buscar) {
        return service.buscar(id(jwt), buscar);
    }

    @GetMapping("/opciones/me")
    public PagoService.Opciones misOpciones(@AuthenticationPrincipal Jwt jwt) {
        return service.opciones(id(jwt), id(jwt));
    }

    @GetMapping("/opciones/{usuarioId}")
    public PagoService.Opciones opciones(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId) {
        return service.opciones(id(jwt), usuarioId);
    }

    /** Multiparte: «datos» con el pago en JSON y «archivos» con el comprobante, que es obligatorio. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PagoService.Creado pagar(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @Size(max = 60) String clave,
            @Valid @RequestPart("datos") PagoRequest r, @RequestPart(name = "archivos", required = false) List<MultipartFile> archivos) {
        boolean sinReferencia = r.referencia() == null || r.referencia().isBlank();
        if ("TRANSFERENCIA".equals(r.medio()) && sinReferencia) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "REFERENCIA_REQUERIDA", "Indique el número de la transferencia");
        }
        List<PagoService.Destino> destinos = r.destinos().stream()
                .map(d -> new PagoService.Destino(d.usuarioId(), d.tipo(), d.referenciaId(), d.periodos(), d.importe())).toList();
        return service.crear(id(jwt), destinos, r.medio(), sinReferencia ? null : r.referencia().trim(), clave, archivos);
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
