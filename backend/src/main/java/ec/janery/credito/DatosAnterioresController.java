package ec.janery.credito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.ahorro.AhorroService;
import ec.janery.parametros.ParametroService;
import ec.janery.socio.ParticipacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Lo que ya existía antes de usar Janery, además de los créditos a letras: emergentes, cupos de socio, ahorros y aportes.
 * Las personas se buscan en {@code /api/v1/admin/creditos/existentes/personas}.
 */
@RestController
@RequestMapping("/api/v1/admin/anteriores")
@PreAuthorize("hasAuthority('SCOPE_REGISTRAR_CREDITOS_EXISTENTES')")
@RequiredArgsConstructor
public class DatosAnterioresController {

    public record EmergenteClienteRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotNull LocalDate fechaInicio,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 6) BigDecimal tasaInteres,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 6) BigDecimal tasaDesgravamen,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal capitalDebe,
            boolean cargosPagados,
            @Size(max = 300) String nota) {
    }

    public record CupoSocioRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal capitalDebe,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal interesPendiente,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal desgravamenPendiente,
            @Size(max = 300) String nota) {
    }

    /** Tasa del plazo fijo en % anual. */
    public record AhorroRequest(@NotNull @Pattern(regexp = "NORMAL|PLAZO_FIJO") String tipo,
            @NotBlank @Size(max = 100) String nombre,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal saldo,
            LocalDate fechaApertura,
            @Min(1) @Max(120) Integer plazoMeses,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 6) BigDecimal tasaAnual) {
    }

    public record AportesRequest(LocalDate socioDesde,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal aporteInicial,
            YearMonth mensualesHasta,
            @Positive @Digits(integer = 12, fraction = 2) BigDecimal importeMensual,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal personales,
            YearMonth cajaChicaHasta) {
    }

    public record CreadoResponse(Long id) {
    }

    /** Para llenar el formulario con los valores de hoy, que la persona puede cambiar. */
    private static final List<String> VALORES = List.of("EMERGENTE_CLIENTE_INTERES", "EMERGENTE_CLIENTE_DESGRAVAMEN",
            "APORTE_OBLIGATORIO", "APORTE_CAJA_CHICA", "PLAZO_FIJO_TASA_3_MESES", "PLAZO_FIJO_TASA_6_MESES",
            "PLAZO_FIJO_TASA_9_MESES", "PLAZO_FIJO_TASA_12_MESES", "PLAZO_FIJO_TASA_24_MESES");

    private final CreditoExistenteService creditos;
    private final AhorroService ahorros;
    private final ParticipacionService participacion;
    private final ParametroService parametros;

    @GetMapping("/valores")
    public Map<String, BigDecimal> valores() {
        return parametros.valoresVigentes(VALORES);
    }

    @PostMapping("/personas/{usuarioId}/emergente-cliente")
    @ResponseStatus(HttpStatus.CREATED)
    public CreadoResponse emergenteCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String clave, @Valid @RequestBody EmergenteClienteRequest r) {
        return new CreadoResponse(creditos.registrarEmergenteCliente(actor(jwt), new CreditoExistenteService.EmergenteCliente(usuarioId,
                r.importe(), r.fechaInicio(), r.tasaInteres(), r.tasaDesgravamen(), r.capitalDebe(), r.cargosPagados(), nota(r.nota())), clave));
    }

    @PostMapping("/personas/{usuarioId}/cupo-socio")
    @ResponseStatus(HttpStatus.CREATED)
    public CreadoResponse cupoSocio(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String clave, @Valid @RequestBody CupoSocioRequest r) {
        return new CreadoResponse(creditos.registrarCupoSocio(actor(jwt), new CreditoExistenteService.CupoSocio(usuarioId,
                r.capitalDebe(), r.interesPendiente(), r.desgravamenPendiente(), nota(r.nota())), clave));
    }

    @PostMapping("/personas/{usuarioId}/ahorros")
    @ResponseStatus(HttpStatus.CREATED)
    public AhorroService.AhorroVista ahorro(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String clave, @Valid @RequestBody AhorroRequest r) {
        return ahorros.registrarAnterior(actor(jwt), new AhorroService.Anterior(usuarioId, r.tipo(), r.nombre().trim(), r.saldo(),
                r.fechaApertura(), r.plazoMeses(), r.tasaAnual()), clave);
    }

    @GetMapping("/personas/{usuarioId}/aportes")
    public ParticipacionService.EstadoAportes estadoAportes(@PathVariable Long usuarioId) {
        return participacion.estadoAportes(usuarioId);
    }

    @PostMapping("/personas/{usuarioId}/aportes")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipacionService.AportesRegistrados aportes(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String clave, @Valid @RequestBody AportesRequest r) {
        return participacion.registrarAportesAnteriores(actor(jwt), new ParticipacionService.AportesAnteriores(usuarioId, r.socioDesde(),
                r.aporteInicial(), r.mensualesHasta(), r.importeMensual(), r.personales(), r.cajaChicaHasta()), clave);
    }

    private static Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private static String nota(String nota) {
        return nota == null || nota.isBlank() ? null : nota.trim();
    }
}
