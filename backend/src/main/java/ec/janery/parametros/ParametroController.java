package ec.janery.parametros;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/parametros")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PARAMETROS')")
@RequiredArgsConstructor
public class ParametroController {

    public record CambioRequest(@NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal valor,
            Instant vigenteDesde, @NotBlank @Size(max = 300) String motivo) {
    }

    public record ValorResponse(BigDecimal valor, Instant vigenteDesde, Instant vigenteHasta) {
        static ValorResponse de(ParametroValor v) {
            return v == null ? null : new ValorResponse(v.getValor(), v.getVigenteDesde(), v.getVigenteHasta());
        }
    }

    public record ParametroResponse(Long id, String codigo, String nombre, String descripcion, String unidad,
            ValorResponse vigente, ValorResponse programado) {
    }

    public record HistorialResponse(BigDecimal valorAnterior, BigDecimal valorNuevo, Instant vigenteDesde,
            Long actorId, String motivo, Instant fecha) {
    }

    private final ParametroService service;

    @GetMapping
    public List<ParametroResponse> listar() {
        return service.listar().stream().map(v -> new ParametroResponse(v.parametro().getId(),
                v.parametro().getCodigo(), v.parametro().getNombre(), v.parametro().getDescripcion(),
                v.parametro().getUnidad(), ValorResponse.de(v.vigente()), ValorResponse.de(v.programado()))).toList();
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody CambioRequest request) {
        service.cambiarValor(Long.valueOf(jwt.getSubject()), id, request.valor(), request.vigenteDesde(),
                request.motivo().trim());
    }

    @GetMapping("/{id}/historial")
    public List<HistorialResponse> historial(@PathVariable Long id) {
        return service.historial(id).stream().map(h -> new HistorialResponse(h.getValorAnterior(), h.getValorNuevo(),
                h.getVigenteDesde(), h.getActorId(), h.getMotivo(), h.getCreadoEn())).toList();
    }
}
