package ec.janery.credito;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentoCreditoController {

    private final DocumentoCreditoService service;

    @PostMapping(path = "/creditos/{creditoId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DocumentoCreditoService.DocumentoVista> subir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId,
            @RequestParam("archivos") List<MultipartFile> archivos) {
        return service.subir(Long.valueOf(jwt.getSubject()), creditoId, archivos);
    }

    @GetMapping("/creditos/{creditoId}/documentos")
    public List<DocumentoCreditoService.DocumentoVista> misDocumentos(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId) {
        return service.misDocumentos(Long.valueOf(jwt.getSubject()), creditoId);
    }

    @GetMapping("/admin/creditos/{creditoId}/documentos")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public List<DocumentoCreditoService.DocumentoVista> listar(@PathVariable Long creditoId) {
        return service.listar(creditoId);
    }

    /** Siempre como descarga: el navegador no debe abrir dentro de la aplicación algo subido por otra persona. */
    @GetMapping("/admin/creditos/{creditoId}/documentos/{documentoId}")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public ResponseEntity<byte[]> descargar(@PathVariable Long creditoId, @PathVariable Long documentoId) {
        DocumentoCreditoService.Descarga d = service.descargar(creditoId, documentoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(d.contenido());
    }
}
