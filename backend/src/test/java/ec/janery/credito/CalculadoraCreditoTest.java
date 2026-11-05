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
    void ejemploDelPropietarioCienATresMeses() {
        List<Letra> letras = CalculadoraCredito.letras(bd("100.00"), 3, bd("1"), bd("0.1"), INICIO);

        // Interés 1 y desgravamen 0,10 fijos; la última lleva el centavo de capital y un centavo menos de ahorro.
        assertThat(letras).extracting(Letra::capital).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("33.33"), bd("33.33"), bd("33.34"));
        assertThat(letras).extracting(Letra::ahorroLetras).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("0.57"), bd("0.57"), bd("0.56"));
        assertThat(letras).allSatisfy(l -> {
            assertThat(l.interes()).isEqualByComparingTo("1");
            assertThat(l.fondoDesgravamen()).isEqualByComparingTo("0.10");
            assertThat(l.total()).isEqualByComparingTo("35");
        });
    }

    @Test
    void laLetraEsIgualHastaElFinalYElCapitalSumaExactamenteElImporte() {
        for (String importe : new String[] { "1000.00", "100.00", "333.33", "777.77" }) {
            for (int meses : new int[] { 3, 6, 7, 11, 36 }) {
                List<Letra> letras = CalculadoraCredito.letras(bd(importe), meses, bd("1.5"), bd("0.1"), INICIO);

                assertThat(letras).hasSize(meses);
                assertThat(letras.stream().map(Letra::capital).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(importe);
                assertThat(letras).allSatisfy(l -> {
                    assertThat(l.total()).isEqualByComparingTo(letras.get(0).total());
                    assertThat(l.total().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
                    assertThat(l.ahorroLetras().signum()).isGreaterThanOrEqualTo(0);
                    assertThat(l.capital().add(l.interes()).add(l.fondoDesgravamen()).add(l.ahorroLetras()))
                            .isEqualByComparingTo(l.total());
                });
            }
        }
    }

    @Test
    void cadaLetraAhorraAlMenosCincuentaCentavos() {
        // 30 + 1 + 0 = 31 exactos: sin el mínimo, la letra no ahorraría nada; con él sube a 32.
        List<Letra> letras = CalculadoraCredito.letras(bd("90.00"), 3, bd("1.111112"), bd("0"), INICIO);
        assertThat(letras).allSatisfy(l -> assertThat(l.ahorroLetras()).isGreaterThanOrEqualTo(bd("0.50")));
        assertThat(letras.get(0).total()).isEqualByComparingTo("32");
        for (String importe : new String[] { "1000.00", "100.00", "333.33", "777.77" }) {
            for (int meses : new int[] { 3, 6, 7, 11, 36 }) {
                assertThat(CalculadoraCredito.letras(bd(importe), meses, bd("1.5"), bd("0.1"), INICIO))
                        .allSatisfy(l -> assertThat(l.ahorroLetras()).isGreaterThanOrEqualTo(bd("0.50")));
            }
        }
    }

    @Test
    void siLaSumaYaEsEnteraEnLaBaseElAhorroNoQuedaNegativo() {
        // 33,33 + 0,67 = 34 exactos; con la última (33,34) el total sube a 35.
        List<Letra> letras = CalculadoraCredito.letras(bd("100.00"), 3, bd("0.67"), bd("0"), INICIO);
        assertThat(letras.get(2).ahorroLetras().signum()).isGreaterThanOrEqualTo(0);
        assertThat(letras.get(0).total()).isEqualByComparingTo(letras.get(2).total());
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

    /** Ejemplos de docs/reglas-negocio.md: 12 letras exigen 7; 5 letras exigen 3. */
    @Test
    void precancelarExigeLaMitadRedondeadaHaciaAbajoMasUna() {
        assertThat(CalculadoraCredito.letrasParaPrecancelar(12)).isEqualTo(7);
        assertThat(CalculadoraCredito.letrasParaPrecancelar(5)).isEqualTo(3);
        assertThat(CalculadoraCredito.letrasParaPrecancelar(3)).isEqualTo(2);
    }

    @Test
    void unaLineaMuestraLoDevengadoSinGuardarloYDevengarLoFijaUnaSolaVez() {
        Entidades.Credito c = linea(bd("100"), bd("0.12"), INICIO);
        LocalDate diezDias = INICIO.plusDays(10);

        assertThat(c.interesHasta(diezDias)).isEqualByComparingTo("1.20");
        assertThat(c.getInteresAcumulado()).isEqualByComparingTo("0");

        c.devengar(diezDias);
        c.devengar(diezDias);
        assertThat(c.getInteresAcumulado()).isEqualByComparingTo("1.20");
        assertThat(c.getUltimoDevengo()).isEqualTo(diezDias);
        // Una fecha anterior al último devengo no resta.
        assertThat(c.interesHasta(INICIO)).isEqualByComparingTo("1.20");
    }

    @Test
    void soloLosCuposSonLineas() {
        Entidades.Credito c = new Entidades.Credito();
        for (String familia : new String[] { "EMERGENTE_SOCIO", "EMERGENTE_ESTACIONAL" }) {
            c.setFamilia(familia);
            assertThat(c.esLinea()).as(familia).isTrue();
        }
        for (String familia : new String[] { "LETRAS", "EMERGENTE_CLIENTE" }) {
            c.setFamilia(familia);
            assertThat(c.esLinea()).as(familia).isFalse();
        }
    }

    private static Entidades.Credito linea(BigDecimal capital, BigDecimal tasaDiaria, LocalDate desde) {
        Entidades.Credito c = new Entidades.Credito();
        c.setFamilia("EMERGENTE_SOCIO");
        c.setCapitalPendiente(capital);
        c.setTasaInteres(tasaDiaria);
        c.setTasaDesgravamen(BigDecimal.ZERO);
        c.setUltimoDevengo(desde);
        return c;
    }
}
