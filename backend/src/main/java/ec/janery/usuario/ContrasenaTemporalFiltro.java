package ec.janery.usuario;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Con una contraseña temporal solo se puede cambiarla. La temporal la dictó otra persona: si pudiera operar con ella,
 * quien la conoce actuaría en nombre del titular. El token de acceso lleva la marca {@link #RECLAMO} mientras dure.
 *
 * <p>Se permiten la sesión (cambiar contraseña, cerrar sesión, datos propios) y lo que muestra la cabecera.
 */
public class ContrasenaTemporalFiltro extends OncePerRequestFilter {

    public static final String RECLAMO = "cambiar_contrasena";

    private static final List<String> PERMITIDAS = List.of("/api/v1/auth/", "/api/v1/public/", "/api/v1/eventos");
    private static final List<String> LECTURAS_DE_CABECERA = List.of("/api/v1/anuncio", "/api/v1/simulacion");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (debeCambiar() && !permitida(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"codigo":"CAMBIO_CONTRASENA_REQUERIDO","mensaje":"Cambie su contraseña temporal antes de continuar",\
                    "detalles":[],"correlacion":"%s"}""".formatted(ec.janery.common.CorrelacionFiltro.actual()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean debeCambiar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(RECLAMO));
    }

    private static boolean permitida(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (PERMITIDAS.stream().anyMatch(ruta::startsWith) || "/api/v1/uso/visitas".equals(ruta)) {
            return true;
        }
        return HttpMethod.GET.matches(request.getMethod()) && LECTURAS_DE_CABECERA.contains(ruta);
    }
}
