package ec.janery.socio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public record AporteInicialRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotNull @Pattern(regexp = "EFECTIVO|TRANSFERENCIA") String metodoPago, @Size(max = 100) String referencia) {
    }

    public record RetiroGananciaRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal importe) {
    }

    public record CompraventaRequest(@NotNull Long vendedorId, @NotNull Long compradorId,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal capital,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio) {
    }

    public record DescuentosRequest(@NotNull List<ParticipacionService.Descuento> descuentos) {
    }

    public record OperacionResponse(Long operacionId) {
    }

    public record MovimientoResponse(Long id, String sentido, BigDecimal importe, String concepto, String detalle,
            Instant fecha, String quien) {
    }

    public record CajaChicaResponse(BigDecimal saldo, String moneda, Pagina<MovimientoResponse> movimientos) {
    }

    private final ParticipacionService service;
    private final ec.janery.caja.CajaService cajas;

    @GetMapping("/socios/me")
    public ResumenSocio resumen(@AuthenticationPrincipal Jwt jwt) {
        return service.miResumen(id(jwt));
    }

    @GetMapping("/socios/me/aportes")
    public List<AporteVista> aportes(@AuthenticationPrincipal Jwt jwt) {
        return service.misAportes(id(jwt));
    }

    @PostMapping("/socios/me/aportes")
    public OperacionResponse aportar(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave,
            @Valid @RequestBody AporteRequest r) {
        return new OperacionResponse(service.aportar(id(jwt), r.tipo(), r.importe(), r.periodo(), clave));
    }

    public record PeriodosRequest(@NotNull @Pattern(regexp = "OBLIGATORIO|CAJA_CHICA") String tipo,
            @NotNull @Size(min = 1, max = 12) List<String> periodos) {
    }

    public record RecepcionRequest(@NotNull Long socioId, @NotNull @Size(min = 1, max = 12) List<String> periodos) {
    }

    public record OperacionesResponse(List<Long> operaciones) {
    }

    /** Pago de varios meses a la vez, del aporte mensual o de caja chica. */
    @PostMapping("/socios/me/aportes/periodos")
    public OperacionesResponse pagarPeriodos(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @Size(max = 80) String clave,
            @Valid @RequestBody PeriodosRequest r) {
        return new OperacionesResponse(service.pagarPeriodos(id(jwt), r.tipo(), r.periodos(), clave));
    }

    @GetMapping("/socios/me/calendario")
    public List<ParticipacionService.MesAporte> calendario(@AuthenticationPrincipal Jwt jwt) {
        return service.miCalendario(id(jwt));
    }

    @GetMapping("/socios/condiciones")
    public ParticipacionService.Condiciones condiciones() {
        return service.condiciones();
    }

    @PostMapping("/socios/hacerme-socio")
    public OperacionResponse hacermeSocio(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave) {
        return new OperacionResponse(service.hacerseSocio(id(jwt), clave));
    }

    @GetMapping("/caja-chica/socios")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CAJA_CHICA')")
    public List<ParticipacionService.SocioCajaChica> sociosCajaChica() {
        return service.sociosCajaChica();
    }

    /** Quien administra caja chica registra el aporte que un socio le pagó en persona. */
    @PostMapping("/caja-chica/aportes")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CAJA_CHICA')")
    public OperacionesResponse recibirAporte(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @Size(max = 80) String clave,
            @Valid @RequestBody RecepcionRequest r) {
        return new OperacionesResponse(service.recibirAporteCajaChica(id(jwt), r.socioId(), r.periodos(), clave));
    }

    @PostMapping("/socios/me/aportes-caja-chica")
    public OperacionResponse aportarCajaChica(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @RequestBody AporteCajaChicaRequest r) {
        return new OperacionResponse(service.aportarCajaChica(id(jwt), r.periodo(), clave));
    }

    @GetMapping("/socios/me/participacion")
    public List<PeriodoVista> participacion(@AuthenticationPrincipal Jwt jwt) {
        return service.miEvolucion(id(jwt));
    }

    @PostMapping("/socios/me/retiro-ganancias")
    public OperacionResponse retirarGanancias(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody RetiroGananciaRequest r) {
        return new OperacionResponse(service.retirarGanancias(id(jwt), r.importe(), clave));
    }

    @GetMapping("/admin/socios/{socioId}/obligaciones")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public ParticipacionService.MenuObligaciones obligaciones(@PathVariable Long socioId) {
        return service.menuObligaciones(socioId);
    }

    @PostMapping("/admin/socios/{socioId}/descuentos")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descontar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long socioId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody DescuentosRequest r) {
        service.descontar(id(jwt), socioId, r.descuentos(), clave);
    }

    @PostMapping("/admin/socios/{socioId}/autorizacion-retiro")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void autorizarRetiro(@AuthenticationPrincipal Jwt jwt, @PathVariable Long socioId) {
        service.autorizarRetiro(id(jwt), socioId);
    }

    @DeleteMapping("/admin/socios/{socioId}/autorizacion-retiro")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarRetiro(@AuthenticationPrincipal Jwt jwt, @PathVariable Long socioId) {
        service.revocarRetiro(id(jwt), socioId);
    }

    @PostMapping("/admin/socios/compraventas")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public OperacionResponse compraventa(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave,
            @Valid @RequestBody CompraventaRequest r) {
        return new OperacionResponse(service.registrarCompraventa(id(jwt), r.vendedorId(), r.compradorId(), r.capital(), r.precio(), clave));
    }

    @GetMapping("/caja-chica")
    @PreAuthorize("hasAnyAuthority('SCOPE_GESTIONAR_CAJA_CHICA', 'SCOPE_SOCIO')")
    public CajaChicaResponse cajaChica(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var movimientos = service.movimientosCajaChica(Math.max(pagina, 0), Pagina.limitar(tamano));
        var quienes = cajas.quienes(movimientos.getContent());
        return new CajaChicaResponse(service.saldoCajaChica(), "USD",
                Pagina.de(movimientos, m -> new MovimientoResponse(m.getId(), m.getSentido(), m.getImporte(), m.getConcepto(),
                        m.getDetalle(), m.getCreadoEn(), quienes.get(m.getId()))));
    }

    @PostMapping("/caja-chica/gastos")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CAJA_CHICA')")
    public OperacionResponse gasto(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave,
            @Valid @RequestBody GastoRequest r) {
        return new OperacionResponse(service.registrarGastoCajaChica(id(jwt), r.importe(), r.detalle().trim(), clave));
    }

    @GetMapping("/admin/socios")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public List<ResumenSocio> socios() {
        return service.listarSocios();
    }

    @PostMapping("/admin/socios/{socioId}/aporte-inicial")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    public OperacionResponse aporteInicial(@AuthenticationPrincipal Jwt jwt, @PathVariable Long socioId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String clave, @Valid @RequestBody AporteInicialRequest r) {
        return new OperacionResponse(service.registrarAporteInicial(id(jwt), socioId, r.importe(), r.metodoPago(),
                r.referencia(), clave));
    }

    @GetMapping("/admin/resultados")
    @PreAuthorize("hasAnyAuthority('SCOPE_GESTIONAR_RESULTADOS', 'SCOPE_SOCIO')")
    public Pagina<ResultadoVista> resultados(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.listarResultados(Math.max(pagina, 0), Pagina.limitar(tamano)), v -> v);
    }

    @GetMapping("/admin/resultados/en-espera")
    @PreAuthorize("hasAnyAuthority('SCOPE_GESTIONAR_RESULTADOS', 'SCOPE_SOCIO')")
    public ParticipacionService.EnEsperaVista resultadosEnEspera() {
        return service.enEspera();
    }

    @PostMapping("/admin/resultados")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_RESULTADOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarResultado(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResultadoRequest r) {
        service.registrarResultado(id(jwt), r.tipo(), r.importe(), r.concepto().trim(), r.fecha());
    }

    @GetMapping("/admin/cierres")
    @PreAuthorize("hasAnyAuthority('SCOPE_GESTIONAR_RESULTADOS', 'SCOPE_SOCIO')")
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
