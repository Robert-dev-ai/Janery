package ec.janery.socio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RepartoProporcionalTest {

    private static Map<Long, BigDecimal> valores(String... v) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < v.length; i++) {
            m.put((long) i, new BigDecimal(v[i]));
        }
        return m;
    }

    @Test
    void repartePorProporcion() {
        Map<Long, BigDecimal> partes = RepartoProporcional.repartir(new BigDecimal("100.00"), valores("300", "100"));
        assertThat(partes.get(0L)).isEqualByComparingTo("75");
        assertThat(partes.get(1L)).isEqualByComparingTo("25");
    }

    @Test
    void losCentavosQueSobranNoSeAsignanANadie() {
        Map<Long, BigDecimal> partes = RepartoProporcional.repartir(new BigDecimal("0.03"), valores("1", "1"));
        assertThat(partes.get(0L)).isEqualByComparingTo("0.01");
        assertThat(partes.get(1L)).isEqualByComparingTo("0.01");
    }

    @Test
    void siUnSocioNoAlcanzaUnCentavoNoSeReparteNada() {
        assertThat(RepartoProporcional.repartir(new BigDecimal("0.01"), valores("1", "1", "2"))).isEmpty();
        assertThat(RepartoProporcional.repartir(new BigDecimal("0.04"), valores("1", "1", "2"))).hasSize(3);
    }

    @Test
    void sinParticipacionNoHayReparto() {
        assertThatThrownBy(() -> RepartoProporcional.repartir(BigDecimal.ONE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
