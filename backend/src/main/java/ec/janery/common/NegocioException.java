package ec.janery.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** Regla de negocio incumplida. El mensaje se muestra al usuario: no debe contener datos sensibles. */
@Getter
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public NegocioException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public static NegocioException noEncontrado(String mensaje) {
        return new NegocioException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
    }

    public static NegocioException conflicto(String codigo, String mensaje) {
        return new NegocioException(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
