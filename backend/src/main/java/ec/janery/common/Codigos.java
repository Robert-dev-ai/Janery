package ec.janery.common;

/**
 * Códigos legibles para identificar registros al hablar de ellos: un prefijo por tipo y el número del registro. Se
 * derivan del id, así que no se guardan ni pueden repetirse. Los informes los arman igual en SQL.
 */
public final class Codigos {

    private Codigos() {
    }

    public static String credito(String familia, Long id) {
        String prefijo = switch (familia) {
            case "LETRAS" -> "CL";
            case "EMERGENTE_CLIENTE" -> "CE";
            case "EMERGENTE_SOCIO" -> "CS";
            case "EMERGENTE_ESTACIONAL" -> "CP";
            default -> "CR";
        };
        return de(prefijo, id);
    }

    public static String cupo(Long id) {
        return de("SC", id);
    }

    public static String ahorro(String tipo, Long id) {
        return de("PLAZO_FIJO".equals(tipo) ? "PF" : "AH", id);
    }

    public static String deposito(Long id) {
        return de("DP", id);
    }

    public static String retiro(Long id) {
        return de("RT", id);
    }

    public static String socio(Long id) {
        return de("SO", id);
    }

    private static String de(String prefijo, Long id) {
        return id == null ? null : "%s-%05d".formatted(prefijo, id);
    }
}
