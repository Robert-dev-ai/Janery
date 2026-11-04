package ec.janery.usuario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.persona.PersonaController;
import ec.janery.persona.PersonaService;

@Service
public class AuthService {

    public record Sesion(String accessToken, Instant expiraEn, String refreshToken, Duration duracionRefresh) {
    }

    private static final String MODULO = "USUARIO";
    private static final String PENDIENTE = "PENDIENTE";

    private final Repositorios.UsuarioRepository usuarios;
    private final Repositorios.TokenRefrescoRepository tokens;
    private final Repositorios.RestablecimientoRepository restablecimientos;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AuditoriaService auditoria;
    private final PersonaService personas;
    private final Clock reloj;
    private final Duration duracionAccess;
    private final Duration duracionRefresh;
    private final String hashFicticio;
    private final SecureRandom aleatorio = new SecureRandom();

    AuthService(Repositorios.UsuarioRepository usuarios, Repositorios.TokenRefrescoRepository tokens,
            Repositorios.RestablecimientoRepository restablecimientos,
            PasswordEncoder encoder, JwtEncoder jwtEncoder, AuditoriaService auditoria, PersonaService personas, Clock reloj,
            @Value("${janery.jwt.access-minutos}") long accessMinutos,
            @Value("${janery.jwt.refresh-dias}") long refreshDias) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.restablecimientos = restablecimientos;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.auditoria = auditoria;
        this.personas = personas;
        this.reloj = reloj;
        this.duracionAccess = Duration.ofMinutes(accessMinutos);
        this.duracionRefresh = Duration.ofDays(refreshDias);
        this.hashFicticio = encoder.encode("sin-usuario");
    }

    static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public Long registrar(String correo, String contrasena) {
        String normalizado = normalizar(correo);
        if (usuarios.existsByCorreo(normalizado)) {
            throw NegocioException.conflicto("CORREO_REGISTRADO", "Ya existe una cuenta con ese correo");
        }
        Usuario usuario = usuarios.save(new Usuario(normalizado, encoder.encode(contrasena), Instant.now(reloj)));
        auditoria.registrar(usuario.getId(), "REGISTRO", MODULO, "usuario", usuario.getId(), null, null, null);
        return usuario.getId();
    }

    /** Usuario y datos personales se crean juntos: si la identificación ya existe, tampoco queda el usuario. */
    @Transactional
    public Long registrarConPerfil(String correo, String contrasena, PersonaController.PerfilRequest perfil) {
        Long id = registrar(correo, contrasena);
        personas.completarPerfil(id, perfil);
        return id;
    }

    /** Sin transacción exterior: el intento fallido debe quedar auditado aunque se lance la excepción. */
    public Sesion iniciarSesion(String identificador, String contrasena) {
        // Un correo siempre lleva arroba; una cédula, nunca.
        Usuario usuario = (identificador.contains("@") ? usuarios.findByCorreo(normalizar(identificador))
                : usuarios.findByIdentificacion(identificador.trim())).orElse(null);
        // Con usuario inexistente también se compara un hash, para no revelar por tiempo qué correos existen.
        boolean valida = encoder.matches(contrasena, usuario == null ? hashFicticio : usuario.getHashContrasena());
        if (usuario != null && usuario.bloqueado()) {
            auditoria.registrar(usuario.getId(), "LOGIN_FALLIDO", MODULO, "usuario", usuario.getId(), null, null, "BLOQUEADO");
            throw bloqueado();
        }
        if (usuario == null || !valida || !usuario.activo()) {
            Long id = usuario == null ? null : usuario.getId();
            auditoria.registrar(id, "LOGIN_FALLIDO", MODULO, "usuario", id, null, null, null);
            if (usuario != null && usuario.activo()) {
                boolean queda = usuario.registrarFallo();
                usuarios.save(usuario);
                if (queda) {
                    auditoria.registrar(id, "ACCESO_BLOQUEADO", MODULO, "usuario", id, "ACTIVO", "BLOQUEADO", null);
                    throw bloqueado();
                }
                int restantes = Usuario.INTENTOS_MAXIMOS - usuario.getIntentosFallidos();
                throw new NegocioException(HttpStatus.UNAUTHORIZED, "CREDENCIALES", "Credenciales incorrectas. "
                        + (restantes == 1 ? "Le queda 1 intento" : "Le quedan " + restantes + " intentos")
                        + " antes de que se bloquee su acceso");
            }
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "CREDENCIALES", "Credenciales incorrectas");
        }
        if (usuario.getIntentosFallidos() > 0) {
            usuario.reiniciarIntentos();
            usuarios.save(usuario);
        }
        auditoria.registrar(usuario.getId(), "LOGIN", MODULO, "usuario", usuario.getId(), null, null, null);
        return emitir(usuario);
    }

    private static NegocioException bloqueado() {
        return new NegocioException(HttpStatus.UNAUTHORIZED, "ACCESO_BLOQUEADO",
                "Su acceso está bloqueado por " + Usuario.INTENTOS_MAXIMOS + " contraseñas incorrectas. "
                        + "Use «¿Olvidó su contraseña?» o pida a la presidencia que la restablezca");
    }

    /** Sin correo saliente: la solicitud le llega a la presidencia. No revela si el usuario existe. */
    @Transactional
    public void solicitarRestablecimiento(String identificador) {
        Optional<Usuario> usuario = identificador.contains("@") ? usuarios.findByCorreo(normalizar(identificador))
                : usuarios.findByIdentificacion(identificador.trim());
        usuario.filter(u -> !restablecimientos.existsByUsuarioIdAndEstado(u.getId(), PENDIENTE)).ifPresent(u -> {
            restablecimientos.save(new SolicitudRestablecimiento(null, u.getId(), PENDIENTE, null, null, Instant.now(reloj)));
            auditoria.registrar(u.getId(), "RESTABLECIMIENTO_SOLICITADO", MODULO, "usuario", u.getId(), null, null, null);
        });
    }

    /** La contraseña temporal solo se muestra una vez, a quien la genera; se guarda cifrada como cualquier otra. */
    @Transactional
    public String restablecer(Long actorId, Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        String temporal = contrasenaTemporal();
        String antes = usuario.getEstado();
        usuario.restablecer(encoder.encode(temporal));
        tokens.revocarOtros(usuarioId, "", Instant.now(reloj));
        restablecimientos.findByUsuarioIdAndEstado(usuarioId, PENDIENTE).forEach(s -> {
            s.setEstado("ATENDIDA");
            s.setAtendidaPor(actorId);
            s.setAtendidaEn(Instant.now(reloj));
        });
        auditoria.registrar(actorId, "CONTRASENA_RESTABLECIDA", MODULO, "usuario", usuarioId, antes, "ACTIVO", null);
        return temporal;
    }

    private String contrasenaTemporal() {
        // Sin letras que se confunden al dictarlas (0/O, 1/l/I).
        String letras = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder clave = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            clave.append(letras.charAt(aleatorio.nextInt(letras.length())));
        }
        return clave.toString();
    }

    /** Corrige un correo mal escrito al registrarse. */
    @Transactional
    public void cambiarCorreo(Long actorId, Long usuarioId, String correo) {
        String normalizado = normalizar(correo);
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        if (normalizado.equals(usuario.getCorreo())) {
            return;
        }
        if (usuarios.existsByCorreo(normalizado)) {
            throw NegocioException.conflicto("CORREO_REGISTRADO", "Ya existe una cuenta con ese correo");
        }
        String antes = usuario.getCorreo();
        usuario.cambiarCorreo(normalizado);
        auditoria.registrar(actorId, "CORREO_CAMBIADO", MODULO, "usuario", usuarioId, antes, normalizado, null);
    }

    /** La persona cambia su propio correo. Pide la contraseña porque con el correo se inicia sesión. */
    @Transactional
    public void cambiarMiCorreo(Long usuarioId, String correo, String contrasena) {
        Usuario usuario = usuarios.findById(usuarioId).filter(Usuario::activo)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        // Con 401 el cliente entendería que caducó la sesión.
        if (!encoder.matches(contrasena, usuario.getHashContrasena())) {
            auditoria.registrar(usuarioId, "CONTRASENA_FALLIDA", MODULO, "usuario", usuarioId, null, null, null);
            throw new NegocioException(HttpStatus.BAD_REQUEST, "CONTRASENA_ACTUAL_INCORRECTA",
                    "La contraseña actual no es correcta");
        }
        cambiarCorreo(usuarioId, usuarioId, correo);
    }

    @Transactional
    public Sesion refrescar(String refreshToken) {
        Instant ahora = Instant.now(reloj);
        TokenRefresco actual = refreshToken == null ? null : tokens.findByHashToken(hash(refreshToken)).orElse(null);
        if (actual == null || !actual.vigente(ahora)) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "SESION_EXPIRADA", "La sesión ha expirado");
        }
        Usuario usuario = usuarios.findById(actual.getUsuarioId())
                .filter(Usuario::activo)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "SESION_EXPIRADA", "La sesión ha expirado"));
        actual.revocar(ahora);
        return emitir(usuario);
    }

    @Transactional
    public void cerrarSesion(String refreshToken) {
        if (refreshToken != null) {
            tokens.findByHashToken(hash(refreshToken)).ifPresent(t -> {
                t.revocar(Instant.now(reloj));
                auditoria.registrar(t.getUsuarioId(), "LOGOUT", MODULO, "usuario", t.getUsuarioId(), null, null, null);
            });
        }
    }

    /** Sin deshacer ante el rechazo: el intento fallido debe quedar auditado, y antes de él no se escribe nada. */
    @Transactional(noRollbackFor = NegocioException.class)
    public void cambiarContrasena(Long usuarioId, String actual, String nueva, String refreshToken) {
        Usuario usuario = usuarios.findById(usuarioId).filter(Usuario::activo)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        // Con 401 el cliente entendería que caducó la sesión.
        if (!encoder.matches(actual, usuario.getHashContrasena())) {
            auditoria.registrar(usuarioId, "CONTRASENA_FALLIDA", MODULO, "usuario", usuarioId, null, null, null);
            throw new NegocioException(HttpStatus.BAD_REQUEST, "CONTRASENA_ACTUAL_INCORRECTA",
                    "La contraseña actual no es correcta");
        }
        if (encoder.matches(nueva, usuario.getHashContrasena())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "CONTRASENA_REPETIDA",
                    "La contraseña nueva debe ser distinta de la actual");
        }
        usuario.cambiarContrasena(encoder.encode(nueva));
        revocarOtrasSesiones(usuarioId, refreshToken);
        auditoria.registrar(usuarioId, "CONTRASENA_CAMBIADA", MODULO, "usuario", usuarioId, null, null, null);
    }

    @Transactional
    public void cerrarOtrasSesiones(Long usuarioId, String refreshToken) {
        revocarOtrasSesiones(usuarioId, refreshToken);
        auditoria.registrar(usuarioId, "SESIONES_CERRADAS", MODULO, "usuario", usuarioId, null, null, null);
    }

    private void revocarOtrasSesiones(Long usuarioId, String refreshToken) {
        tokens.revocarOtros(usuarioId, refreshToken == null ? "" : hash(refreshToken), Instant.now(reloj));
    }

    /** Los permisos de sus cargos, y SOCIO para consultar las finanzas: los socios ven, pero no registran. */
    private java.util.List<String> alcances(Long usuarioId) {
        return usuarios.alcances(usuarioId);
    }

    private Sesion emitir(Usuario usuario) {
        Instant ahora = Instant.now(reloj);
        Instant expira = ahora.plus(duracionAccess);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("scope", String.join(" ", alcances(usuario.getId())))
                .build();
        String access = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new TokenRefresco(usuario.getId(), hash(refresh), ahora.plus(duracionRefresh), ahora));
        return new Sesion(access, expira, refresh, duracionRefresh);
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
