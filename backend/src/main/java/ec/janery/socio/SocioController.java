package ec.janery.socio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.Pagina;
import ec.janery.socio.ParticipacionService.AporteVista;
import ec.janery.socio.ParticipacionService.CierreVista;
import ec.janery.socio.ParticipacionService.PeriodoVista;
import ec.janery.socio.ParticipacionService.ResultadoVista;
import ec.janery.socio.ParticipacionService.ResumenSocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
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
public class SocioController {

    public record AporteRequest(@NotNull @Pattern(regexp = "OBLIGATORIO|PERSONAL") String tipo,
            @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe, String periodo) {
    }

    public record AporteCajaChicaRequest(String periodo) {
    }

    public record GastoRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotBlank @Size(max = 200) String detalle) {
    }

    public record ResultadoRequest(@NotNull @Pattern(regexp = "GANANCIA|PERDIDA") String tipo,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotBlank @Size(max = 200) String concepto, LocalDate fecha) {
    }

    public record CierreRequest(@Min(2000) int anio) {
    }

    public record OperacionResponse(Long operacionId) {
    }

    public record MovimientoResponse(Long id, String sentido, BigDecimal importe, String concepto, String detalle,
            Instant fecha) {
    }

    public record CajaChicaResponse(BigDecimal saldo, String moneda, Pagina<MovimientoResponse> movimientos) {
    }

    private final ParticipacionService service;

    @GetMapping("/socios/me")
    public ResumenSocio resumen(@AuthenticationPrincipal Jwt jwt) {
        return service.miResumen(id(jwt));
    }

    @GetMapping("/socios/me/aportes")
    public List<AporteVista> aportes(@AuthenticationPrincipal Jwt jwt) {
        return service.misAportes(id(jwt));
    }

    @PostMapping("/socios/me/aportes")
    public OperacionResponse aportar(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String clave,
            @Valid @RequestBody AporteRequest r) {
        return new OperacionResponse(service.aportar(id(jwt), r.tipo(), r.importe(), r.periodo(), clave));
    }

    @PostMapping("/socios/me/aportes-caja-chica")
    public OperacionResponse aportarCajaChica(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String clave, @RequestBody AporteCajaChicaRequest r) {
        return new OperacionResponse(service.aportarCajaChica(id(jwt), r.periodo(), clave));
    }

    @GetMapping("/socios/me/participacion")
    public List<PeriodoVista> participacion(@AuthenticationPrincipal Jwt jwt) {
        return service.miEvolucion(id(jwt));
    }

    @GetMapping("/caja-chica")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CAJA_CHICA')")
    public CajaChicaResponse cajaChica(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return new CajaChicaResponse(service.saldoCajaChica(), "USD",
                Pagina.de(service.movimientosCajaChica(Math.max(pagina, 0), Pagina.limitar(tamano)),
                        m -> new MovimientoResponse(m.getId(), m.getSentido(), m.getImporte(), m.getConcepto(),
                                m.getDetalle(), m.getCreadoEn())));
    }

    @PostMapping("/caja-chica/gastos")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CAJA_CHICA')")
    public OperacionResponse gasto(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String clave,
            @Valid @RequestBody GastoRequest r) {
        return new OperacionResponse(service.registrarGastoCajaChica(id(jwt), r.importe(), r.detalle().trim(), clave));
    }

    @GetMapping("/admin/socios")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public List<ResumenSocio> socios() {
        return service.listarSocios();
    }

    @GetMapping("/admin/resultados")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public Pagina<ResultadoVista> resultados(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.listarResultados(Math.max(pagina, 0), Pagina.limitar(tamano)), v -> v);
    }

    @PostMapping("/admin/resultados")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarResultado(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResultadoRequest r) {
        service.registrarResultado(id(jwt), r.tipo(), r.importe(), r.concepto().trim(), r.fecha());
    }

    @GetMapping("/admin/cierres")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public List<CierreVista> cierres() {
        return service.listarCierres();
    }

    @PostMapping("/admin/cierres")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public CierreVista cerrar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CierreRequest r) {
        return service.cerrarAnio(id(jwt), r.anio());
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
