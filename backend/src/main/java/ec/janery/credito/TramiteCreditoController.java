package ec.janery.credito;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.persona.PersonaController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Trámite de créditos en ventanilla: tesorería solicita en nombre de la persona. */
@RestController
@RequestMapping("/api/v1/admin/creditos/tramite")
@PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
@RequiredArgsConstructor
public class TramiteCreditoController {

    public record DatosRequest(@NotBlank @Size(max = 200) String direccion, @NotNull @Valid PersonaController.DatosCreditoRequest datos) {
    }

    private final TramiteCreditoService service;
    private final DocumentoCreditoService documentos;

    @GetMapping("/personas")
    public List<TramiteCreditoService.PersonaTramite> buscar(@RequestParam(defaultValue = "") String buscar) {
        return service.buscar(buscar);
    }

    @GetMapping("/personas/{usuarioId}/perfil")
    public PersonaController.PerfilResponse perfil(@PathVariable Long usuarioId) {
        return service.perfil(usuarioId);
    }

    @PutMapping("/personas/{usuarioId}/datos-credito")
    public PersonaController.PerfilResponse datos(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @Valid @RequestBody DatosRequest r) {
        return service.actualizarDatos(id(jwt), usuarioId, r.direccion().trim(), r.datos());
    }

    @GetMapping("/personas/{usuarioId}/productos")
    public CreditoService.Productos productos(@PathVariable Long usuarioId) {
        return service.productos(usuarioId);
    }

    @PostMapping("/personas/{usuarioId}/simulacion")
    public CreditoService.Simulacion simular(@PathVariable Long usuarioId, @Valid @RequestBody CreditoController.LetrasRequest r) {
        return service.simular(usuarioId, r.modalidad(), r.importe(), r.plazoMeses());
    }

    @PostMapping("/personas/{usuarioId}/letras")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditoController.CreadoResponse letras(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @Valid @RequestBody CreditoController.LetrasRequest r) {
        return new CreditoController.CreadoResponse(service.solicitarLetras(id(jwt), usuarioId, r.modalidad(), r.importe(),
                r.plazoMeses(), r.origenEncaje(), r.ahorroEncajeId()));
    }

    @PostMapping("/personas/{usuarioId}/emergente")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditoController.CreadoResponse emergente(@AuthenticationPrincipal Jwt jwt, @PathVariable Long usuarioId,
            @RequestHeader("Idempotency-Key") @jakarta.validation.constraints.NotBlank @Size(max = 100) String clave, @Valid @RequestBody CreditoController.EmergenteRequest r) {
        return new CreditoController.CreadoResponse(service.solicitarEmergente(id(jwt), usuarioId, r.importe(), clave));
    }

    @PostMapping(path = "/{creditoId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DocumentoCreditoService.DocumentoVista> subir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId,
            @RequestParam("archivos") List<MultipartFile> archivos) {
        return documentos.subirPorPersonal(id(jwt), creditoId, archivos);
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
