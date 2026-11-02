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
                r.identificacion().trim(), r.telefono().trim(), r.direccion() == null || r.direccion().isBlank() ? null : r.direccion().trim(),
                Instant.now(reloj), null, null, null, null, null, null, null, null, null, null, null));
        auditoria.registrar(usuarioId, "PERFIL_COMPLETADO", "PERSONA", "persona", persona.getId(), null, null, null);
        return persona;
    }

    /** Nombres e identificación quedan fijos: son los datos con los que se abrió la cuenta. */
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
