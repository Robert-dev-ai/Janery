package ec.janery.usuario;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.common.Pagina;

/** Consulta de usuarios, cargos y creación del presidente inicial a partir de la configuración local. */
@Service
public class UsuarioAdminService implements ApplicationRunner {

    public record UsuarioResumen(Long id, String correo, String estado, Instant creadoEn, List<String> cargos,
            boolean socio) {
    }

    private static final String MODULO = "USUARIO";
    private static final String CARGO_INICIAL = "PRESIDENCIA";

    private final Repositorios.UsuarioRepository usuarios;
    private final Repositorios.UsuarioCargoRepository usuarioCargos;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final String correoInicial;
    private final String contrasenaInicial;
    private final ec.janery.socio.SocioService socios;

    UsuarioAdminService(Repositorios.UsuarioRepository usuarios, Repositorios.UsuarioCargoRepository usuarioCargos,
            PasswordEncoder encoder, AuditoriaService auditoria, Clock reloj, ec.janery.socio.SocioService socios,
            @Value("${janery.admin-inicial.correo}") String correoInicial,
            @Value("${janery.admin-inicial.contrasena}") String contrasenaInicial) {
        this.usuarios = usuarios;
        this.usuarioCargos = usuarioCargos;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.socios = socios;
        this.correoInicial = correoInicial;
        this.contrasenaInicial = contrasenaInicial;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (correoInicial.isBlank() || contrasenaInicial.isBlank()
                || usuarios.existsByCorreo(AuthService.normalizar(correoInicial))) {
            return;
        }
        Instant ahora = Instant.now(reloj);
        Usuario usuario = usuarios.save(new Usuario(AuthService.normalizar(correoInicial),
                encoder.encode(contrasenaInicial), ahora));
        usuarioCargos.save(new UsuarioCargo(usuario.getId(), cargoId(CARGO_INICIAL), ahora, null));
        auditoria.registrar(null, "PRESIDENTE_INICIAL", MODULO, "usuario", usuario.getId(), null, CARGO_INICIAL, null);
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioResumen> listar(int pagina, int tamano) {
        return Pagina.de(usuarios.findAll(PageRequest.of(pagina, tamano, Sort.by("id"))),
                u -> new UsuarioResumen(u.getId(), u.getCorreo(), u.getEstado(), u.getCreadoEn(),
                        usuarios.cargosVigentes(u.getId()), socios.usuarioEsSocio(u.getId())));
    }

    @Transactional
    public void asignarCargo(Long actorId, Long usuarioId, String codigoCargo) {
        if (!usuarios.existsById(usuarioId)) {
            throw NegocioException.noEncontrado("Usuario no encontrado");
        }
        Long cargoId = cargoId(codigoCargo);
        if (usuarioCargos.findByUsuarioIdAndCargoIdAndHastaIsNull(usuarioId, cargoId).isPresent()) {
            throw NegocioException.conflicto("CARGO_YA_ASIGNADO", "El usuario ya tiene ese cargo");
        }
        usuarioCargos.save(new UsuarioCargo(usuarioId, cargoId, Instant.now(reloj), actorId));
        auditoria.registrar(actorId, "CARGO_ASIGNADO", MODULO, "usuario", usuarioId, null, codigoCargo, null);
    }

    @Transactional
    public void revocarCargo(Long actorId, Long usuarioId, String codigoCargo) {
        UsuarioCargo vigente = usuarioCargos.findByUsuarioIdAndCargoIdAndHastaIsNull(usuarioId, cargoId(codigoCargo))
                .orElseThrow(() -> NegocioException.noEncontrado("El usuario no tiene ese cargo"));
        vigente.revocar(Instant.now(reloj));
        auditoria.registrar(actorId, "CARGO_REVOCADO", MODULO, "usuario", usuarioId, codigoCargo, null, null);
    }

    private Long cargoId(String codigo) {
        return usuarios.idDeCargo(codigo).orElseThrow(() -> NegocioException.noEncontrado("Cargo no encontrado"));
    }
}
