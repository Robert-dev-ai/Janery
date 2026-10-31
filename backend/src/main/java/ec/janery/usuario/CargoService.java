package ec.janery.usuario;

import java.util.Arrays;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import lombok.RequiredArgsConstructor;

/** Qué permisos concede cada cargo. Los cambios rigen desde el siguiente token, en 15 minutos como máximo. */
@Service
@RequiredArgsConstructor
public class CargoService {

    public record CargoVista(String codigo, String nombre, List<String> permisos) {
    }

    public record PermisoVista(String codigo, String descripcion) {
    }

    private static final String MODULO = "USUARIO";
    private static final String CARGO_PROTEGIDO = "PRESIDENCIA";
    private static final String PERMISO_PROTEGIDO = "ADMIN_USUARIOS";

    private static final String SQL_CARGOS = """
            SELECT c.codigo, c.nombre, COALESCE(string_agg(p.codigo, ',' ORDER BY p.codigo), '') AS permisos
            FROM cargos c
            LEFT JOIN cargo_permisos cp ON cp.cargo_id = c.id
            LEFT JOIN permisos p ON p.id = cp.permiso_id
            """;

    private final JdbcClient jdbc;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<CargoVista> cargos() {
        return jdbc.sql(SQL_CARGOS + " GROUP BY c.id, c.codigo, c.nombre ORDER BY c.nombre").query(CargoService::fila).list();
    }

    /** Solo los cargos vigentes del usuario: cada uno es un área de trabajo. */
    @Transactional(readOnly = true)
    public List<CargoVista> misAreas(Long usuarioId) {
        return jdbc.sql(SQL_CARGOS + """
                JOIN usuario_cargos uc ON uc.cargo_id = c.id AND uc.hasta IS NULL AND uc.usuario_id = :usuario
                GROUP BY c.id, c.codigo, c.nombre ORDER BY c.nombre
                """).param("usuario", usuarioId).query(CargoService::fila).list();
    }

    @Transactional(readOnly = true)
    public List<PermisoVista> permisos() {
        return jdbc.sql("SELECT codigo, descripcion FROM permisos ORDER BY codigo")
                .query((rs, n) -> new PermisoVista(rs.getString(1), rs.getString(2))).list();
    }

    @Transactional
    public void conceder(Long actorId, String cargo, String permiso) {
        int filas = jdbc.sql("""
                INSERT INTO cargo_permisos (cargo_id, permiso_id)
                SELECT c.id, p.id FROM cargos c, permisos p WHERE c.codigo = :cargo AND p.codigo = :permiso
                ON CONFLICT DO NOTHING
                """).param("cargo", cargo).param("permiso", permiso).update();
        if (filas > 0) {
            auditoria.registrar(actorId, "PERMISO_CONCEDIDO", MODULO, "cargo", cargo, null, permiso, null);
        } else {
            exigirExistencia(cargo, permiso);
        }
    }

    @Transactional
    public void retirar(Long actorId, String cargo, String permiso) {
        // Sin este permiso nadie podría volver a administrar cargos.
        if (CARGO_PROTEGIDO.equals(cargo) && PERMISO_PROTEGIDO.equals(permiso)) {
            throw NegocioException.conflicto("PERMISO_PROTEGIDO", "Presidencia debe conservar la administración de usuarios");
        }
        int filas = jdbc.sql("""
                DELETE FROM cargo_permisos
                WHERE cargo_id = (SELECT id FROM cargos WHERE codigo = :cargo)
                  AND permiso_id = (SELECT id FROM permisos WHERE codigo = :permiso)
                """).param("cargo", cargo).param("permiso", permiso).update();
        if (filas > 0) {
            auditoria.registrar(actorId, "PERMISO_RETIRADO", MODULO, "cargo", cargo, permiso, null, null);
        } else {
            exigirExistencia(cargo, permiso);
        }
    }

    private void exigirExistencia(String cargo, String permiso) {
        boolean existen = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM cargos WHERE codigo = :cargo)
                   AND EXISTS (SELECT 1 FROM permisos WHERE codigo = :permiso)
                """).param("cargo", cargo).param("permiso", permiso).query(Boolean.class).single();
        if (!existen) {
            throw NegocioException.noEncontrado("Cargo o permiso no encontrado");
        }
    }

    private static CargoVista fila(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String permisos = rs.getString("permisos");
        return new CargoVista(rs.getString("codigo"), rs.getString("nombre"),
                permisos.isEmpty() ? List.of() : Arrays.asList(permisos.split(",")));
    }
}
