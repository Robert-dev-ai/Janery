package ec.janery.simulacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import lombok.RequiredArgsConstructor;

@Service
@Profile("simulacion")
@RequiredArgsConstructor
public class SimulacionService {

    private static final int MAX_DIAS = 3660;

    public record Estado(LocalDate fecha, Instant ahora, long diasAdelantados) {
    }

    private final RelojSimulado reloj;
    private final AuditoriaService auditoria;

    public Estado estado() {
        return new Estado(LocalDate.now(reloj), reloj.instant(), reloj.diasAdelantados());
    }

    /**
     * Interés de ahorros y mora se calculan con la fecha al consultarlos: no hay procesos que ejecutar al cruzar un mes.
     * Se indica una sola de las dos opciones: días a sumar o fecha de destino.
     */
    public synchronized Estado avanzar(Long actorId, Integer dias, LocalDate fecha) {
        if ((dias == null) == (fecha == null)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DESTINO_INVALIDO", "Indique los días o la fecha, solo uno de los dos");
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (dias != null && dias > MAX_DIAS) {
            throw excesivo();
        }
        LocalDate destino = fecha != null ? fecha : hoy.plusDays(dias);
        if (!destino.isAfter(hoy)) {
            throw NegocioException.conflicto("FECHA_NO_FUTURA", "La fecha simulada solo puede avanzar");
        }
        if (destino.isAfter(hoy.plusDays(MAX_DIAS))) {
            throw excesivo();
        }
        LocalTime hora = LocalTime.now(reloj);
        reloj.avanzarHasta(destino.atTime(hora).atZone(reloj.getZone()).toInstant());
        auditoria.registrar(actorId, "RELOJ_AVANZADO", "SIMULACION", "reloj", null, hoy.toString(), destino.toString(), null);
        return estado();
    }

    private static NegocioException excesivo() {
        return NegocioException.conflicto("SALTO_EXCESIVO", "No se puede avanzar más de " + MAX_DIAS + " días de una vez");
    }
}
