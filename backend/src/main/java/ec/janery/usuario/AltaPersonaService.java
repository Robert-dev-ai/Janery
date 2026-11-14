package ec.janery.usuario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.cuenta.CuentaService;
import ec.janery.persona.PersonaController;
import lombok.RequiredArgsConstructor;

/**
 * Decisión del propietario (13/11/2026): la presidencia crea la cuenta de quien no se registra solo. Queda con sus datos,
 * su cuenta principal activa (la apertura se cobra en efectivo, como en ventanilla) y una contraseña temporal. Así ya se
 * le pueden tramitar o registrar créditos.
 */
@Service
@RequiredArgsConstructor
public class AltaPersonaService {

    public record Alta(Long usuarioId, String cuenta, String contrasenaTemporal) {
    }

    private final AuthService auth;
    private final CuentaService cuentas;
    private final AuditoriaService auditoria;

    /** Todo o nada: si la cédula o el correo ya existen, o la apertura falla, no queda nada a medias. */
    @Transactional
    public Alta crear(Long actorId, String correo, PersonaController.PerfilRequest perfil) {
        AuthService.Creado creado = auth.crearConTemporal(correo, perfil);
        CuentaService.CuentaVista cuenta = cuentas.abrirEnVentanilla(actorId, creado.usuarioId());
        auditoria.registrar(actorId, "USUARIO_CREADO_POR_PERSONAL", "USUARIO", "usuario", creado.usuarioId(), null,
                cuenta.cuenta().getNumero(), null);
        return new Alta(creado.usuarioId(), cuenta.cuenta().getNumero(), creado.contrasenaTemporal());
    }
}
