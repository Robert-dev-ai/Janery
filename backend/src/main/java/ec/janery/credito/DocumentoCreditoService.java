package ec.janery.credito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import ec.janery.credito.Entidades.Credito;
import ec.janery.persona.PersonaService;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/** Documentos PDF de una solicitud de crédito. Aún no condicionan la aprobación: se guardan para el análisis futuro. */
@Service
@RequiredArgsConstructor
public class DocumentoCreditoService {

    @Entity(name = "DocumentoCredito")
    @Table(name = "documentos_credito")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class DocumentoCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private String nombre;
        private Long tamano;
        private byte[] contenido;
        private Long subidoPor;
        private Instant creadoEn;
    }

    public record DocumentoVista(Long id, String nombre, Long tamano, Instant creadoEn) {
    }

    public record Descarga(String nombre, byte[] contenido) {
    }

    interface DocumentoRepository extends JpaRepository<DocumentoCredito, Long> {
        /** Sin el contenido: listar no debe traer los archivos de la base de datos. */
        @Query("""
                select new ec.janery.credito.DocumentoCreditoService$DocumentoVista(d.id, d.nombre, d.tamano, d.creadoEn)
                from DocumentoCredito d where d.creditoId = :creditoId order by d.id
                """)
        List<DocumentoVista> delCredito(@Param("creditoId") Long creditoId);

        long countByCreditoId(Long creditoId);
    }

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_DOCUMENTOS = 10;
    private static final byte[] CABECERA_PDF = { '%', 'P', 'D', 'F', '-' };

    private final DocumentoRepository documentos;
    private final CreditoService.CreditoRepository creditos;
    private final PersonaService personas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    /** Todo o nada: si un archivo no es válido, no se guarda ninguno. */
    @Transactional
    public List<DocumentoVista> subir(Long usuarioId, Long creditoId, List<MultipartFile> archivos) {
        Credito c = propio(usuarioId, creditoId);
        if (!"SOLICITADO".equals(c.getEstado()) && !"ACTIVO".equals(c.getEstado())) {
            throw NegocioException.conflicto("ESTADO_INVALIDO", "Este crédito ya no admite documentos");
        }
        if (archivos.isEmpty() || documentos.countByCreditoId(creditoId) + archivos.size() > MAX_DOCUMENTOS) {
            throw NegocioException.conflicto("DOCUMENTOS_EXCEDIDOS", "Se admiten hasta " + MAX_DOCUMENTOS + " documentos por crédito");
        }
        Instant ahora = Instant.now(reloj);
        for (MultipartFile archivo : archivos) {
            byte[] contenido = contenido(archivo);
            documentos.save(new DocumentoCredito(null, creditoId, nombre(archivo), (long) contenido.length, contenido, usuarioId, ahora));
        }
        auditoria.registrar(usuarioId, "DOCUMENTOS_CREDITO", "CREDITO", "credito", creditoId, null, String.valueOf(archivos.size()), null);
        return documentos.delCredito(creditoId);
    }

    @Transactional(readOnly = true)
    public List<DocumentoVista> misDocumentos(Long usuarioId, Long creditoId) {
        propio(usuarioId, creditoId);
        return documentos.delCredito(creditoId);
    }

    @Transactional(readOnly = true)
    public List<DocumentoVista> listar(Long creditoId) {
        return documentos.delCredito(creditoId);
    }

    @Transactional(readOnly = true)
    public Descarga descargar(Long creditoId, Long documentoId) {
        DocumentoCredito d = documentos.findById(documentoId).filter(x -> x.getCreditoId().equals(creditoId))
                .orElseThrow(() -> NegocioException.noEncontrado("Documento no encontrado"));
        return new Descarga(d.getNombre(), d.getContenido());
    }

    private Credito propio(Long usuarioId, Long creditoId) {
        Long personaId = personas.dePorUsuario(usuarioId).orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado")).getId();
        return creditos.findById(creditoId).filter(c -> c.getPersonaId().equals(personaId))
                .orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
    }

    /** Se comprueba el contenido: el nombre y el tipo declarados los decide quien sube el archivo. */
    private static byte[] contenido(MultipartFile archivo) {
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DOCUMENTO_MUY_GRANDE", "Cada documento puede pesar hasta 10 MB");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length < CABECERA_PDF.length || !Arrays.equals(Arrays.copyOf(bytes, CABECERA_PDF.length), CABECERA_PDF)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DOCUMENTO_NO_PDF", "Solo se admiten documentos PDF");
        }
        return bytes;
    }

    private static String nombre(MultipartFile archivo) {
        String original = archivo.getOriginalFilename() == null ? "documento.pdf" : archivo.getOriginalFilename();
        String limpio = original.replaceAll(".*[/\\\\]", "").replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (limpio.isEmpty()) {
            return "documento.pdf";
        }
        return limpio.length() > 255 ? limpio.substring(limpio.length() - 255) : limpio;
    }
}
