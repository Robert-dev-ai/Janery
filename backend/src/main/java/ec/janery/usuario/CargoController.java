package ec.janery.usuario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CargoController {

    private final CargoService service;

    @GetMapping("/areas/me")
    public List<CargoService.CargoVista> misAreas(@AuthenticationPrincipal Jwt jwt) {
        return service.misAreas(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/admin/cargos")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
    public List<CargoService.CargoVista> cargos() {
        return service.cargos();
    }

    @GetMapping("/admin/permisos")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
    public List<CargoService.PermisoVista> permisos() {
        return service.permisos();
    }

    @PostMapping("/admin/cargos/{cargo}/permisos/{permiso}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void conceder(@AuthenticationPrincipal Jwt jwt, @PathVariable String cargo, @PathVariable String permiso) {
        service.conceder(Long.valueOf(jwt.getSubject()), cargo, permiso);
    }

    @DeleteMapping("/admin/cargos/{cargo}/permisos/{permiso}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_USUARIOS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirar(@AuthenticationPrincipal Jwt jwt, @PathVariable String cargo, @PathVariable String permiso) {
        service.retirar(Long.valueOf(jwt.getSubject()), cargo, permiso);
    }
}
