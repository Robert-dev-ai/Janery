package ec.janery.correo;

/** Envío de correos. Sin configurar, Janery funciona igual: lo que iba por correo se atiende en persona. */
public interface EnvioCorreo {

    /** Falso si falta la configuración: quien llama sigue por el camino sin correo. */
    boolean disponible();

    /** Lanza si el proveedor rechaza el envío o no responde. */
    void enviar(String para, String asunto, String texto, String html);
}
