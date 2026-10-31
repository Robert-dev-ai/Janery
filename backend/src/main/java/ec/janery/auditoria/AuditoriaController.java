package ec.janery.auditoria;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.Pagina;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    public record EventoResponse(Long id, Long actorId, String accion, String modulo, String entidad, String entidadId,
            String antes, String despues, String motivo, Instant fecha) {
    }

    private final AuditoriaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_CONSULTAR_AUDITORIA')")
    public Pagina<EventoResponse> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.listar(Math.max(pagina, 0), Pagina.limitar(tamano)),
                e -> new EventoResponse(e.getId(), e.getActorId(), e.getAccion(), e.getModulo(), e.getEntidad(),
                        e.getEntidadId(), e.getAntes(), e.getDespues(), e.getMotivo(), e.getCreadoEn()));
    }
}
