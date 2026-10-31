package ec.janery.reportes;

/** Expone a las pruebas de otro paquete el generador de CSV, que es interno del módulo. */
public final class CsvDePrueba {

    private CsvDePrueba() {
    }

    public static byte[] generar(ReporteService.Tabla tabla) {
        return ReporteService.csv(tabla);
    }
}
