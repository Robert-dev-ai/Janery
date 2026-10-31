package ec.janery.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permite al frontend comprobar la conectividad con la API. */
@RestController
@RequestMapping("/api/v1/public/estado")
public class EstadoController {

    public record EstadoResponse(String servicio, String estado) {
    }

    @GetMapping
    public EstadoResponse estado() {
        return new EstadoResponse("janery", "disponible");
    }
}
