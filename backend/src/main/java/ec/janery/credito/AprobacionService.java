package ec.janery.credito;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.common.NegocioException;

/**
 * Decisión del propietario: un crédito se aprueba o se rechaza con la autorización de tres áreas: presidencia o
 * vicepresidencia, tesorería y comité de crédito. Cada persona vota una vez, por una de sus áreas. Con los tres votos
 * se decide: se aprueba solo si los tres aprueban.
 */
@Service
public class AprobacionService {

    public record Voto(String area, String nombre, String decision, String motivo, Instant fecha) {
    }

    public enum Resultado { PENDIENTE, APROBADA, RECHAZADA }

    public static final String CREDITO = "CREDITO";
    public static final String CUPO = "CUPO";
    static final List<String> AREAS = List.of("DIRECCION", "TESORERIA", "COMITE_CREDITO");
    private static final Map<String, String> AREA_DE_CARGO = Map.of("PRESIDENCIA", "DIRECCION", "VICEPRESIDENCIA", "DIRECCION",
            "TESORERIA", "TESORERIA", "COMITE_CREDITO", "COMITE_CREDITO");

    private final JdbcClient jdbc;
    private final Clock reloj;
    /** Las pruebas de cálculo lo apagan: allí un solo voto decide, como antes de esta regla. */
    private final boolean conjunta;

    AprobacionService(JdbcClient jdbc, Clock reloj, @Value("${janery.creditos.aprobacion-conjunta:true}") boolean conjunta) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.conjunta = conjunta;
    }

    /** Requiere que quien llama ya bloqueó la solicitud: así dos votos simultáneos no se pisan. */
    @Transactional
    public Resultado votar(Long actorId, String tipo, Long referenciaId, boolean aprueba, String motivo) {
        if (!conjunta) {
            return aprueba ? Resultado.APROBADA : Resultado.RECHAZADA;
        }
        Set<String> areas = jdbc.sql("""
                SELECT c.codigo FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                WHERE uc.usuario_id = :usuario AND uc.hasta IS NULL
                """).param("usuario", actorId).query(String.class).list().stream()
                .map(AREA_DE_CARGO::get).filter(a -> a != null).collect(Collectors.toSet());
        if (areas.isEmpty()) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "SIN_AREA_DE_APROBACION",
                    "Solo presidencia o vicepresidencia, tesorería y comité de crédito deciden los créditos");
        }
        List<Voto> previos = votos(tipo, List.of(referenciaId)).getOrDefault(referenciaId, List.of());
        boolean yaVoto = jdbc.sql("""
                SELECT count(*) > 0 FROM aprobaciones_credito WHERE tipo = :tipo AND referencia_id = :ref AND usuario_id = :usuario
                """).param("tipo", tipo).param("ref", referenciaId).param("usuario", actorId).query(Boolean.class).single();
        if (yaVoto) {
            throw NegocioException.conflicto("YA_VOTO", "Usted ya dio su voto en esta solicitud");
        }
        Set<String> votadas = new HashSet<>(previos.stream().map(Voto::area).toList());
        String area = AREAS.stream().filter(areas::contains).filter(a -> !votadas.contains(a)).findFirst()
                .orElseThrow(() -> NegocioException.conflicto("AREA_YA_VOTO", "Su área ya votó esta solicitud"));
        jdbc.sql("""
                INSERT INTO aprobaciones_credito (tipo, referencia_id, area, usuario_id, decision, motivo, creado_en)
                VALUES (:tipo, :ref, :area, :usuario, :decision, :motivo, :ahora)
                """).param("tipo", tipo).param("ref", referenciaId).param("area", area).param("usuario", actorId)
                .param("decision", aprueba ? "APROBAR" : "RECHAZAR").param("motivo", motivo)
                .param("ahora", java.sql.Timestamp.from(Instant.now(reloj))).update();
        List<Voto> todos = votos(tipo, List.of(referenciaId)).get(referenciaId);
        if (todos.size() < AREAS.size()) {
            return Resultado.PENDIENTE;
        }
        return todos.stream().allMatch(v -> "APROBAR".equals(v.decision())) ? Resultado.APROBADA : Resultado.RECHAZADA;
    }

    /** Motivos de quienes rechazaron, para mostrarlos a la persona. */
    @Transactional(readOnly = true)
    public String motivoRechazo(String tipo, Long referenciaId, String ultimo) {
        if (!conjunta) {
            return ultimo;
        }
        return votos(tipo, List.of(referenciaId)).getOrDefault(referenciaId, List.of()).stream()
                .filter(v -> "RECHAZAR".equals(v.decision()) && v.motivo() != null)
                .map(v -> nombreArea(v.area()) + ": " + v.motivo()).collect(Collectors.joining(" · "));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Voto>> votos(String tipo, Collection<Long> referencias) {
        if (referencias.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                SELECT a.referencia_id, a.area, COALESCE(%s, u.correo),
                       a.decision, a.motivo, a.creado_en
                FROM aprobaciones_credito a JOIN usuarios u ON u.id = a.usuario_id LEFT JOIN personas p ON p.usuario_id = u.id
                WHERE a.tipo = :tipo AND a.referencia_id IN (:refs) ORDER BY a.id
                """.formatted(ec.janery.auditoria.HistorialSql.nombreCorto("p"))).param("tipo", tipo).param("refs", referencias)
                .query((rs, n) -> Map.entry(rs.getLong(1), new Voto(rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant())))
                .list().stream().collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    public boolean conjunta() {
        return conjunta;
    }

    static String nombreArea(String area) {
        return switch (area) {
            case "DIRECCION" -> "Presidencia";
            case "TESORERIA" -> "Tesorería";
            default -> "Comité de crédito";
        };
    }
}
