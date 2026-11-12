package ec.janery.common;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Un código por petición, el mismo en la respuesta de error, en la cabecera y en cada línea del log
 * ({@code logging.pattern.correlation}): con el código que ve la persona se encuentra lo ocurrido en el log.
 *
 * <p>Si un proxy ya trae uno, se respeta; solo se aceptan letras, números y guiones, para que no se pueda inyectar texto
 * en el log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionFiltro extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    static final String CLAVE_LOG = "correlacion";
    private static final Pattern VALIDA = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(CABECERA);
        String id = recibida != null && VALIDA.matcher(recibida).matches() ? recibida : UUID.randomUUID().toString();
        MDC.put(CLAVE_LOG, id);
        response.setHeader(CABECERA, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CLAVE_LOG);
        }
    }

    /** El código de la petición en curso; uno nuevo fuera de una petición. */
    public static String actual() {
        String id = MDC.get(CLAVE_LOG);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
