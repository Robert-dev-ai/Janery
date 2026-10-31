package ec.janery.auditoria;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    @Repository
    interface AuditoriaRepository extends JpaRepository<AuditoriaEvento, Long> {
    }

    private final AuditoriaRepository repository;
    private final Clock reloj;

    /** Se une a la transacción en curso: la operación y su auditoría se confirman juntas. No pasar secretos. */
    @Transactional
    public void registrar(Long actorId, String accion, String modulo, String entidad, Object entidadId,
            String antes, String despues, String motivo) {
        repository.save(new AuditoriaEvento(null, actorId, accion, modulo, entidad,
                entidadId == null ? null : entidadId.toString(), antes, despues, motivo, Instant.now(reloj)));
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaEvento> listar(int pagina, int tamano) {
        return repository.findAll(PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
    }
}
