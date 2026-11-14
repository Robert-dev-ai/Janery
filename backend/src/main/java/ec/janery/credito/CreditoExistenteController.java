package ec.janery.credito;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Registro de créditos a letras que ya existían antes de usar Janery. */
@RestController
@RequestMapping("/api/v1/admin/creditos/existentes")
@PreAuthorize("hasAuthority('SCOPE_REGISTRAR_CREDITOS_EXISTENTES')")
@RequiredArgsConstructor
public class CreditoExistenteController {

    /** Tasas en porcentaje mensual sobre el importe; el encaje, en dólares. */
    public record ExistenteRequest(@NotNull @Pattern(regexp = "NORMAL|INVERSION|ESTUDIO|PERSONALIZADO") String modalidad,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @Min(1) @Max(600) int plazoMeses,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 6) BigDecimal tasaInteres,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 6) BigDecimal tasaDesgravamen,
            boolean conAhorroLetras,
            @NotNull LocalDate fechaInicio,
            @Min(0) int letrasPagadas,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal encaje,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal capitalDebe,
            @Size(max = 300) String nota) {

        CreditoExistenteService.Datos datos(Long usuarioId) {
            return new CreditoExistenteService.Datos(usuarioId, modalidad, importe, plazoMeses, tasaInteres, tasaDesgravamen,
                    conAhorroLetras, fechaInicio, letrasPagadas, encaje, capitalDebe, nota == null || nota.isBlank() ? null : nota.trim());
        }
    }

    private final CreditoExistenteService service;
    private final TramiteCreditoService tramite;

    /** Personas con cuenta principal activa: las letras pendientes se cobran de ella. */
    @GetMapping("/personas")
    public List<TramiteCreditoService.PersonaTramite> buscar(@RequestParam(defaultValue = "") String buscar) {
        return tramite.buscar(buscar);
    }

    @PostMapping("/personas/{usuarioId}/vista-previa")
    public CreditoExistenteService.Vista previsualizar(@PathVariable Long usuarioId, @Valid @RequestBody ExistenteRequest r) {
        return service.previsualizar(r.datos(usuarioId));
    }

    @PostMapping("/personas/{usuarioId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditoController.CreadoResponse registrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @Size(max = 100) String clave, @Valid @RequestBody ExistenteRequest r) {
        return new CreditoController.CreadoResponse(service.registrar(Long.valueOf(jwt.getSubject()), r.datos(usuarioId), clave));
    }
}
