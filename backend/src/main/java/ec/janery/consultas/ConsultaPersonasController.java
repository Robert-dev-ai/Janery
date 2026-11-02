package ec.janery.consultas;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.NegocioException;
import ec.janery.common.Pagina;
import ec.janery.socio.SocioService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConsultaPersonasController {

    private final ConsultaPersonasService service;
    private final SocioService socios;

    // --- Área general: exclusiva de socios activos. No depende de un cargo o permiso, sino de ser socio.

    @GetMapping("/socios/general/resumen")
    public ConsultaPersonasService.ResumenGeneral resumen(@AuthenticationPrincipal Jwt jwt) {
        exigirSocio(jwt);
        return service.resumenGeneral();
    }

    @GetMapping("/socios/general/personas")
    public Pagina<ConsultaPersonasService.PersonaFinanciera> personas(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String buscar, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        exigirSocio(jwt);
        return service.personasFinancieras(buscar, Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @GetMapping("/socios/general/personas/{id}")
    public ConsultaPersonasService.DetalleFinanciero persona(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        exigirSocio(jwt);
        return service.detalleFinanciero(id);
    }

    @GetMapping("/socios/general/movimientos")
    public Pagina<ConsultaPersonasService.MovimientoGeneral> movimientos(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        exigirSocio(jwt);
        return service.movimientos(Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    // --- Gestión

    @GetMapping("/admin/personas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
    public Pagina<ConsultaPersonasService.PersonaContacto> personasContacto(@RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.personasContacto(buscar, Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @GetMapping("/admin/historial-crediticio")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public Pagina<ConsultaPersonasService.HistorialCrediticio> historiales(@RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.historiales(buscar, Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @GetMapping("/admin/creditos/{creditoId}/solicitante")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public ConsultaPersonasService.SolicitanteCredito solicitante(@PathVariable Long creditoId) {
        return service.solicitante(creditoId);
    }

    @GetMapping("/admin/historial-crediticio/{personaId}")
    @PreAuthorize("hasAuthority('SCOPE_APROBAR_CREDITOS')")
    public List<ConsultaPersonasService.CreditoDetalle> historial(@PathVariable Long personaId) {
        return service.creditosDe(personaId);
    }

    private void exigirSocio(Jwt jwt) {
        if (!socios.usuarioEsSocio(Long.valueOf(jwt.getSubject()))) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "SOLO_SOCIOS", "Esta área es exclusiva de los socios");
        }
    }
}
