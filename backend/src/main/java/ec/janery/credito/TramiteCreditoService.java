package ec.janery.credito;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.persona.PersonaController;
import ec.janery.persona.PersonaService;
import ec.janery.socio.SocioService;
import lombok.RequiredArgsConstructor;

/**
 * Decisión del propietario: tesorería tramita el crédito de quien llega en persona. Es la misma solicitud que haría la
 * persona desde su panel, con las mismas reglas; queda auditado quién la tramitó. Después se aprueba con los tres votos.
 */
@Service
@RequiredArgsConstructor
public class TramiteCreditoService {

    public record PersonaTramite(Long usuarioId, String nombre, String identificacion, boolean socio, String cuenta) {
    }

    private static final String MODULO = "CREDITO";

    private final JdbcClient jdbc;
    private final CreditoService creditos;
    private final PersonaService personas;
    private final SocioService socios;
    private final AuditoriaService auditoria;

    /** Solo personas con cuenta principal activa: el crédito se desembolsa en ella. */
    @Transactional(readOnly = true)
    public List<PersonaTramite> buscar(String texto) {
        String filtro = "%" + (texto == null ? "" : texto.trim().toLowerCase()) + "%";
        return jdbc.sql("""
                SELECT p.usuario_id, p.nombres || ' ' || p.apellidos, p.identificacion,
                       EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO'), c.numero
                FROM personas p JOIN cuentas_principales c ON c.persona_id = p.id AND c.estado = 'ACTIVA'
                WHERE lower(p.nombres || ' ' || p.apellidos) LIKE :filtro OR p.identificacion LIKE :filtro OR lower(c.numero) LIKE :filtro
                ORDER BY p.apellidos, p.nombres LIMIT 20
                """).param("filtro", filtro)
                .query((rs, n) -> new PersonaTramite(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5)))
                .list();
    }

    @Transactional(readOnly = true)
    public PersonaController.PerfilResponse perfil(Long usuarioId) {
        return PersonaController.PerfilResponse.de(personas.dePorUsuario(usuarioId)
                .orElseThrow(() -> NegocioException.noEncontrado("Persona no encontrada")));
    }

    /** Los datos que la persona dicta o corrige en la ventanilla. */
    @Transactional
    public PersonaController.PerfilResponse actualizarDatos(Long actorId, Long usuarioId, String direccion,
            PersonaController.DatosCreditoRequest datos) {
        var persona = personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.noEncontrado("Persona no encontrada"));
        personas.actualizarContacto(usuarioId, new PersonaController.ContactoRequest(persona.getTelefono(), direccion));
        var actualizada = personas.actualizarDatosCredito(usuarioId, datos);
        auditoria.registrar(actorId, "DATOS_CREDITO_TRAMITADOS", MODULO, "persona", persona.getId(), null, null, null);
        return PersonaController.PerfilResponse.de(actualizada);
    }

    @Transactional(readOnly = true)
    public CreditoService.Productos productos(Long usuarioId) {
        return creditos.productos(usuarioId);
    }

    @Transactional(readOnly = true)
    public CreditoService.Simulacion simular(Long usuarioId, String modalidad, BigDecimal importe, int plazo) {
        return creditos.simular(usuarioId, modalidad, importe, plazo);
    }

    @Transactional
    public Long solicitarLetras(Long actorId, Long usuarioId, String modalidad, BigDecimal importe, int plazo,
            String origenEncaje, Long ahorroEncajeId, String clave) {
        boolean repetida = creditos.claveDeSolicitudUsada(clave);
        Long id = creditos.solicitarLetras(usuarioId, modalidad, importe, plazo, origenEncaje, ahorroEncajeId, clave);
        if (repetida) {
            return id;
        }
        auditoria.registrar(actorId, "CREDITO_TRAMITADO", MODULO, "credito", id, null, importe.toPlainString(), null);
        return id;
    }

    /** Socio: retiro de su cupo emergente. Cliente: crédito emergente. */
    @Transactional
    public Long solicitarEmergente(Long actorId, Long usuarioId, BigDecimal importe, String clave) {
        boolean socio = socios.usuarioEsSocio(usuarioId);
        Long id = socio ? creditos.solicitarCupo(usuarioId, importe, null, clave)
                : creditos.solicitarEmergenteCliente(usuarioId, importe, clave);
        auditoria.registrar(actorId, "CREDITO_TRAMITADO", MODULO, socio ? "solicitud_cupo" : "credito", id, null,
                importe.toPlainString(), null);
        return id;
    }
}
