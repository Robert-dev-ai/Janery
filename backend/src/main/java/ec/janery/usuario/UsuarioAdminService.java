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
            boolean socio, String nombre, String identificacion, boolean pideRestablecer) {
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
    private final ec.janery.persona.PersonaService personas;
    private final Repositorios.RestablecimientoRepository restablecimientos;

    UsuarioAdminService(Repositorios.UsuarioRepository usuarios, Repositorios.UsuarioCargoRepository usuarioCargos,
            PasswordEncoder encoder, AuditoriaService auditoria, Clock reloj, ec.janery.socio.SocioService socios,
            ec.janery.persona.PersonaService personas, Repositorios.RestablecimientoRepository restablecimientos,
            @Value("${janery.admin-inicial.correo}") String correoInicial,
            @Value("${janery.admin-inicial.contrasena}") String contrasenaInicial) {
        this.usuarios = usuarios;
        this.usuarioCargos = usuarioCargos;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.socios = socios;
        this.personas = personas;
        this.restablecimientos = restablecimientos;
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
        java.util.Set<Long> piden = restablecimientos.usuariosPendientes();
        var page = usuarios.findAll(PageRequest.of(pagina, tamano, Sort.by("id")));
        // Cargos, personas y socios de toda la página de una vez: una consulta por tipo, no por usuario.
        List<Long> ids = page.getContent().stream().map(Usuario::getId).toList();
        java.util.Map<Long, List<String>> cargos = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            usuarios.cargosVigentesDe(ids).forEach(f -> cargos.computeIfAbsent(((Number) f[0]).longValue(),
                    k -> new java.util.ArrayList<>()).add((String) f[1]));
        }
        var porUsuario = personas.porUsuarios(ids);
        java.util.Set<Long> sociosActivos = socios.personasActivas(porUsuario.values().stream()
                .map(ec.janery.persona.Persona::getId).toList());
        return Pagina.de(page, u -> {
            var persona = java.util.Optional.ofNullable(porUsuario.get(u.getId()));
            return new UsuarioResumen(u.getId(), u.getCorreo(), u.getEstado(), u.getCreadoEn(),
                    cargos.getOrDefault(u.getId(), List.of()), persona.map(p -> sociosActivos.contains(p.getId())).orElse(false),
                    persona.map(p -> p.getNombres() + " " + p.getApellidos()).orElse(null),
                    persona.map(ec.janery.persona.Persona::getIdentificacion).orElse(null), piden.contains(u.getId()));
        });
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
