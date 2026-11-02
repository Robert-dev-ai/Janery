package ec.janery.common;

/**
 * De quién es un movimiento en un libro o informe general: la persona, o un grupo si no es de alguien en especial. En el
 * historial propio no se usa: allí todo es de quien lo consulta.
 */
public final class Quien {

    private Quien() {
    }

    public static final String SOCIOS = "Socios";
    public static final String JANERY = "Janery";

    /** persona: el nombre que dio persona_de_operacion, o null. */
    public static String de(String persona, String concepto, String linea) {
        if (persona != null && !persona.isBlank()) {
            return persona;
        }
        String c = concepto == null ? "" : concepto;
        if (c.contains("RESERVA") || c.contains("GANANCIA") || c.contains("PERDIDA") || c.startsWith("APORTE")
                || (linea != null && linea.startsWith("3."))) {
            return SOCIOS;
        }
        if (c.equals("LIBERACION_AHORRO_LETRAS")) {
            return "Clientes y socios con crédito a letras";
        }
        return JANERY;
    }
}
