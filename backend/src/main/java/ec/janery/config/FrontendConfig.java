package ec.janery.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sirve el frontend compilado que el Dockerfile copia en {@code static/}, en el mismo dominio que la API.
 *
 * <p>Una ruta de la aplicación (por ejemplo {@code /creditos}) no es un archivo: devuelve {@code index.html} para que
 * Angular la resuelva al recargar o abrir un enlace. La API, el actuator y los archivos que faltan siguen dando 404.
 * Sin frontend compilado (desarrollo y pruebas) no sirve nada.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private static final String UBICACION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(UBICACION)
                // El navegador revalida cada archivo: tras publicar una versión nueva nadie se queda con la vieja.
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String ruta, Resource ubicacion) throws IOException {
                        Resource archivo = ubicacion.createRelative(ruta);
                        if (archivo.exists() && archivo.isReadable()) {
                            return archivo;
                        }
                        if (!esRutaDeLaAplicacion(ruta)) {
                            return null;
                        }
                        Resource indice = ubicacion.createRelative("index.html");
                        return indice.exists() && indice.isReadable() ? indice : null;
                    }
                });
    }

    /** Rutas de Angular: ni API, ni actuator, ni un archivo (con extensión) que no existe. */
    static boolean esRutaDeLaAplicacion(String ruta) {
        if (ruta.startsWith("api/") || ruta.equals("api") || ruta.startsWith("actuator")) {
            return false;
        }
        String ultimo = ruta.substring(ruta.lastIndexOf('/') + 1);
        return !ultimo.contains(".");
    }
}
