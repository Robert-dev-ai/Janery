package ec.janery.cuenta;

/**
 * Lo implementa el módulo de pagos: al confirmarse un pago, reparte el dinero en la cuenta de cada beneficiario y paga
 * cada destino. Es una interfaz para que las cuentas no dependan de créditos, ahorros ni socios.
 */
public interface EjecutorPagos {

    /** Devuelve la primera operación de depósito, la que queda anotada en el aviso. */
    Long ejecutar(Long actorId, Long solicitudId, String medio, String referencia);
}
