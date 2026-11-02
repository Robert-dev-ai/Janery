package ec.janery.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.tiemporeal.EventosService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    @Repository
    interface AuditoriaRepository extends JpaRepository<AuditoriaEvento, Long> {
    }

    /** Se auditan, pero no cambian nada que una pantalla muestre. */
    private static final Set<String> ACCIONES_SIN_CAMBIOS = Set.of("LOGIN", "LOGIN_FALLIDO", "LOGOUT", "INFORME_EXPORTADO",
            "CONTRASENA_CAMBIADA", "CONTRASENA_FALLIDA", "SESIONES_CERRADAS");

    private final AuditoriaRepository repository;
    private final Clock reloj;
    private final EventosService eventos;

    /**
     * Se une a la transacción en curso: la operación y su auditoría se confirman juntas. No pasar secretos.
     * Toda acción relevante pasa por aquí, y por eso aquí se avisa a las pantallas abiertas.
     */
    @Transactional
    public void registrar(Long actorId, String accion, String modulo, String entidad, Object entidadId,
            String antes, String despues, String motivo) {
        repository.save(new AuditoriaEvento(null, actorId, accion, modulo, entidad,
                entidadId == null ? null : entidadId.toString(), antes, despues, motivo, Instant.now(reloj)));
        if (!ACCIONES_SIN_CAMBIOS.contains(accion)) {
            eventos.publicar(modulo);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaEvento> listar(int pagina, int tamano) {
        return repository.findAll(PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
    }
}
