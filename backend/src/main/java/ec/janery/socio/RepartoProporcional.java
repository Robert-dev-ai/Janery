package ec.janery.socio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reparte un importe según los valores de cada socio. La suma es exacta: el mayor absorbe los centavos de redondeo. */
final class RepartoProporcional {

    private RepartoProporcional() {
    }

    static Map<Long, BigDecimal> repartir(BigDecimal importe, Map<Long, BigDecimal> valores) {
        BigDecimal total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("No hay participación que repartir");
        }
        Map<Long, BigDecimal> partes = new LinkedHashMap<>();
        BigDecimal asignado = BigDecimal.ZERO;
        for (var valor : valores.entrySet()) {
            BigDecimal parte = importe.multiply(valor.getValue()).divide(total, 2, RoundingMode.DOWN);
            partes.put(valor.getKey(), parte);
            asignado = asignado.add(parte);
        }
        Long mayor = valores.entrySet().stream().max(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .orElseThrow().getKey();
        partes.merge(mayor, importe.subtract(asignado), BigDecimal::add);
        return partes;
    }
}
