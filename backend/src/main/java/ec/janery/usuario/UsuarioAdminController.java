package ec.janery.usuario;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.common.Pagina;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioAdminService service;
    private final ec.janery.socio.SocioService socios;

    @PostMapping("/{id}/socio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarSocio(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        socios.registrar(Long.valueOf(jwt.getSubject()), id);
    }

    @GetMapping
    public Pagina<UsuarioAdminService.UsuarioResumen> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(Math.max(pagina, 0), Pagina.limitar(tamano));
    }

    @PostMapping("/{id}/cargos/{cargo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void asignar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable String cargo) {
        service.asignarCargo(Long.valueOf(jwt.getSubject()), id, cargo);
    }

    @DeleteMapping("/{id}/cargos/{cargo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable String cargo) {
        service.revocarCargo(Long.valueOf(jwt.getSubject()), id, cargo);
    }
}
