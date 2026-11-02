package ec.janery.cuenta;

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
public class ComprobanteController {

    private final ComprobanteService service;

    /** tipo: APERTURA (id de la cuenta) o DEPOSITO (id del aviso de depósito). */
    @PostMapping(path = "/comprobantes/{tipo}/{referenciaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ComprobanteService.Vista> subir(@AuthenticationPrincipal Jwt jwt, @PathVariable String tipo,
            @PathVariable Long referenciaId, @RequestParam("archivos") List<MultipartFile> archivos) {
        return service.subir(Long.valueOf(jwt.getSubject()), tipo.toUpperCase(), referenciaId, archivos);
    }

    @GetMapping("/comprobantes/{tipo}/{referenciaId}")
    public List<ComprobanteService.Vista> mios(@AuthenticationPrincipal Jwt jwt, @PathVariable String tipo,
            @PathVariable Long referenciaId) {
        return service.mios(Long.valueOf(jwt.getSubject()), tipo.toUpperCase(), referenciaId);
    }

    @GetMapping("/admin/comprobantes/{tipo}/{referenciaId}")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public List<ComprobanteService.Vista> listar(@PathVariable String tipo, @PathVariable Long referenciaId) {
        return service.listar(tipo.toUpperCase(), referenciaId);
    }

    /** Siempre como descarga: el navegador no debe abrir dentro de la aplicación algo subido por otra persona. */
    @GetMapping("/admin/comprobantes/archivo/{id}")
    @PreAuthorize("hasAuthority('SCOPE_GESTIONAR_CUENTAS')")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        ComprobanteService.Descarga d = service.descargar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(d.contenido());
    }
}
