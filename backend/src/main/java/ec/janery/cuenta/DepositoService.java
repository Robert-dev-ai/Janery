package ec.janery.cuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.persona.PersonaService;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** La persona avisa que deposita. Nada cambia hasta que el personal confirma que el dinero llegó a la caja. */
@Service
@RequiredArgsConstructor
public class DepositoService {

    @Entity(name = "SolicitudDeposito")
    @Table(name = "solicitudes_deposito")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class SolicitudDeposito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long cuentaId;
        private Long personaId;
        private BigDecimal importe;
        private String medio;
        private String referencia;
        private String estado;
        private String motivo;
        private String claveIdempotencia;
        private Long operacionId;
        private Long resueltaPor;
        private Instant creadoEn;
        private Instant resueltaEn;
    }

    interface SolicitudRepository extends JpaRepository<SolicitudDeposito, Long> {
        Optional<SolicitudDeposito> findByClaveIdempotencia(String clave);

        List<SolicitudDeposito> findByPersonaIdOrderByIdDesc(Long personaId);

        Page<SolicitudDeposito> findByEstado(String estado, Pageable pageable);

        boolean existsByPersonaIdAndEstado(Long personaId, String estado);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from SolicitudDeposito s where s.id = :id")
        Optional<SolicitudDeposito> bloquear(@Param("id") Long id);
    }

    public record Vista(Long id, String numeroCuenta, String titular, BigDecimal importe, String medio, String referencia,
            String estado, String motivo, Instant fecha, Instant resueltaEn) {
    }

    private static final String MODULO = "CUENTA";
    private static final String PENDIENTE = "PENDIENTE";

    private final SolicitudRepository solicitudes;
    private final CuentaService cuentas;
    private final CuentaService.CuentaRepository cuentasRepo;
    private final PersonaService personas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional
    public Vista solicitar(Long usuarioId, BigDecimal importe, String medio, String referencia, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        Optional<SolicitudDeposito> previa = solicitudes.findByClaveIdempotencia(clave);
        if (previa.isPresent()) {
            SolicitudDeposito s = previa.get();
            if (!s.getPersonaId().equals(personaId) || s.getImporte().compareTo(importe) != 0) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return vista(s);
        }
        SolicitudDeposito s = solicitudes.save(new SolicitudDeposito(null, cuenta.getId(), personaId, importe, medio, referencia,
                PENDIENTE, null, clave, null, null, Instant.now(reloj), null));
        auditoria.registrar(usuarioId, "DEPOSITO_SOLICITADO", MODULO, "solicitud_deposito", s.getId(), null, importe.toPlainString(), medio);
        return vista(s);
    }

    @Transactional
    public void cancelar(Long usuarioId, Long id) {
        Long personaId = personaDe(usuarioId);
        SolicitudDeposito s = pendienteBloqueada(id);
        if (!s.getPersonaId().equals(personaId)) {
            throw NegocioException.noEncontrado("Solicitud no encontrada");
        }
        resolver(s, "CANCELADA", null, usuarioId);
        auditoria.registrar(usuarioId, "DEPOSITO_CANCELADO", MODULO, "solicitud_deposito", id, PENDIENTE, "CANCELADA", null);
    }

    @Transactional
    public void rechazar(Long actorId, Long id, String motivo) {
        resolver(pendienteBloqueada(id), "RECHAZADA", motivo, actorId);
        auditoria.registrar(actorId, "DEPOSITO_RECHAZADO", MODULO, "solicitud_deposito", id, PENDIENTE, "RECHAZADA", motivo);
    }

    /** Se confirma cuando el dinero ya se recibió: aquí entra a la cuenta y a la caja principal. */
    @Transactional
    public void confirmar(Long actorId, Long id) {
        SolicitudDeposito s = pendienteBloqueada(id);
        Long operacion = cuentas.depositar(actorId, s.getCuentaId(), s.getImporte(), s.getMedio(), s.getReferencia(),
                "solicitud-deposito-" + id);
        s.setOperacionId(operacion);
        resolver(s, "CONFIRMADA", null, actorId);
        auditoria.registrar(actorId, "DEPOSITO_CONFIRMADO", MODULO, "solicitud_deposito", id, PENDIENTE, "CONFIRMADA", null);
    }

    @Transactional(readOnly = true)
    public List<Vista> misSolicitudes(Long usuarioId) {
        return solicitudes.findByPersonaIdOrderByIdDesc(personaDe(usuarioId)).stream().map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public Page<Vista> listar(String estado, int pagina, int tamano) {
        PageRequest orden = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id"));
        return (estado == null ? solicitudes.findAll(orden) : solicitudes.findByEstado(estado, orden)).map(this::vista);
    }

    private SolicitudDeposito pendienteBloqueada(Long id) {
        SolicitudDeposito s = solicitudes.bloquear(id).orElseThrow(() -> NegocioException.noEncontrado("Solicitud no encontrada"));
        if (!PENDIENTE.equals(s.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Esta solicitud ya se resolvió");
        }
        return s;
    }

    private void resolver(SolicitudDeposito s, String estado, String motivo, Long actorId) {
        s.setEstado(estado);
        s.setMotivo(motivo);
        s.setResueltaPor(actorId);
        s.setResueltaEn(Instant.now(reloj));
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "Complete sus datos personales")).getId();
    }

    private Vista vista(SolicitudDeposito s) {
        String numero = cuentasRepo.findById(s.getCuentaId()).map(CuentaPrincipal::getNumero).orElse(null);
        return new Vista(s.getId(), numero, personas.nombreCompleto(s.getPersonaId()), s.getImporte(), s.getMedio(), s.getReferencia(),
                s.getEstado(), s.getMotivo(), s.getCreadoEn(), s.getResueltaEn());
    }

    /** Un depósito por confirmar acabaría en una cuenta ya cerrada: se resuelve antes. */
    @Component
    @RequiredArgsConstructor
    static class DepositosCierre implements RestriccionCierre {
        private final SolicitudRepository solicitudes;

        @Override
        public void verificar(Long personaId) {
            if (solicitudes.existsByPersonaIdAndEstado(personaId, PENDIENTE)) {
                throw NegocioException.conflicto("DEPOSITO_PENDIENTE", "Tiene un depósito pendiente de confirmar");
            }
        }
    }
}
