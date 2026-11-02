package ec.janery.common;

import java.math.BigDecimal;

/** Un ingreso o gasto real: se reparte entre los socios en la misma transacción. tipo: GANANCIA o PERDIDA. */
public record ResultadoGenerado(String tipo, BigDecimal importe, String concepto, Long actorId) {
}
