package ec.janery.tiemporeal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EventosController {

    private final EventosService service;

    @GetMapping(path = "/api/v1/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return service.conectar(Long.valueOf(jwt.getSubject()));
    }
}
