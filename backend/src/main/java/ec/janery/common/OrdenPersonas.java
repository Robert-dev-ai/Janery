package ec.janery.common;

/**
 * Cómo se muestran y ordenan las personas en todos los listados: «Apellidos Nombres», primero los socios activos y luego
 * las demás personas, cada grupo en orden alfabético.
 *
 * <p>Las expresiones suponen que la tabla {@code personas} tiene el alias {@code p}. El orden no distingue mayúsculas ni
 * tildes y no depende de la configuración regional de la base.
 */
public final class OrdenPersonas {

    /** Para buscar: acepta el nombre escrito en cualquiera de los dos órdenes. */
    public static final String NOMBRE_BUSCABLE = "lower(p.apellidos || ' ' || p.nombres || ' ' || p.nombres || ' ' || p.apellidos)";

    /**
     * Solo alfabético, por apellidos y nombres, para listados que ya son solo de socios. Minúsculas y sin tildes: «Álvarez»
     * va junto a «Alvarado», no después de la zeta.
     */
    public static final String ALFABETICO = "translate(lower(p.apellidos), 'áéíóúüñ', 'aeiouun'), "
            + "translate(lower(p.nombres), 'áéíóúüñ', 'aeiouun'), p.id";

    /** Socios primero; dentro de cada grupo, alfabético. */
    public static final String ORDEN = "NOT EXISTS (SELECT 1 FROM socios so WHERE so.persona_id = p.id AND so.estado = 'ACTIVO'), "
            + ALFABETICO;

    private OrdenPersonas() {
    }

    /** El nombre para mostrar, armado en Java. */
    public static String nombre(String nombres, String apellidos) {
        return apellidos + " " + nombres;
    }
}
