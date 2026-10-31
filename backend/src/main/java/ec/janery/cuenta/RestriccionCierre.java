package ec.janery.cuenta;

/** Cada producto indica aquí si impide cerrar la cuenta principal. Lanza NegocioException si lo impide. */
public interface RestriccionCierre {

    void verificar(Long personaId);
}
