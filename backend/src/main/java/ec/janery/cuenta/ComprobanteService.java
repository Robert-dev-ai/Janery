package ec.janery.cuenta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.usuario.FotoService;
import lombok.RequiredArgsConstructor;

/**
 * Comprobantes del dinero que llega de fuera: el pago de apertura y los depósitos. Quien confirma el ingreso los revisa
 * antes; una transferencia no se confirma sin su comprobante.
 */
@Service
@RequiredArgsConstructor
public class ComprobanteService {

    public record Vista(Long id, String nombre, String tipoContenido, Long tamano, Instant creadoEn) {
    }

    public record Descarga(String nombre, String tipoContenido, byte[] contenido) {
    }

    public static final String APERTURA = "APERTURA";
    public static final String DEPOSITO = "DEPOSITO";
    private static final List<String> TIPOS = List.of(APERTURA, DEPOSITO);
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_POR_PAGO = 5;
    private static final byte[] CABECERA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final JdbcClient jdbc;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Transactional
    public List<Vista> subir(Long usuarioId, String tipo, Long referenciaId, List<MultipartFile> archivos) {
        exigirPropio(usuarioId, tipo, referenciaId);
        if (!pendiente(tipo, referenciaId)) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Este pago ya se revisó: no admite comprobantes");
        }
        if (archivos.isEmpty() || cantidad(tipo, referenciaId) + archivos.size() > MAX_POR_PAGO) {
            throw NegocioException.conflicto("COMPROBANTES_EXCEDIDOS", "Se admiten hasta " + MAX_POR_PAGO + " comprobantes por pago");
        }
        Instant ahora = Instant.now(reloj);
        for (MultipartFile archivo : archivos) {
            byte[] contenido = contenido(archivo);
            jdbc.sql("""
                    INSERT INTO comprobantes (tipo, referencia_id, nombre, tipo_contenido, tamano, contenido, subido_por, creado_en)
                    VALUES (:tipo, :ref, :nombre, :clase, :tamano, :contenido, :usuario, :ahora)
                    """).param("tipo", tipo).param("ref", referenciaId).param("nombre", nombre(archivo))
                    .param("clase", tipoContenido(contenido)).param("tamano", (long) contenido.length)
                    .param("contenido", contenido).param("usuario", usuarioId)
                    .param("ahora", java.sql.Timestamp.from(ahora)).update();
        }
        auditoria.registrar(usuarioId, "COMPROBANTE_SUBIDO", "CUENTA", APERTURA.equals(tipo) ? "cuenta" : "solicitud_deposito",
                referenciaId, null, String.valueOf(archivos.size()), null);
        return listar(tipo, referenciaId);
    }

    @Transactional(readOnly = true)
    public List<Vista> mios(Long usuarioId, String tipo, Long referenciaId) {
        exigirPropio(usuarioId, tipo, referenciaId);
        return listar(tipo, referenciaId);
    }

    @Transactional(readOnly = true)
    public List<Vista> listar(String tipo, Long referenciaId) {
        validarTipo(tipo);
        return jdbc.sql("""
                SELECT id, nombre, tipo_contenido, tamano, creado_en FROM comprobantes
                WHERE tipo = :tipo AND referencia_id = :ref ORDER BY id
                """).param("tipo", tipo).param("ref", referenciaId)
                .query((rs, n) -> new Vista(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getTimestamp(5).toInstant()))
                .list();
    }

    @Transactional(readOnly = true)
    public long cantidad(String tipo, Long referenciaId) {
        return jdbc.sql("SELECT count(*) FROM comprobantes WHERE tipo = :tipo AND referencia_id = :ref")
                .param("tipo", tipo).param("ref", referenciaId).query(Long.class).single();
    }

    @Transactional(readOnly = true)
    public Descarga descargar(Long id) {
        return jdbc.sql("SELECT nombre, tipo_contenido, contenido FROM comprobantes WHERE id = :id").param("id", id)
                .query((rs, n) -> new Descarga(rs.getString(1), rs.getString(2), rs.getBytes(3))).optional()
                .orElseThrow(() -> NegocioException.noEncontrado("Comprobante no encontrado"));
    }

    /** Se exige al confirmar una transferencia: sin el comprobante no hay con qué verificarla. */
    @Transactional(readOnly = true)
    public void exigirSiTransferencia(String tipo, Long referenciaId, String medio) {
        if ("TRANSFERENCIA".equals(medio) && cantidad(tipo, referenciaId) == 0) {
            throw NegocioException.conflicto("COMPROBANTE_REQUERIDO", "Falta el comprobante de la transferencia");
        }
    }

    private void exigirPropio(Long usuarioId, String tipo, Long referenciaId) {
        validarTipo(tipo);
        String sql = APERTURA.equals(tipo)
                ? "SELECT p.usuario_id FROM cuentas_principales c JOIN personas p ON p.id = c.persona_id WHERE c.id = :ref"
                : "SELECT p.usuario_id FROM solicitudes_deposito s JOIN personas p ON p.id = s.persona_id WHERE s.id = :ref";
        Optional<Long> dueno = jdbc.sql(sql).param("ref", referenciaId).query(Long.class).optional();
        if (dueno.filter(usuarioId::equals).isEmpty()) {
            throw NegocioException.noEncontrado("Pago no encontrado");
        }
    }

    private boolean pendiente(String tipo, Long referenciaId) {
        String sql = APERTURA.equals(tipo)
                ? "SELECT estado = 'PENDIENTE_PAGO' FROM cuentas_principales WHERE id = :ref"
                : "SELECT estado = 'PENDIENTE' FROM solicitudes_deposito WHERE id = :ref";
        return jdbc.sql(sql).param("ref", referenciaId).query(Boolean.class).optional().orElse(false);
    }

    private static void validarTipo(String tipo) {
        if (!TIPOS.contains(tipo)) {
            throw NegocioException.noEncontrado("Tipo de comprobante desconocido");
        }
    }

    /** Se comprueba el contenido: el nombre y el tipo declarados los decide quien sube el archivo. */
    private static byte[] contenido(MultipartFile archivo) {
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "COMPROBANTE_MUY_GRANDE", "Cada comprobante puede pesar hasta 10 MB");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (tipoContenido(bytes) == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "COMPROBANTE_INVALIDO", "El comprobante debe ser PDF, JPG, PNG o WEBP");
        }
        return bytes;
    }

    private static String tipoContenido(byte[] bytes) {
        if (bytes.length >= CABECERA_PDF.length && Arrays.equals(Arrays.copyOf(bytes, CABECERA_PDF.length), CABECERA_PDF)) {
            return "application/pdf";
        }
        return FotoService.tipoImagen(bytes);
    }

    private static String nombre(MultipartFile archivo) {
        String original = archivo.getOriginalFilename() == null ? "comprobante" : archivo.getOriginalFilename();
        String limpio = original.replaceAll(".*[/\\\\]", "").replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (limpio.isEmpty()) {
            return "comprobante";
        }
        return limpio.length() > 255 ? limpio.substring(limpio.length() - 255) : limpio;
    }
}
