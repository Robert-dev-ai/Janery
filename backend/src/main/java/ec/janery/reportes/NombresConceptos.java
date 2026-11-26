package ec.janery.reportes;

import java.util.Map;

/**
 * Nombre legible de cada concepto del libro, para los archivos que salen del backend. Es la misma lista que
 * {@code frontend/src/app/shared/utils/etiquetas.ts}: si se agrega un concepto allá, agregarlo también aquí.
 */
final class NombresConceptos {

    private static final Map<String, String> CONCEPTOS = Map.ofEntries(
            Map.entry("DEPOSITO", "Depósito"),
            Map.entry("RETIRO", "Retiro"),
            Map.entry("RETIRO_SOLICITADO", "Retiro solicitado"),
            Map.entry("RETIRO_DEVUELTO", "Retiro devuelto"),
            Map.entry("DESEMBOLSO_CREDITO", "Desembolso de crédito"),
            Map.entry("ENCAJE_CREDITO", "Encaje de crédito"),
            Map.entry("PAGO_CREDITO", "Pago de crédito"),
            Map.entry("PRECANCELACION_CREDITO", "Precancelación de crédito"),
            Map.entry("LIBERACION_AHORRO_LETRAS", "Ahorro letras devuelto"),
            Map.entry("DEVOLUCION_ENCAJE", "Encaje devuelto"),
            Map.entry("APERTURA_PLAZO_FIJO", "Apertura de plazo fijo"),
            Map.entry("SALDO_ANTERIOR", "Saldo anterior a Janery"),
            Map.entry("DEPOSITO_AHORRO", "Depósito a ahorro"),
            Map.entry("RETIRO_AHORRO", "Retiro de ahorro"),
            Map.entry("LIQUIDACION_PLAZO_FIJO", "Liquidación de plazo fijo"),
            Map.entry("INTERES_AHORRO", "Interés de ahorro"),
            Map.entry("INTERES_PRINCIPAL", "Interés de la cuenta principal"),
            Map.entry("LIQUIDACION_CIERRE", "Liquidación del interés al cerrar la cuenta"),
            Map.entry("LIQUIDACION_INTERES", "Liquidación del interés al cerrar una cuenta"),
            Map.entry("INGRESO_APERTURA", "Ingreso de apertura"),
            Map.entry("DEPOSITO_REEMBOLSABLE", "Depósito de apertura (del cliente)"),
            Map.entry("DEVOLUCION_DEPOSITO", "Devolución del depósito de apertura"),
            Map.entry("ENCAJE_EFECTIVO", "Encaje en efectivo"),
            Map.entry("APORTE_OBLIGATORIO", "Aporte mensual"),
            Map.entry("APORTE_PERSONAL", "Aporte personal"),
            Map.entry("APORTE_CAJA_CHICA", "Aporte a caja chica"),
            Map.entry("RETIRO_GANANCIA", "Retiro de ganancias"),
            Map.entry("DESCUENTO_DE_GANANCIAS", "Descuento de ganancias"),
            Map.entry("COMPRA_DE_CAPITAL", "Compra de capital"),
            Map.entry("VENTA_DE_CAPITAL", "Venta de capital"),
            Map.entry("APERTURA", "Apertura de cuenta"),
            Map.entry("ASIENTO_MANUAL", "Registro manual"),
            Map.entry("DEPOSITO_CUENTA", "Depósito a la cuenta"),
            Map.entry("RETIRO_CUENTA", "Retiro entregado"),
            Map.entry("RETIRO_APARTADO", "Retiro pedido (apartado)"),
            Map.entry("RETIRO_RESUELTO", "Retiro resuelto"),
            Map.entry("DESEMBOLSO", "Desembolso de crédito"),
            Map.entry("ENCAJE", "Encaje de crédito"),
            Map.entry("PAGO_CAPITAL", "Pago de letra: capital"),
            Map.entry("PAGO_INTERES", "Pago de letra: interés"),
            Map.entry("PAGO_FONDO_DESGRAVAMEN", "Pago de letra: desgravamen"),
            Map.entry("PAGO_AHORRO_LETRAS", "Pago de letra: ahorro letras"),
            Map.entry("PAGO_MORA", "Pago de mora"),
            Map.entry("RESERVA", "Fondo de reserva"),
            Map.entry("RESERVA_DE_GANANCIA", "Reserva de la ganancia"),
            Map.entry("TRASPASO_A_RESERVA", "Traspaso al fondo de reserva"),
            Map.entry("TRASPASO_A_CAJA_CHICA", "Traspaso a caja chica"),
            Map.entry("INTERES", "Interés de ahorro"),
            Map.entry("LIQUIDACION", "Liquidación de plazo fijo"),
            Map.entry("RETIRO_INTERES", "Retiro de interés de ahorro"),
            Map.entry("APORTE_INICIAL", "Aporte inicial"),
            Map.entry("APORTE_COMPRA", "Compra de capital"),
            Map.entry("APORTE_VENTA", "Venta de capital"),
            Map.entry("DESCUENTO_OBLIGACION", "Descuento por obligaciones"),
            Map.entry("APORTE_SOCIO", "Aporte a caja chica"),
            Map.entry("GASTO", "Gasto de caja chica"));

    private NombresConceptos() {
    }

    /** Un concepto sin nombre en la lista se lee a partir de su código: «PAGO_EXTRA» → «Pago extra». */
    static String de(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "";
        }
        if (codigo.startsWith("MANUAL: ")) {
            return "Registro manual: " + codigo.substring(8);
        }
        String nombre = CONCEPTOS.get(codigo);
        if (nombre != null) {
            return nombre;
        }
        String t = codigo.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
