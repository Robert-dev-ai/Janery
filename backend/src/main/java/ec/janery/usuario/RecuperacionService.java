package ec.janery.usuario;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.correo.EnvioCorreo;

/**
 * Decisión del propietario (13/11/2026): quien olvida su contraseña, o bloqueó su acceso, la recupera con un enlace que
 * llega a su correo. Si el envío de correos no está configurado o falla, el pedido le llega a la presidencia, como antes.
 *
 * <p>
 * La respuesta nunca revela si el correo o la cédula existen.
 */
@Service
public class RecuperacionService {

    /** Por dónde se atiende el pedido. Depende solo de la configuración, no de si la persona existe. */
    public enum Medio { CORREO, PRESIDENCIA }

    static final Duration VIGENCIA = Duration.ofMinutes(30);
    /** Evita llenar el buzón de alguien pidiendo enlaces seguidos. */
    static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofMinutes(2);
    private static final String MODULO = "USUARIO";
    private static final Logger log = LoggerFactory.getLogger(RecuperacionService.class);

    private final Repositorios.UsuarioRepository usuarios;
    private final Repositorios.TokenRefrescoRepository tokens;
    private final AuthService auth;
    private final EnvioCorreo correo;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final JdbcClient jdbc;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final String urlPublica;
    private final SecureRandom aleatorio = new SecureRandom();

    RecuperacionService(Repositorios.UsuarioRepository usuarios, Repositorios.TokenRefrescoRepository tokens, AuthService auth,
            EnvioCorreo correo, PasswordEncoder encoder, AuditoriaService auditoria, JdbcClient jdbc, Clock reloj,
            PlatformTransactionManager transacciones, @Value("${janery.url-publica:http://localhost:4200}") String urlPublica) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.auth = auth;
        this.correo = correo;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    /** El correo se envía fuera de la transacción: si Brevo tarda, no deja la base esperando. */
    public Medio solicitar(String identificador) {
        if (!correo.disponible()) {
            auth.solicitarRestablecimiento(identificador);
            return Medio.PRESIDENCIA;
        }
        Optional<Usuario> usuario = (identificador.contains("@") ? usuarios.findByCorreo(AuthService.normalizar(identificador))
                : usuarios.findByIdentificacion(identificador.trim()))
                .filter(u -> u.activo() || u.bloqueado());
        if (usuario.isEmpty()) {
            return Medio.CORREO;
        }
        Usuario u = usuario.get();
        String enlace = transaccion.execute(estado -> crearEnlace(u.getId()));
        if (enlace == null) {
            return Medio.CORREO;
        }
        try {
            enviar(u, enlace);
            auditoria.registrar(u.getId(), "RECUPERACION_ENVIADA", MODULO, "usuario", u.getId(), null, null, null);
        } catch (RuntimeException e) {
            // Sin correo, que al menos le llegue a la presidencia.
            log.warn("No se pudo enviar el correo de recuperación al usuario {}: {}", u.getId(), e.getMessage());
            auth.solicitarRestablecimiento(identificador);
        }
        return Medio.CORREO;
    }

    /** Null si acaba de pedir otro enlace. */
    private String crearEnlace(Long usuarioId) {
        Instant ahora = Instant.now(reloj);
        boolean reciente = jdbc.sql("SELECT EXISTS (SELECT 1 FROM recuperaciones_contrasena WHERE usuario_id = :id AND creado_en > :desde)")
                .param("id", usuarioId).param("desde", java.sql.Timestamp.from(ahora.minus(ESPERA_ENTRE_ENVIOS)))
                .query(Boolean.class).single();
        if (reciente) {
            return null;
        }
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("""
                INSERT INTO recuperaciones_contrasena (usuario_id, hash_token, expira_en, creado_en)
                VALUES (:id, :hash, :expira, :ahora)
                """).param("id", usuarioId).param("hash", AuthService.hash(token))
                .param("expira", java.sql.Timestamp.from(ahora.plus(VIGENCIA))).param("ahora", java.sql.Timestamp.from(ahora)).update();
        return token;
    }

    private void enviar(Usuario u, String token) {
        String nombre = jdbc.sql("SELECT nombres FROM personas WHERE usuario_id = :id").param("id", u.getId())
                .query(String.class).optional().orElse("");
        String saludo = nombre.isBlank() ? "Hola:" : "Hola, " + nombre + ":";
        String enlace = urlPublica + "/restablecer-contrasena?token=" + token;
        long minutos = VIGENCIA.toMinutes();
        String texto = saludo + "\n\nPara elegir una contraseña nueva en Janery, abra este enlace. Sirve una sola vez y vence en "
                + minutos + " minutos:\n\n" + enlace
                + "\n\nSi no lo pidió usted, ignore este correo: su contraseña no cambia.\n";
        String html = "<p>" + HtmlUtils.htmlEscape(saludo) + "</p>"
                + "<p>Para elegir una contraseña nueva en Janery, abra este enlace. Sirve una sola vez y vence en " + minutos + " minutos:</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(enlace) + "\">Elegir una contraseña nueva</a></p>"
                + "<p>Si no lo pidió usted, ignore este correo: su contraseña no cambia.</p>";
        correo.enviar(u.getCorreo(), "Recuperar su contraseña de Janery", texto, html);
    }

    /** Usa el enlace una sola vez: elige la contraseña, desbloquea el acceso y cierra las sesiones abiertas. */
    @Transactional
    public void restablecer(String token, String nueva) {
        Instant ahora = Instant.now(reloj);
        // En una sentencia: dos envíos simultáneos del mismo enlace no pueden usarlo los dos.
        Long usuarioId = jdbc.sql("""
                UPDATE recuperaciones_contrasena SET usada_en = :ahora
                WHERE hash_token = :hash AND usada_en IS NULL AND expira_en > :ahora
                RETURNING usuario_id
                """).param("ahora", java.sql.Timestamp.from(ahora)).param("hash", AuthService.hash(token))
                .query(Long.class).optional()
                .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, "ENLACE_INVALIDO",
                        "El enlace ya se usó o venció. Pida otro desde «¿Olvidó su contraseña?»"));
        Usuario usuario = usuarios.findById(usuarioId).filter(u -> u.activo() || u.bloqueado())
                .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, "ENLACE_INVALIDO",
                        "El enlace ya se usó o venció. Pida otro desde «¿Olvidó su contraseña?»"));
        String antes = usuario.getEstado();
        usuario.recuperar(encoder.encode(nueva));
        tokens.revocarOtros(usuarioId, "", ahora);
        // Los demás enlaces que pidió dejan de servir, y su pedido a la presidencia queda atendido.
        jdbc.sql("UPDATE recuperaciones_contrasena SET usada_en = :ahora WHERE usuario_id = :id AND usada_en IS NULL")
                .param("ahora", java.sql.Timestamp.from(ahora)).param("id", usuarioId).update();
        jdbc.sql("""
                UPDATE solicitudes_restablecimiento SET estado = 'ATENDIDA', atendida_por = :id, atendida_en = :ahora
                WHERE usuario_id = :id AND estado = 'PENDIENTE'
                """).param("id", usuarioId).param("ahora", java.sql.Timestamp.from(ahora)).update();
        auditoria.registrar(usuarioId, "CONTRASENA_RECUPERADA", MODULO, "usuario", usuarioId, antes, "ACTIVO", null);
    }
}
