package ec.janery.socio;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.persona.PersonaService;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/** Condición de socio. Aportes y participación llegan en la fase 5; admisión y salida, más adelante. */
@Service
@RequiredArgsConstructor
public class SocioService {

    @Entity(name = "Socio")
    @Table(name = "socios")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Socio {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long personaId;
        private Instant desde;
        private String estado;
    }

    interface SocioRepository extends JpaRepository<Socio, Long> {
        boolean existsByPersonaIdAndEstado(Long personaId, String estado);

        java.util.Optional<Socio> findByPersonaIdAndEstado(Long personaId, String estado);

        java.util.List<Socio> findByEstadoOrderById(String estado);

        @org.springframework.data.jpa.repository.Query(
                "select s.personaId from Socio s where s.personaId in :personaIds and s.estado = 'ACTIVO'")
        java.util.Set<Long> personasActivas(
                @org.springframework.data.repository.query.Param("personaIds") java.util.Collection<Long> personaIds);

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select s from Socio s where s.id = :id")
        java.util.Optional<Socio> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
    }

    private final SocioRepository repository;
    private final PersonaService personas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public boolean esSocio(Long personaId) {
        return repository.existsByPersonaIdAndEstado(personaId, "ACTIVO");
    }

    /** De esas personas, las que son socias activas. Una consulta para todo un listado. */
    @Transactional(readOnly = true)
    public java.util.Set<Long> personasActivas(java.util.Collection<Long> personaIds) {
        return personaIds.isEmpty() ? java.util.Set.of() : repository.personasActivas(personaIds);
    }

    @Transactional(readOnly = true)
    public boolean usuarioEsSocio(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).map(p -> esSocio(p.getId())).orElse(false);
    }

    @Transactional(readOnly = true)
    Socio deUsuario(Long usuarioId) {
        return personas.dePorUsuario(usuarioId)
                .flatMap(p -> repository.findByPersonaIdAndEstado(p.getId(), "ACTIVO"))
                .orElseThrow(() -> NegocioException.noEncontrado("No figura como socio"));
    }

    @Transactional(readOnly = true)
    java.util.List<Socio> activos() {
        return repository.findByEstadoOrderById("ACTIVO");
    }

    @Transactional(readOnly = true)
    Socio porId(Long socioId) {
        return repository.findById(socioId).orElseThrow(() -> NegocioException.noEncontrado("Socio no encontrado"));
    }

    /** Serializa las operaciones sobre las ganancias de un socio. Requiere transacción. */
    @Transactional
    Socio bloquear(Long socioId) {
        return repository.bloquear(socioId).orElseThrow(() -> NegocioException.noEncontrado("Socio no encontrado"));
    }

    @Transactional(readOnly = true)
    Long personaDe(Long socioId) {
        return repository.findById(socioId).map(Socio::getPersonaId)
                .orElseThrow(() -> NegocioException.noEncontrado("Socio no encontrado"));
    }

    @Transactional
    public void registrar(Long actorId, Long usuarioId) {
        Long personaId = personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "La persona debe completar sus datos antes de ser socia")).getId();
        if (esSocio(personaId)) {
            throw NegocioException.conflicto("SOCIO_EXISTENTE", "Esta persona ya es socia");
        }
        Socio socio = repository.save(new Socio(null, personaId, Instant.now(reloj), "ACTIVO"));
        auditoria.registrar(actorId, "SOCIO_REGISTRADO", "SOCIO", "socio", socio.getId(), null, "ACTIVO", null);
    }
}
