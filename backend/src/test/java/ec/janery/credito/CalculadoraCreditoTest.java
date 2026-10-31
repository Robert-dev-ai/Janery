package ec.janery.credito;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import ec.janery.credito.CalculadoraCredito.Letra;

class CalculadoraCreditoTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 31);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void letraCompletaHastaElDolarEnteroConAhorroLetras() {
        List<Letra> letras = CalculadoraCredito.letras(bd("1000.00"), 10, bd("1"), bd("0.1"), INICIO);

        // 100 + 10 + 1 = 111: ya es entero.
        assertThat(letras.get(0).total()).isEqualByComparingTo("111");
        assertThat(letras.get(0).ahorroLetras()).isEqualByComparingTo("0");
        // 100 + 9 + 0,90 = 109,90: se completa con 0,10.
        assertThat(letras.get(1).interes()).isEqualByComparingTo("9");
        assertThat(letras.get(1).fondoDesgravamen()).isEqualByComparingTo("0.90");
        assertThat(letras.get(1).ahorroLetras()).isEqualByComparingTo("0.10");
        assertThat(letras.get(1).total()).isEqualByComparingTo("110");
    }

    @Test
    void todasLasLetrasSonEnterasYElCapitalSumaExactamenteElImporte() {
        List<Letra> letras = CalculadoraCredito.letras(bd("1000.00"), 7, bd("1.5"), bd("0.1"), INICIO);

        assertThat(letras).hasSize(7);
        assertThat(letras.stream().map(Letra::capital).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1000");
        assertThat(letras).allSatisfy(l -> {
            assertThat(l.total().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
            assertThat(l.capital().add(l.interes()).add(l.fondoDesgravamen()).add(l.ahorroLetras()))
                    .isEqualByComparingTo(l.total());
            assertThat(l.ahorroLetras()).isLessThan(BigDecimal.ONE);
        });
    }

    @Test
    void vencimientosMensualesRespetanElFinDeMes() {
        List<Letra> letras = CalculadoraCredito.letras(bd("300.00"), 3, bd("1"), bd("0"), INICIO);

        assertThat(letras.get(0).vencimiento()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(letras.get(1).vencimiento()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void devengoDiarioSobreElCapitalDelEjemploDelPropietario() {
        // USD 100 al 0,12 % diario durante 10 días; USD 150 tras otra disposición.
        assertThat(CalculadoraCredito.devengo(bd("100"), bd("0.12"), 10)).isEqualByComparingTo("1.20");
        assertThat(CalculadoraCredito.devengo(bd("150"), bd("0.12"), 1)).isEqualByComparingTo("0.18");
        assertThat(CalculadoraCredito.devengo(bd("100"), bd("0.12"), 0)).isEqualByComparingTo("0");
    }
}
