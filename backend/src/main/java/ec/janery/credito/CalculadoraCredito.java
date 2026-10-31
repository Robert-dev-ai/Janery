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
     * Capital constante; interés y desgravamen sobre el capital pendiente. El ahorro letras completa cada letra
     * hasta el dólar entero, y la última absorbe el redondeo del capital.
     */
    static List<Letra> letras(BigDecimal importe, int meses, BigDecimal tasaInteres, BigDecimal tasaDesgravamen,
            LocalDate inicio) {
        BigDecimal capitalBase = importe.divide(BigDecimal.valueOf(meses), 2, RoundingMode.DOWN);
        BigDecimal pendiente = importe;
        List<Letra> letras = new ArrayList<>(meses);
        for (int n = 1; n <= meses; n++) {
            BigDecimal capital = n == meses ? pendiente : capitalBase;
            BigDecimal interes = porcentaje(pendiente, tasaInteres);
            BigDecimal desgravamen = porcentaje(pendiente, tasaDesgravamen);
            BigDecimal suma = capital.add(interes).add(desgravamen);
            BigDecimal total = suma.setScale(0, RoundingMode.CEILING).setScale(2);
            letras.add(new Letra(n, inicio.plusMonths(n), capital, total.subtract(suma), desgravamen, interes, total));
            pendiente = pendiente.subtract(capital);
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
