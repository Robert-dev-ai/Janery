package ec.janery.persona;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PersonaService {

    interface PersonaRepository extends JpaRepository<Persona, Long> {
        Optional<Persona> findByUsuarioId(Long usuarioId);

        boolean existsByUsuarioId(Long usuarioId);

        boolean existsByIdentificacion(String identificacion);
    }

    private final PersonaRepository repository;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional
    public Persona completarPerfil(Long usuarioId, PersonaController.PerfilRequest r) {
        if (repository.existsByUsuarioId(usuarioId)) {
            throw NegocioException.conflicto("PERFIL_EXISTENTE", "El perfil ya fue completado");
        }
        if (repository.existsByIdentificacion(r.identificacion().trim())) {
            throw NegocioException.conflicto("IDENTIFICACION_REGISTRADA", "Esa identificación ya está registrada");
        }
        Persona persona = repository.save(new Persona(null, usuarioId, r.nombres().trim(), r.apellidos().trim(),
                r.identificacion().trim(), r.telefono().trim(), r.direccion().trim(), Instant.now(reloj)));
        auditoria.registrar(usuarioId, "PERFIL_COMPLETADO", "PERSONA", "persona", persona.getId(), null, null, null);
        return persona;
    }

    @Transactional(readOnly = true)
    public Optional<Persona> dePorUsuario(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    @Transactional(readOnly = true)
    public String nombreCompleto(Long personaId) {
        return repository.findById(personaId).map(p -> p.getNombres() + " " + p.getApellidos()).orElse(null);
    }

    @Transactional(readOnly = true)
    public Long usuarioDe(Long personaId) {
        return repository.findById(personaId).map(Persona::getUsuarioId)
                .orElseThrow(() -> NegocioException.noEncontrado("Persona no encontrada"));
    }

    @Transactional(readOnly = true)
    public boolean tienePerfil(Long usuarioId) {
        return repository.existsByUsuarioId(usuarioId);
    }
}
