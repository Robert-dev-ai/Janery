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
@PreAuthorize("hasAuthority('SCOPE_CONSULTAR_CAJA')")
@RequiredArgsConstructor
public class TesoreriaController {

    public record MovimientoResponse(Long id, String sentido, BigDecimal importe, String concepto, Instant fecha) {
    }

    public record TesoreriaResponse(BigDecimal saldo, String moneda, Pagina<MovimientoResponse> movimientos) {
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
        return new TesoreriaResponse(service.saldoPrincipal(), "USD",
                Pagina.de(service.movimientosPrincipal(Math.max(pagina, 0), Pagina.limitar(tamano)),
                        m -> new MovimientoResponse(m.getId(), m.getSentido(), m.getImporte(), m.getConcepto(),
                                m.getCreadoEn())));
    }
}
