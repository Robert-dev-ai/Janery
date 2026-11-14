package ec.janery.correo;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Brevo, por su API HTTPS. El plan gratuito de Render bloquea la salida a los puertos de correo (SMTP), así que Gmail
 * por SMTP no funcionaría publicado; por HTTPS sí. El remitente (por ejemplo el Gmail de la caja) se verifica en Brevo.
 */
@Component
class BrevoCorreo implements EnvioCorreo {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private final String clave;
    private final String remitente;
    private final String nombreRemitente;
    private final RestClient http;

    BrevoCorreo(@Value("${janery.correo.brevo-api-key:}") String clave, @Value("${janery.correo.remitente:}") String remitente,
            @Value("${janery.correo.remitente-nombre:Janery}") String nombreRemitente) {
        this.clave = clave == null ? "" : clave.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        // Quien pide el enlace espera la respuesta: si Brevo no contesta, mejor fallar pronto y avisar a la presidencia.
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public boolean disponible() {
        return !clave.isEmpty() && !remitente.isEmpty();
    }

    @Override
    public void enviar(String para, String asunto, String texto, String html) {
        if (!disponible()) {
            throw new IllegalStateException("El envío de correos no está configurado");
        }
        http.post().uri(URL).header("api-key", clave).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(Map.of("sender", Map.of("name", nombreRemitente, "email", remitente), "to", List.of(Map.of("email", para)),
                        "subject", asunto, "textContent", texto, "htmlContent", html))
                .retrieve().toBodilessEntity();
    }
}
