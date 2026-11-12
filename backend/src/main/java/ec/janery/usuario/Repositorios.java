package ec.janery.usuario;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

final class Repositorios {

    private Repositorios() {
    }

    interface UsuarioRepository extends JpaRepository<Usuario, Long> {
        Optional<Usuario> findByCorreo(String correo);

        boolean existsByCorreo(String correo);

        @org.springframework.data.jpa.repository.Query(value = """
                SELECT u.* FROM usuarios u JOIN personas p ON p.usuario_id = u.id WHERE p.identificacion = :identificacion
                """, nativeQuery = true)
        Optional<Usuario> findByIdentificacion(@org.springframework.data.repository.query.Param("identificacion") String identificacion);

        @Query(value = """
                SELECT DISTINCT p.codigo FROM usuario_cargos uc
                JOIN cargo_permisos cp ON cp.cargo_id = uc.cargo_id
                JOIN permisos p ON p.id = cp.permiso_id
                WHERE uc.usuario_id = :usuarioId AND uc.hasta IS NULL
                """, nativeQuery = true)
        List<String> permisosVigentes(@Param("usuarioId") Long usuarioId);

        /** Los permisos de sus cargos, y SOCIO si lo es, en una sola consulta. */
        @Query(value = """
                SELECT DISTINCT p.codigo FROM usuario_cargos uc
                JOIN cargo_permisos cp ON cp.cargo_id = uc.cargo_id
                JOIN permisos p ON p.id = cp.permiso_id
                WHERE uc.usuario_id = :usuarioId AND uc.hasta IS NULL
                UNION ALL
                SELECT 'SOCIO' WHERE EXISTS (SELECT 1 FROM socios s JOIN personas pe ON pe.id = s.persona_id
                                             WHERE pe.usuario_id = :usuarioId AND s.estado = 'ACTIVO')
                """, nativeQuery = true)
        List<String> alcances(@Param("usuarioId") Long usuarioId);

        @Query(value = """
                SELECT uc.usuario_id, c.codigo FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                WHERE uc.usuario_id IN (:usuarioIds) AND uc.hasta IS NULL ORDER BY c.codigo
                """, nativeQuery = true)
        List<Object[]> cargosVigentesDe(@Param("usuarioIds") java.util.Collection<Long> usuarioIds);

        @Query(value = """
                SELECT c.codigo FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                WHERE uc.usuario_id = :usuarioId AND uc.hasta IS NULL ORDER BY c.codigo
                """, nativeQuery = true)
        List<String> cargosVigentes(@Param("usuarioId") Long usuarioId);

        @Query(value = "SELECT id FROM cargos WHERE codigo = :codigo", nativeQuery = true)
        Optional<Long> idDeCargo(@Param("codigo") String codigo);
    }

    interface TokenRefrescoRepository extends JpaRepository<TokenRefresco, Long> {
        Optional<TokenRefresco> findByHashToken(String hashToken);

        @Modifying
        @Query("""
                UPDATE TokenRefresco t SET t.revocadoEn = :ahora
                WHERE t.usuarioId = :usuarioId AND t.revocadoEn IS NULL AND t.hashToken <> :hashConservado
                """)
        int revocarOtros(@Param("usuarioId") Long usuarioId, @Param("hashConservado") String hashConservado,
                @Param("ahora") Instant ahora);

        /** 0 si otra petición ya lo usó: un token de refresco sirve una sola vez. */
        @Modifying
        @Query("UPDATE TokenRefresco t SET t.revocadoEn = :ahora WHERE t.id = :id AND t.revocadoEn IS NULL")
        int revocarSiVigente(@Param("id") Long id, @Param("ahora") Instant ahora);
    }

    interface RestablecimientoRepository extends JpaRepository<SolicitudRestablecimiento, Long> {
        boolean existsByUsuarioIdAndEstado(Long usuarioId, String estado);

        List<SolicitudRestablecimiento> findByUsuarioIdAndEstado(Long usuarioId, String estado);

        @Query("select s.usuarioId from SolicitudRestablecimiento s where s.estado = 'PENDIENTE'")
        java.util.Set<Long> usuariosPendientes();
    }

    interface UsuarioCargoRepository extends JpaRepository<UsuarioCargo, Long> {
        Optional<UsuarioCargo> findByUsuarioIdAndCargoIdAndHastaIsNull(Long usuarioId, Long cargoId);
    }
}
