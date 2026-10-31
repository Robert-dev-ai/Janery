package ec.janery.reportes;

import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService service;

    @GetMapping("/admin/reportes")
    @PreAuthorize("hasAuthority('SCOPE_CONSULTAR_INFORMES')")
    public List<ReporteService.Definicion> catalogo() {
        return service.catalogo();
    }

    @GetMapping("/admin/reportes/{codigo}")
    @PreAuthorize("hasAuthority('SCOPE_CONSULTAR_INFORMES')")
    public ReporteService.Tabla consultar(@PathVariable String codigo, @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return service.consultar(codigo, desde, hasta);
    }

    @GetMapping("/admin/reportes/{codigo}/csv")
    @PreAuthorize("hasAuthority('SCOPE_CONSULTAR_INFORMES')")
    public ResponseEntity<byte[]> exportar(@AuthenticationPrincipal Jwt jwt, @PathVariable String codigo,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return archivo(service.exportar(Long.valueOf(jwt.getSubject()), codigo, desde, hasta));
    }

    @GetMapping("/cuentas-principales/me/estado-de-cuenta.csv")
    public ResponseEntity<byte[]> miEstadoDeCuenta(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return archivo(service.miEstadoDeCuenta(Long.valueOf(jwt.getSubject()), desde, hasta));
    }

    private static ResponseEntity<byte[]> archivo(ReporteService.Archivo archivo) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
