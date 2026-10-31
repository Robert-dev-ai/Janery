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

@Service
public class AuthService {

    public record Sesion(String accessToken, Instant expiraEn, String refreshToken, Duration duracionRefresh) {
    }

    private static final String MODULO = "USUARIO";

    private final Repositorios.UsuarioRepository usuarios;
    private final Repositorios.TokenRefrescoRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final Duration duracionAccess;
    private final Duration duracionRefresh;
    private final String hashFicticio;
    private final SecureRandom aleatorio = new SecureRandom();

    AuthService(Repositorios.UsuarioRepository usuarios, Repositorios.TokenRefrescoRepository tokens,
            PasswordEncoder encoder, JwtEncoder jwtEncoder, AuditoriaService auditoria, Clock reloj,
            @Value("${janery.jwt.access-minutos}") long accessMinutos,
            @Value("${janery.jwt.refresh-dias}") long refreshDias) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.auditoria = auditoria;
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

    /** Sin transacción exterior: el intento fallido debe quedar auditado aunque se lance la excepción. */
    public Sesion iniciarSesion(String correo, String contrasena) {
        Usuario usuario = usuarios.findByCorreo(normalizar(correo)).orElse(null);
        // Con usuario inexistente también se compara un hash, para no revelar por tiempo qué correos existen.
        boolean valida = encoder.matches(contrasena, usuario == null ? hashFicticio : usuario.getHashContrasena());
        if (usuario == null || !valida || !usuario.activo()) {
            auditoria.registrar(usuario == null ? null : usuario.getId(), "LOGIN_FALLIDO", MODULO, "usuario",
                    usuario == null ? null : usuario.getId(), null, null, null);
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "CREDENCIALES", "Correo o contraseña incorrectos");
        }
        auditoria.registrar(usuario.getId(), "LOGIN", MODULO, "usuario", usuario.getId(), null, null, null);
        return emitir(usuario);
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

    private Sesion emitir(Usuario usuario) {
        Instant ahora = Instant.now(reloj);
        Instant expira = ahora.plus(duracionAccess);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("scope", String.join(" ", usuarios.permisosVigentes(usuario.getId())))
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
