package ec.janery.credito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cálculos puros de crédito. Las fórmulas se describen en docs/reglas-negocio.md, sección 10. */
final class CalculadoraCredito {

    record Letra(int numero, LocalDate vencimiento, BigDecimal capital, BigDecimal ahorroLetras,
            BigDecimal fondoDesgravamen, BigDecimal interes, BigDecimal total) {
    }

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraCredito() {
    }

    /**
     * Letra fija de principio a fin, en dólares enteros. Interés y desgravamen son fijos sobre el capital sacado. La
     * última letra lleva los centavos que faltan de capital, y por eso esos mismos centavos menos de ahorro letras.
     */
    static List<Letra> letras(BigDecimal importe, int meses, BigDecimal tasaInteres, BigDecimal tasaDesgravamen,
            LocalDate inicio) {
        BigDecimal capitalBase = importe.divide(BigDecimal.valueOf(meses), 2, RoundingMode.DOWN);
        BigDecimal capitalUltima = importe.subtract(capitalBase.multiply(BigDecimal.valueOf(meses - 1)));
        BigDecimal interes = porcentaje(importe, tasaInteres);
        BigDecimal desgravamen = porcentaje(importe, tasaDesgravamen);
        // Se calcula con la última, la de más capital: así el ahorro letras nunca queda negativo.
        BigDecimal total = capitalUltima.add(interes).add(desgravamen).setScale(0, RoundingMode.CEILING).setScale(2);
        List<Letra> letras = new ArrayList<>(meses);
        for (int n = 1; n <= meses; n++) {
            BigDecimal capital = n == meses ? capitalUltima : capitalBase;
            BigDecimal ahorro = total.subtract(capital).subtract(interes).subtract(desgravamen);
            letras.add(new Letra(n, inicio.plusMonths(n), capital, ahorro, desgravamen, interes, total));
        }
        return letras;
    }

    static BigDecimal porcentaje(BigDecimal base, BigDecimal tasa) {
        return base.multiply(tasa).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    /** Cargo simple de {@code dias} sobre el capital, sin redondear: se acumula y se redondea al cobrar. */
    static BigDecimal devengo(BigDecimal capital, BigDecimal tasaDiaria, long dias) {
        return capital.multiply(tasaDiaria).multiply(BigDecimal.valueOf(dias)).divide(CIEN, 6, RoundingMode.HALF_UP);
    }
}
