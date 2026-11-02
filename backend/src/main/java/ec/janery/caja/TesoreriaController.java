package ec.janery.caja;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.Pagina;
import ec.janery.socio.ParticipacionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tesoreria")
@PreAuthorize("hasAnyAuthority('SCOPE_CONSULTAR_CAJA', 'SCOPE_SOCIO')")
@RequiredArgsConstructor
public class TesoreriaController {

    public record MovimientoResponse(Long id, String sentido, BigDecimal importe, String concepto, Instant fecha, String quien) {
    }

    public record TesoreriaResponse(BigDecimal saldo, String moneda, Pagina<MovimientoResponse> movimientos,
            java.util.List<CajaService.MesVariacion> meses) {
    }

    public record EstadoResponse(EstadoCajaService.EstadoCaja caja, ParticipacionService.EnEsperaVista enEspera) {
    }

    private final CajaService service;
    private final EstadoCajaService estado;
    private final ParticipacionService participacion;

    @GetMapping("/estado")
    public EstadoResponse estado() {
        return new EstadoResponse(estado.consultar(), participacion.enEspera());
    }

    @GetMapping
    public TesoreriaResponse consultar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var movimientos = service.movimientosPrincipal(Math.max(pagina, 0), Pagina.limitar(tamano));
        var quienes = service.quienes(movimientos.getContent());
        return new TesoreriaResponse(service.saldoPrincipal(), "USD",
                Pagina.de(movimientos, m -> new MovimientoResponse(m.getId(), m.getSentido(), m.getImporte(), m.getConcepto(),
                        m.getCreadoEn(), quienes.get(m.getId()))), service.variacionMensualPrincipal());
    }
}
