package ec.janery.tiemporeal;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Avisa a los navegadores conectados de que algo cambió. El aviso solo dice el módulo, sin datos: cada pantalla vuelve
 * a pedir lo suyo a la API, que es quien valida permisos y titularidad.
 */
@Service
public class EventosService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventosService.class);

    /** Algo más que el token de acceso: al cerrarse, el navegador reconecta con un token vigente. */
    private static final long DURACION_MS = 16 * 60 * 1000L;

    private final List<SseEmitter> conexiones = new CopyOnWriteArrayList<>();

    public SseEmitter conectar() {
        SseEmitter conexion = new SseEmitter(DURACION_MS);
        conexion.onCompletion(() -> conexiones.remove(conexion));
        conexion.onTimeout(conexion::complete);
        conexion.onError(e -> conexiones.remove(conexion));
        conexiones.add(conexion);
        enviar(conexion, SseEmitter.event().comment("conectado"));
        return conexion;
    }

    /** Se envía al confirmarse la transacción: una operación revertida no debe avisar, ni la pantalla leer antes de tiempo. */
    public void publicar(String modulo) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    difundir(modulo);
                }
            });
        } else {
            difundir(modulo);
        }
    }

    /** Mantiene viva la conexión a través de proxies, y detecta los navegadores que ya se fueron. */
    @Scheduled(fixedRate = 25_000)
    void latido() {
        conexiones.forEach(c -> enviar(c, SseEmitter.event().comment("latido")));
    }

    private void difundir(String modulo) {
        conexiones.forEach(c -> enviar(c, SseEmitter.event().name("cambio").data(modulo)));
    }

    /**
     * Nunca lanza: se llama tras confirmar operaciones de dinero, y un fallo aquí devolvería un error al cliente por una
     * operación que sí se guardó. Una conexión que falla se descarta; el navegador vuelve a conectarse solo.
     */
    private void enviar(SseEmitter conexion, SseEmitter.SseEventBuilder evento) {
        try {
            conexion.send(evento);
        } catch (IOException | IllegalStateException e) {
            conexiones.remove(conexion);
        } catch (RuntimeException e) {
            conexiones.remove(conexion);
            log.warn("No se pudo avisar a una pantalla conectada: {}", e.toString());
        }
    }
}
