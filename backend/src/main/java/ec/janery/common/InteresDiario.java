package ec.janery.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import ec.janery.parametros.ParametroService;
import lombok.RequiredArgsConstructor;

/**
 * Interés diario sin capitalizar, el mismo para el ahorro normal y la cuenta principal; cada uno con su tasa.
 *
 * <p>Definido por el propietario: tasa anual / 365 cada día sobre el saldo, sin capitalizar. Lo corrido se acumula y
 * pasa a disponible cada día 1. Se usa la tasa vigente al calcular.
 *
 * <p>Debe devengarse justo antes de cambiar el saldo: el interés corrió sobre el saldo anterior.
 */
@Component
@RequiredArgsConstructor
public class InteresDiario {

    /** Interés a hoy: lo disponible se liberó el último día 1; lo acumulado se libera el próximo. */
    public record Interes(BigDecimal acumulado, BigDecimal disponible) {
    }

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal DIAS_ANIO = BigDecimal.valueOf(365);
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(6);

    private final ParametroService parametros;
    private final Clock reloj;

    /**
     * Sin fecha de último devengo, o si ya se devengó hoy, no hay nada nuevo. El llamador guarda el resultado y pone
     * {@link #hoy()} como nuevo último devengo.
     */
    public Interes aHoy(BigDecimal saldo, BigDecimal acumulado, BigDecimal disponible, LocalDate ultimoDevengo,
            String parametroTasa) {
        LocalDate hoy = hoy();
        if (ultimoDevengo == null || !ultimoDevengo.isBefore(hoy)) {
            return new Interes(acumulado, disponible);
        }
        BigDecimal diario = saldo.multiply(parametros.valorVigente(parametroTasa));
        LocalDate desde = ultimoDevengo;
        LocalDate primero = hoy.withDayOfMonth(1);
        if (desde.isBefore(primero)) {
            disponible = disponible.add(acumulado).add(interesDe(diario, desde, primero));
            acumulado = CERO;
            desde = primero;
        }
        return new Interes(acumulado.add(interesDe(diario, desde, hoy)), disponible);
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** Día en que lo acumulado pasa a disponible. */
    public LocalDate proximaLiberacion() {
        return hoy().withDayOfMonth(1).plusMonths(1);
    }

    private static BigDecimal interesDe(BigDecimal saldoPorTasa, LocalDate desde, LocalDate hasta) {
        return saldoPorTasa.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(desde, hasta)))
                .divide(CIEN.multiply(DIAS_ANIO), 6, RoundingMode.HALF_UP);
    }
}
