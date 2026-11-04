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

        java.util.List<Persona> findByUsuarioIdIn(java.util.Collection<Long> usuarioIds);

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
        Persona persona = repository.save(new Persona(null, usuarioId, Persona.formatearNombre(r.nombres()),
                Persona.formatearNombre(r.apellidos()),
                r.identificacion().trim(), r.telefono().trim(), r.direccion() == null || r.direccion().isBlank() ? null : r.direccion().trim(),
                Instant.now(reloj), null, null, null, null, null, null, null, null, null, null, null));
        auditoria.registrar(usuarioId, "PERFIL_COMPLETADO", "PERSONA", "persona", persona.getId(), null, null, null);
        return persona;
    }

    /** Nombres e identificación quedan fijos: todo lo de la persona está vinculado a ellos. */
    @Transactional
    public Persona actualizarContacto(Long usuarioId, PersonaController.ContactoRequest r) {
        Persona persona = repository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> NegocioException.noEncontrado("Perfil no completado"));
        persona.actualizarContacto(r.telefono().trim(), r.direccion().trim());
        auditoria.registrar(usuarioId, "PERFIL_ACTUALIZADO", "PERSONA", "persona", persona.getId(), null, null, null);
        return persona;
    }

    @Transactional
    public Persona actualizarDatosCredito(Long usuarioId, PersonaController.DatosCreditoRequest r) {
        Persona persona = repository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> NegocioException.noEncontrado("Perfil no completado"));
        if (r.fechaNacimiento().isAfter(java.time.LocalDate.now(reloj).minusYears(18))) {
            throw new NegocioException(org.springframework.http.HttpStatus.BAD_REQUEST, "MENOR_DE_EDAD",
                    "Debe ser mayor de edad: revise su fecha de nacimiento");
        }
        persona.actualizarDatosCredito(r);
        auditoria.registrar(usuarioId, "DATOS_CREDITO_ACTUALIZADOS", "PERSONA", "persona", persona.getId(), null, null, null);
        return persona;
    }

    @Transactional(readOnly = true)
    public boolean datosCreditoCompletos(Long personaId) {
        return repository.findById(personaId).map(Persona::datosCreditoCompletos).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Persona> dePorUsuario(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    /**
     * Trae varias personas en una consulta. Dentro de la misma transacción, nombreCompleto y usuarioDe las toman luego de
     * memoria: así un listado no hace un viaje a la base por fila.
     */
    @Transactional(readOnly = true)
    public void precargar(java.util.Collection<Long> personaIds) {
        java.util.Set<Long> ids = new java.util.HashSet<>(personaIds);
        ids.remove(null);
        if (!ids.isEmpty()) {
            repository.findAllById(ids);
        }
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, Persona> porUsuarios(java.util.Collection<Long> usuarioIds) {
        return usuarioIds.isEmpty() ? java.util.Map.of() : repository.findByUsuarioIdIn(usuarioIds).stream()
                .collect(java.util.stream.Collectors.toMap(Persona::getUsuarioId, p -> p));
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
