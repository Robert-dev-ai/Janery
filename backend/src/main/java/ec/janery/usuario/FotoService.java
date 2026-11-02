package ec.janery.usuario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import lombok.RequiredArgsConstructor;

/** Foto de perfil. Va aparte de la entidad para no leer la imagen en cada consulta del usuario. */
@Service
@RequiredArgsConstructor
public class FotoService {

    public record Foto(String tipo, byte[] contenido) {
    }

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final JdbcClient jdbc;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public Optional<Foto> de(Long usuarioId) {
        return jdbc.sql("SELECT foto_tipo, foto FROM usuarios WHERE id = :id AND foto IS NOT NULL")
                .param("id", usuarioId)
                .query((rs, n) -> new Foto(rs.getString(1), rs.getBytes(2))).optional();
    }

    @Transactional(readOnly = true)
    public boolean tiene(Long usuarioId) {
        return jdbc.sql("SELECT foto IS NOT NULL FROM usuarios WHERE id = :id").param("id", usuarioId)
                .query(Boolean.class).optional().orElse(false);
    }

    @Transactional
    public void guardar(Long usuarioId, MultipartFile archivo) {
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FOTO_MUY_GRANDE", "La foto puede pesar hasta 2 MB");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String tipo = tipoImagen(bytes);
        if (tipo == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FOTO_INVALIDA", "La foto debe ser JPG, PNG o WEBP");
        }
        jdbc.sql("UPDATE usuarios SET foto = :foto, foto_tipo = :tipo WHERE id = :id")
                .param("foto", bytes).param("tipo", tipo).param("id", usuarioId).update();
        auditoria.registrar(usuarioId, "FOTO_ACTUALIZADA", "USUARIO", "usuario", usuarioId, null, null, null);
    }

    @Transactional
    public void quitar(Long usuarioId) {
        jdbc.sql("UPDATE usuarios SET foto = NULL, foto_tipo = NULL WHERE id = :id").param("id", usuarioId).update();
        auditoria.registrar(usuarioId, "FOTO_QUITADA", "USUARIO", "usuario", usuarioId, null, null, null);
    }

    /** Se reconoce por el contenido: el nombre y el tipo declarados los decide quien sube el archivo. */
    public static String tipoImagen(byte[] b) {
        if (empieza(b, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (empieza(b, 0x89, 'P', 'N', 'G')) {
            return "image/png";
        }
        if (b.length > 12 && empieza(b, 'R', 'I', 'F', 'F') && Arrays.equals(Arrays.copyOfRange(b, 8, 12), "WEBP".getBytes())) {
            return "image/webp";
        }
        return null;
    }

    private static boolean empieza(byte[] b, int... cabecera) {
        if (b.length < cabecera.length) {
            return false;
        }
        for (int i = 0; i < cabecera.length; i++) {
            if ((b[i] & 0xFF) != cabecera[i]) {
                return false;
            }
        }
        return true;
    }
}
