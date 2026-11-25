package ec.janery.cuenta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import ec.janery.common.InteresDiario;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cuentas_principales")
@Getter
@NoArgsConstructor
public class CuentaPrincipal {

    public static final String PENDIENTE_PAGO = "PENDIENTE_PAGO";
    public static final String ACTIVA = "ACTIVA";
    public static final String PENDIENTE_CIERRE = "PENDIENTE_CIERRE";
    public static final String CERRADA = "CERRADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long personaId;
    private String numero;
    private String estado;
    private BigDecimal saldoDisponible;
    private String metodoPago;
    private String referenciaPago;
    private Instant abiertaEn;
    private Instant cerradaEn;
    private Instant creadoEn;
    /** Interés corrido, como en el ahorro normal: lo acumulado pasa a disponible cada día 1. */
    private BigDecimal interesAcumulado;
    private BigDecimal interesDisponible;
    private LocalDate ultimoDevengo;

    CuentaPrincipal(Long personaId, String numero, String metodoPago, String referenciaPago, Instant creadoEn) {
        this.personaId = personaId;
        this.numero = numero;
        this.estado = PENDIENTE_PAGO;
        this.saldoDisponible = BigDecimal.ZERO.setScale(2);
        this.metodoPago = metodoPago;
        this.referenciaPago = referenciaPago;
        this.creadoEn = creadoEn;
        this.interesAcumulado = InteresDiario.CERO;
        this.interesDisponible = InteresDiario.CERO;
    }

    /** Gana interés desde el día en que se activa. */
    void activar(Instant ahora, LocalDate hoy) {
        estado = ACTIVA;
        abiertaEn = ahora;
        ultimoDevengo = hoy;
    }

    void devengar(InteresDiario.Interes interes, LocalDate hoy) {
        interesAcumulado = interes.acumulado();
        interesDisponible = interes.disponible();
        // Sin fecha (una cuenta anterior a esta función que no estaba activa), empieza a ganar desde hoy.
        ultimoDevengo = hoy;
    }

    /** Lo que pasa al saldo sale primero de lo disponible; al cerrar, también de lo acumulado. */
    void descontarInteres(BigDecimal deDisponible, BigDecimal deAcumulado) {
        interesDisponible = interesDisponible.subtract(deDisponible);
        interesAcumulado = interesAcumulado.subtract(deAcumulado);
    }

    void rechazar() {
        estado = RECHAZADA;
    }

    void solicitarCierre() {
        estado = PENDIENTE_CIERRE;
    }

    void cerrar(Instant ahora) {
        estado = CERRADA;
        cerradaEn = ahora;
    }

    void aplicar(BigDecimal nuevoSaldo) {
        saldoDisponible = nuevoSaldo;
    }
}
