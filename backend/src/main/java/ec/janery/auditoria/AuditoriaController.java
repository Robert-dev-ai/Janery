package ec.janery.auditoria;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

    /** Además del registro tal cual, su lectura en palabras: quién, con qué cargos, qué hizo, a quién y con qué código. */
    public record EventoResponse(Long id, Long actorId, String accion, String modulo, String entidad, String entidadId,
            String antes, String despues, String motivo, Instant fecha, String quien, String cargos, String descripcion,
            String afectado, String codigo) {
    }

    private record Legible(String quien, String cargos, String descripcion, String afectado, String codigo) {
    }

    private final AuditoriaService service;
    private final JdbcClient jdbc;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_CONSULTAR_AUDITORIA')")
    @Transactional(readOnly = true)
    public Pagina<EventoResponse> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var eventos = service.listar(Math.max(pagina, 0), Pagina.limitar(tamano));
        Map<Long, Legible> legibles = legibles(eventos.getContent().stream().map(AuditoriaEvento::getId).toList());
        return Pagina.de(eventos, e -> {
            Legible l = legibles.getOrDefault(e.getId(), new Legible(null, null, e.getAccion(), null, null));
            return new EventoResponse(e.getId(), e.getActorId(), e.getAccion(), e.getModulo(), e.getEntidad(),
                    e.getEntidadId(), e.getAntes(), e.getDespues(), e.getMotivo(), e.getCreadoEn(), l.quien(), l.cargos(),
                    l.descripcion(), l.afectado(), l.codigo());
        });
    }

    private Map<Long, Legible> legibles(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT e.id, " + HistorialSql.COLUMNAS + HistorialSql.desde() + " WHERE e.id IN (:ids)")
                .param("ids", ids)
                .query((rs, n) -> Map.entry(rs.getLong("id"), new Legible(rs.getString("Quién"), rs.getString("Cargos"),
                        rs.getString("Qué hizo"), rs.getString("A quién"), rs.getString("Código"))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
