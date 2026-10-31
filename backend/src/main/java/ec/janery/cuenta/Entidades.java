package ec.janery.cuenta;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

final class Entidades {

    private Entidades() {
    }

    @Entity(name = "OperacionFinanciera")
    @Table(name = "operaciones_financieras")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class OperacionFinanciera {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String tipo;
        private BigDecimal importe;
        private String metodoPago;
        private String referencia;
        private String claveIdempotencia;
        private Long actorId;
        private Instant creadoEn;
    }

    @Entity(name = "DepositoApertura")
    @Table(name = "depositos_apertura")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class DepositoApertura {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long cuentaId;
        private Long operacionId;
        private BigDecimal importeTotal;
        private BigDecimal importeIngreso;
        private BigDecimal importeReembolsable;
        private Instant devueltoEn;

        void marcarDevuelto(Instant ahora) {
            devueltoEn = ahora;
        }
    }

    @Entity(name = "MovimientoCuenta")
    @Table(name = "movimientos_cuenta")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class MovimientoCuenta {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long cuentaId;
        private Long operacionId;
        private String sentido;
        private BigDecimal importe;
        private String concepto;
        private BigDecimal saldoResultante;
        private Instant creadoEn;
    }
}
