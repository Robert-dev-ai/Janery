package ec.janery.socio;

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

final class Entidades {

    private Entidades() {
    }

    @Entity(name = "AporteSocio")
    @Table(name = "aportes_socios")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AporteSocio {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long socioId;
        private String tipo;
        private BigDecimal importe;
        private String periodo;
        private Long operacionId;
        private Instant creadoEn;
    }

    @Entity(name = "AporteCajaChica")
    @Table(name = "aportes_caja_chica")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AporteCajaChica {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long socioId;
        private BigDecimal importe;
        private String periodo;
        private Long operacionId;
        private Instant creadoEn;
    }

    @Entity(name = "Resultado")
    @Table(name = "resultados")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Resultado {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String tipo;
        private BigDecimal importe;
        private String concepto;
        private LocalDate fecha;
        private BigDecimal reserva;
        private BigDecimal porcentajeReserva;
        private Long actorId;
        private Instant creadoEn;
    }

    @Entity(name = "CierreAnual")
    @Table(name = "cierres_anuales")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class CierreAnual {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Integer anio;
        private BigDecimal resultadoNeto;
        private BigDecimal porcentajeReserva;
        private BigDecimal reserva;
        private BigDecimal distribuido;
        private Long actorId;
        private Instant creadoEn;
    }

    @Entity(name = "AutorizacionRetiro")
    @Table(name = "autorizaciones_retiro")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AutorizacionRetiro {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long socioId;
        private Integer anio;
        private Long autorizadoPor;
        private Instant creadoEn;
    }

    @Entity(name = "CompraventaCapital")
    @Table(name = "compraventas_capital")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class CompraventaCapital {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long vendedorId;
        private Long compradorId;
        private BigDecimal capital;
        private BigDecimal precio;
        private Long operacionId;
        private Long actorId;
        private Instant creadoEn;
    }

    @Entity(name = "VariacionParticipacion")
    @Table(name = "variaciones_participacion")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class VariacionParticipacion {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long socioId;
        private String tipo;
        private BigDecimal importe;
        private LocalDate fecha;
        private Long aporteId;
        private Long resultadoId;
        private Long cierreId;
        private Instant creadoEn;
    }
}
