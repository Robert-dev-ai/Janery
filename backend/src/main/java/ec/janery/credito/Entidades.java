package ec.janery.credito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

final class Entidades {

    private Entidades() {
    }

    @Entity(name = "Credito")
    @Table(name = "creditos")
    @Getter
    @Setter
    @NoArgsConstructor
    static class Credito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long personaId;
        private Instant precanceladoEn;
        private String familia;
        private String modalidad;
        private String perfil;
        private String estado;
        private BigDecimal importe = BigDecimal.ZERO;
        private Integer plazoMeses;
        private BigDecimal tasaInteres;
        private BigDecimal tasaDesgravamen;
        private String unidadTasa;
        private BigDecimal porcentajeEncaje;
        /** Null en los créditos sin cobro de suministros: emergentes, cupos y los anteriores a Janery. */
        private BigDecimal porcentajeSuministros;
        private BigDecimal capitalPendiente = BigDecimal.ZERO;
        private BigDecimal interesAcumulado = BigDecimal.ZERO;
        private BigDecimal desgravamenAcumulado = BigDecimal.ZERO;
        private LocalDate ultimoDevengo;
        private Integer anioPromocion;
        private LocalDate fechaInicio;
        private String motivoRechazo;
        private Instant finalizadoEn;
        private Instant creadoEn;
        private String origenEncaje;
        private Long ahorroEncajeId;
        private BigDecimal tasaMora = BigDecimal.ZERO;
        private String claveSolicitud;
        /** Registrado con sus condiciones originales: ya existía antes de usar Janery. */
        private boolean existente;

        /** Cupo de socio, general o de promoción: sin letras, devenga cargos por día sobre el capital pendiente. */
        boolean esLinea() {
            return "EMERGENTE_SOCIO".equals(familia) || "EMERGENTE_ESTACIONAL".equals(familia);
        }

        /** Interés de una línea hasta hoy, con lo aún no guardado. Sin redondear: se redondea al mostrar o cobrar. */
        BigDecimal interesHasta(LocalDate hoy) {
            return interesAcumulado.add(CalculadoraCredito.devengo(capitalPendiente, tasaInteres, diasSinDevengar(hoy)));
        }

        BigDecimal desgravamenHasta(LocalDate hoy) {
            return desgravamenAcumulado.add(CalculadoraCredito.devengo(capitalPendiente, tasaDesgravamen, diasSinDevengar(hoy)));
        }

        /** Guarda los cargos de una línea hasta hoy. Va antes de cambiar el capital o las tasas: lo devengado conserva los suyos. */
        void devengar(LocalDate hoy) {
            if (diasSinDevengar(hoy) > 0) {
                interesAcumulado = interesHasta(hoy);
                desgravamenAcumulado = desgravamenHasta(hoy);
                ultimoDevengo = hoy;
            }
        }

        private long diasSinDevengar(LocalDate hoy) {
            return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(ultimoDevengo, hoy));
        }
    }

    /** Un retiro del cupo de socio, en espera de aprobación. */
    @Entity(name = "SolicitudCupo")
    @Table(name = "solicitudes_cupo")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class SolicitudCupo {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long personaId;
        private BigDecimal importe;
        private String promocion;
        private String estado;
        private String motivo;
        private String clave;
        private Long resueltaPor;
        private Instant resueltaEn;
        private Instant creadoEn;
    }

    @Entity(name = "CuotaCredito")
    @Table(name = "cuotas_credito")
    @Getter
    @Setter
    @NoArgsConstructor
    static class CuotaCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private Integer numero;
        private LocalDate vencimiento;
        private BigDecimal capital;
        private BigDecimal ahorroLetras;
        private BigDecimal fondoDesgravamen;
        private BigDecimal interes;
        private BigDecimal total;
        private BigDecimal capitalPagado = BigDecimal.ZERO;
        private BigDecimal interesPagado = BigDecimal.ZERO;
        private BigDecimal desgravamenPagado = BigDecimal.ZERO;
        private BigDecimal ahorroPagado = BigDecimal.ZERO;
        private BigDecimal moraPagada = BigDecimal.ZERO;
        private LocalDate moraHasta;
        private Instant pagadaEn;

        CuotaCredito(Long creditoId, Integer numero, LocalDate vencimiento, BigDecimal capital, BigDecimal ahorroLetras,
                BigDecimal fondoDesgravamen, BigDecimal interes, BigDecimal total) {
            this.creditoId = creditoId;
            this.numero = numero;
            this.vencimiento = vencimiento;
            this.capital = capital;
            this.ahorroLetras = ahorroLetras;
            this.fondoDesgravamen = fondoDesgravamen;
            this.interes = interes;
            this.total = total;
        }

        BigDecimal pagado() {
            return capitalPagado.add(interesPagado).add(desgravamenPagado).add(ahorroPagado);
        }

        BigDecimal pendiente() {
            return total.subtract(pagado());
        }

        /** Días de atraso: corren desde el vencimiento hasta que se paga lo que no es mora. */
        long diasAtraso(LocalDate hoy) {
            if (pagadaEn != null) {
                return 0;
            }
            return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(vencimiento, moraHasta != null ? moraHasta : hoy));
        }

        BigDecimal moraPendiente(LocalDate hoy, BigDecimal tasa) {
            return tasa.multiply(BigDecimal.valueOf(diasAtraso(hoy))).setScale(2, java.math.RoundingMode.HALF_UP)
                    .subtract(moraPagada).max(BigDecimal.ZERO);
        }

        /** Lo que falta para saldar la letra hoy: su parte sin pagar y la mora que lleva. */
        BigDecimal deuda(LocalDate hoy, BigDecimal tasaMora) {
            return pendiente().add(moraPendiente(hoy, tasaMora));
        }
    }

    @Entity(name = "DesembolsoCredito")
    @Table(name = "desembolsos_credito")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class DesembolsoCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private Long operacionId;
        private BigDecimal importe;
        private Instant creadoEn;
    }

    @Entity(name = "PagoCredito")
    @Table(name = "pagos_credito")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PagoCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private Long cuotaId;
        private Long operacionId;
        private BigDecimal importe;
        private Instant creadoEn;
    }

    @Entity(name = "DetallePago")
    @Table(name = "detalles_pago")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class DetallePago {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long pagoId;
        private String concepto;
        private BigDecimal importe;
    }

    @Entity(name = "EncajeCredito")
    @Table(name = "encajes_credito")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class EncajeCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private Long operacionId;
        private BigDecimal importe;
        private Instant pagadoEn;
        private Instant devueltoEn;
    }

    /** Lo cobrado por suministros al inicio de un crédito a letras: no se devuelve, es ganancia. */
    @Entity(name = "SuministroCredito")
    @Table(name = "suministros_credito")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class SuministroCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private Long operacionId;
        private BigDecimal importe;
        private Instant creadoEn;
    }

    @Entity(name = "AhorroLetrasCredito")
    @Table(name = "ahorro_letras_credito")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AhorroLetrasCredito {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private BigDecimal saldo;
        private Instant liberadoEn;
    }

    @Entity(name = "MovimientoAhorroLetras")
    @Table(name = "movimientos_ahorro_letras")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class MovimientoAhorroLetras {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long creditoId;
        private Long cuotaId;
        private String tipo;
        private BigDecimal importe;
        private Instant creadoEn;
    }
}
