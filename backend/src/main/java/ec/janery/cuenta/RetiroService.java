package ec.janery.cuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.caja.CajaService;
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

/**
 * Sacar dinero de Janery. Al pedirlo, el saldo se aparta de la cuenta principal; la caja solo cambia cuando el personal
 * confirma que entregó el dinero. Rechazar o cancelar devuelve lo apartado.
 */
@Service
@RequiredArgsConstructor
public class RetiroService {

    @Entity(name = "SolicitudRetiro")
    @Table(name = "solicitudes_retiro")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class SolicitudRetiro {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long cuentaId;
        private Long personaId;
        private BigDecimal importe;
        private String medio;
        private String banco;
        private String tipoCuenta;
        private String numeroCuenta;
        private String titularCuenta;
        private String estado;
        private String motivo;
        private String referencia;
        private String claveIdempotencia;
        private Long operacionId;
        private Long resueltaPor;
        private Instant creadoEn;
        private Instant resueltaEn;
    }

    interface SolicitudRepository extends JpaRepository<SolicitudRetiro, Long> {
        Optional<SolicitudRetiro> findByClaveIdempotencia(String clave);

        List<SolicitudRetiro> findByPersonaIdOrderByIdDesc(Long personaId);

        Page<SolicitudRetiro> findByEstado(String estado, org.springframework.data.domain.Pageable pageable);

        boolean existsByPersonaIdAndEstado(Long personaId, String estado);

        @Query("select s.personaId from SolicitudRetiro s where s.id = :id")
        Optional<Long> personaDe(@Param("id") Long id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from SolicitudRetiro s where s.id = :id")
        Optional<SolicitudRetiro> bloquear(@Param("id") Long id);
    }

    public record DatosBanco(String banco, String tipoCuenta, String numeroCuenta, String titularCuenta) {
    }

    public record Vista(Long id, String numeroCuenta, String titular, BigDecimal importe, String medio, DatosBanco banco,
            String estado, String motivo, String referencia, Instant fecha, Instant resueltaEn) {
    }

    private static final String MODULO = "CUENTA";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String ENTRADA = "ENTRADA";
    private static final String SALIDA = "SALIDA";

    private final SolicitudRepository solicitudes;
    private final CuentaService cuentas;
    private final CuentaService.CuentaRepository cuentasRepo;
    private final PersonaService personas;
    private final CajaService caja;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional
    public Vista solicitar(Long usuarioId, BigDecimal importe, String medio, DatosBanco banco, String clave) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        // Tras el bloqueo: un reintento concurrente espera aquí y encuentra la solicitud ya creada.
        Optional<SolicitudRetiro> previa = solicitudes.findByClaveIdempotencia(clave);
        if (previa.isPresent()) {
            SolicitudRetiro s = previa.get();
            if (!s.getPersonaId().equals(personaId) || s.getImporte().compareTo(importe) != 0) {
                throw NegocioException.conflicto("CLAVE_REUTILIZADA", "La clave de idempotencia ya se usó en otra operación");
            }
            return vista(s);
        }
        Long operacion = cuentas.crearOperacionInterna("RETIRO_RESERVADO", importe, null, usuarioId);
        cuentas.mover(cuenta, operacion, SALIDA, importe, "RETIRO_SOLICITADO");
        boolean aBanco = "TRANSFERENCIA".equals(medio);
        SolicitudRetiro s = solicitudes.save(new SolicitudRetiro(null, cuenta.getId(), personaId, importe, medio,
                aBanco ? banco.banco() : null, aBanco ? banco.tipoCuenta() : null, aBanco ? banco.numeroCuenta() : null,
                aBanco ? banco.titularCuenta() : null, PENDIENTE, null, null, clave, operacion, null, Instant.now(reloj), null));
        auditoria.registrar(usuarioId, "RETIRO_SOLICITADO", MODULO, "solicitud_retiro", s.getId(), null, importe.toPlainString(), medio);
        return vista(s);
    }

    @Transactional
    public void cancelar(Long usuarioId, Long id) {
        Long personaId = personaDe(usuarioId);
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaId);
        SolicitudRetiro s = bloquear(id);
        if (!s.getPersonaId().equals(personaId)) {
            throw NegocioException.noEncontrado("Solicitud no encontrada");
        }
        liberar(cuenta, s, "CANCELADA", null, usuarioId);
        auditoria.registrar(usuarioId, "RETIRO_CANCELADO", MODULO, "solicitud_retiro", id, PENDIENTE, "CANCELADA", null);
    }

    @Transactional
    public void rechazar(Long actorId, Long id, String motivo) {
        CuentaPrincipal cuenta = cuentas.cuentaActivaBloqueada(personaDeSolicitud(id));
        liberar(cuenta, bloquear(id), "RECHAZADA", motivo, actorId);
        auditoria.registrar(actorId, "RETIRO_RECHAZADO", MODULO, "solicitud_retiro", id, PENDIENTE, "RECHAZADA", motivo);
    }

    /** Se confirma cuando el dinero ya se entregó: aquí sale de la caja principal. */
    @Transactional
    public void confirmarEntrega(Long actorId, Long id, String referencia) {
        cuentas.cuentaActivaBloqueada(personaDeSolicitud(id));
        SolicitudRetiro s = bloquear(id);
        exigirPendiente(s);
        if (caja.saldoBloqueado(CajaService.PRINCIPAL).compareTo(s.getImporte()) < 0) {
            throw NegocioException.conflicto("CAJA_INSUFICIENTE", "La caja principal no tiene dinero suficiente para esta entrega");
        }
        Long operacion = cuentas.crearOperacionExterna("RETIRO", s.getImporte(), s.getMedio(), referencia, null, actorId);
        caja.registrarEnPrincipal(operacion, SALIDA, s.getImporte(), "RETIRO_CUENTA");
        s.setEstado("ENTREGADA");
        s.setReferencia(referencia);
        resolver(s, actorId);
        auditoria.registrar(actorId, "RETIRO_ENTREGADO", MODULO, "solicitud_retiro", id, PENDIENTE, "ENTREGADA", referencia);
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

    private void liberar(CuentaPrincipal cuenta, SolicitudRetiro s, String estado, String motivo, Long actorId) {
        exigirPendiente(s);
        Long operacion = cuentas.crearOperacionInterna("RETIRO_LIBERADO", s.getImporte(), null, actorId);
        cuentas.mover(cuenta, operacion, ENTRADA, s.getImporte(), "RETIRO_DEVUELTO");
        s.setEstado(estado);
        s.setMotivo(motivo);
        resolver(s, actorId);
    }

    private void resolver(SolicitudRetiro s, Long actorId) {
        s.setResueltaPor(actorId);
        s.setResueltaEn(Instant.now(reloj));
    }

    private Long personaDeSolicitud(Long id) {
        return solicitudes.personaDe(id).orElseThrow(() -> NegocioException.noEncontrado("Solicitud no encontrada"));
    }

    /** Se relee bajo bloqueo: dos confirmaciones simultáneas no pueden sacar el dinero dos veces de la caja. */
    private SolicitudRetiro bloquear(Long id) {
        return solicitudes.bloquear(id).orElseThrow(() -> NegocioException.noEncontrado("Solicitud no encontrada"));
    }

    private static void exigirPendiente(SolicitudRetiro s) {
        if (!PENDIENTE.equals(s.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Esta solicitud ya se resolvió");
        }
    }

    private Long personaDe(Long usuarioId) {
        return personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.conflicto(
                "PERFIL_INCOMPLETO", "Complete sus datos personales")).getId();
    }

    private Vista vista(SolicitudRetiro s) {
        DatosBanco banco = s.getBanco() == null ? null
                : new DatosBanco(s.getBanco(), s.getTipoCuenta(), s.getNumeroCuenta(), s.getTitularCuenta());
        String numero = cuentasRepo.findById(s.getCuentaId()).map(CuentaPrincipal::getNumero).orElse(null);
        return new Vista(s.getId(), numero, personas.nombreCompleto(s.getPersonaId()), s.getImporte(), s.getMedio(), banco,
                s.getEstado(), s.getMotivo(), s.getReferencia(), s.getCreadoEn(), s.getResueltaEn());
    }

    /** Con saldo cero en la cuenta aún puede haber dinero apartado: se resuelve antes de cerrar. */
    @Component
    @RequiredArgsConstructor
    static class RetirosCierre implements RestriccionCierre {
        private final SolicitudRepository solicitudes;

        @Override
        public void verificar(Long personaId) {
            if (solicitudes.existsByPersonaIdAndEstado(personaId, PENDIENTE)) {
                throw NegocioException.conflicto("RETIRO_PENDIENTE", "Tiene un retiro pendiente de entrega");
            }
        }
    }
}
