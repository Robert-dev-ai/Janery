package ec.janery.socio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reparte un importe según los valores de cada socio. Cada parte se redondea hacia abajo: los centavos que sobran no se
 * asignan a nadie. Si algún socio no alcanzaría ni un centavo, no se reparte nada: el importe aún no es repartible.
 */
final class RepartoProporcional {

    private RepartoProporcional() {
    }

    static Map<Long, BigDecimal> repartir(BigDecimal importe, Map<Long, BigDecimal> valores) {
        BigDecimal total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("No hay participación que repartir");
        }
        Map<Long, BigDecimal> partes = new LinkedHashMap<>();
        for (var valor : valores.entrySet()) {
            BigDecimal parte = importe.multiply(valor.getValue()).divide(total, 2, RoundingMode.DOWN);
            if (parte.signum() <= 0) {
                return Map.of();
            }
            partes.put(valor.getKey(), parte);
        }
        return partes;
    }
}
