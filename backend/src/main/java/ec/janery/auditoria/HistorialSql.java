package ec.janery.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lectura legible de la auditoría: quién lo hizo (primer nombre y apellido), con qué cargos, qué hizo en palabras, el
 * código del registro y a qué persona afectó. Lo usan el historial de operaciones y la pantalla de auditoría.
 */
public final class HistorialSql {

    private HistorialSql() {
    }

    public static final Map<String, String> ACCIONES = new LinkedHashMap<>();

    static {
        ACCIONES.put("REGISTRO", "Se registró en Janery");
        ACCIONES.put("PERFIL_COMPLETADO", "Completó sus datos personales");
        ACCIONES.put("PERFIL_ACTUALIZADO", "Actualizó su teléfono o dirección");
        ACCIONES.put("DATOS_CREDITO_ACTUALIZADOS", "Actualizó sus datos para crédito");
        ACCIONES.put("FOTO_ACTUALIZADA", "Cambió su foto de perfil");
        ACCIONES.put("FOTO_QUITADA", "Quitó su foto de perfil");
        ACCIONES.put("LOGIN", "Inició sesión");
        ACCIONES.put("LOGOUT", "Cerró sesión");
        ACCIONES.put("LOGIN_FALLIDO", "Intentó entrar con una contraseña incorrecta");
        ACCIONES.put("ACCESO_BLOQUEADO", "Su acceso se bloqueó por 3 contraseñas incorrectas");
        ACCIONES.put("RESTABLECIMIENTO_SOLICITADO", "Pidió restablecer su contraseña");
        ACCIONES.put("CONTRASENA_RESTABLECIDA", "Restableció la contraseña de");
        ACCIONES.put("CONTRASENA_CAMBIADA", "Cambió su contraseña");
        ACCIONES.put("CONTRASENA_FALLIDA", "Falló al cambiar su contraseña");
        ACCIONES.put("SESIONES_CERRADAS", "Cerró sus otras sesiones");
        ACCIONES.put("CORREO_CAMBIADO", "Corrigió el correo de");
        ACCIONES.put("NOMBRE_CAMBIADO", "Corrigió el nombre de");
        ACCIONES.put("PRESIDENTE_INICIAL", "Se creó el usuario de la presidencia");
        ACCIONES.put("CARGO_ASIGNADO", "Asignó un cargo a");
        ACCIONES.put("CARGO_REVOCADO", "Quitó un cargo a");
        ACCIONES.put("PERMISO_CONCEDIDO", "Concedió un permiso a un cargo");
        ACCIONES.put("PERMISO_RETIRADO", "Retiró un permiso a un cargo");
        ACCIONES.put("SOCIO_REGISTRADO", "Registró como socio a");
        ACCIONES.put("APERTURA_SOLICITADA", "Pidió abrir su cuenta principal");
        ACCIONES.put("APERTURA_CONFIRMADA", "Confirmó la apertura de la cuenta de");
        ACCIONES.put("APERTURA_RECHAZADA", "Rechazó la apertura de la cuenta de");
        ACCIONES.put("CIERRE_SOLICITADO", "Pidió cerrar su cuenta");
        ACCIONES.put("CIERRE_CONFIRMADO", "Confirmó el cierre de la cuenta de");
        ACCIONES.put("COMPROBANTE_SUBIDO", "Subió un comprobante de pago");
        ACCIONES.put("DEPOSITO", "Registró un depósito en la cuenta de");
        ACCIONES.put("RETIRO", "Registró un retiro de la cuenta de");
        ACCIONES.put("DEPOSITO_SOLICITADO", "Avisó un depósito");
        ACCIONES.put("DEPOSITO_CONFIRMADO", "Confirmó el depósito de");
        ACCIONES.put("DEPOSITO_RECHAZADO", "Rechazó el depósito de");
        ACCIONES.put("DEPOSITO_CANCELADO", "Canceló su aviso de depósito");
        ACCIONES.put("RETIRO_SOLICITADO", "Pidió retirar dinero");
        ACCIONES.put("RETIRO_ENTREGADO", "Entregó el retiro de");
        ACCIONES.put("RETIRO_RECHAZADO", "Rechazó el retiro de");
        ACCIONES.put("RETIRO_CANCELADO", "Canceló su pedido de retiro");
        ACCIONES.put("AHORRO_CREADO", "Abrió un ahorro");
        ACCIONES.put("AHORRO_RENOMBRADO", "Cambió el nombre de su ahorro");
        ACCIONES.put("AHORRO_ELIMINADO", "Eliminó su ahorro");
        ACCIONES.put("AHORRO_DEPOSITO", "Pasó dinero a su ahorro");
        ACCIONES.put("AHORRO_RETIRO", "Sacó dinero de su ahorro");
        ACCIONES.put("AHORRO_INTERES", "Cobró el interés de su ahorro");
        ACCIONES.put("CREDITO_SOLICITADO", "Pidió un crédito");
        ACCIONES.put("DOCUMENTOS_CREDITO", "Subió documentos de su crédito");
        ACCIONES.put("CREDITO_APROBADO", "Aprobó el crédito de");
        ACCIONES.put("CREDITO_RECHAZADO", "Rechazó el crédito de");
        ACCIONES.put("CREDITO_DESEMBOLSADO", "Desembolsó el crédito de");
        ACCIONES.put("CREDITO_PAGO", "Pagó su crédito");
        ACCIONES.put("CREDITO_PRECANCELADO", "Pagó todo su crédito por adelantado");
        ACCIONES.put("CREDITO_FINALIZADO", "Terminó de pagar su crédito");
        ACCIONES.put("CUPO_SOLICITADO", "Pidió dinero de su cupo emergente");
        ACCIONES.put("CUPO_APROBADO", "Aprobó el retiro de cupo de");
        ACCIONES.put("CUPO_RECHAZADO", "Rechazó el retiro de cupo de");
        ACCIONES.put("APORTE_INICIAL", "Registró el aporte inicial de");
        ACCIONES.put("APORTE_OBLIGATORIO", "Pagó su aporte mensual");
        ACCIONES.put("APORTE_PERSONAL", "Hizo un aporte personal");
        ACCIONES.put("APORTE_CAJA_CHICA", "Pagó su aporte a caja chica");
        ACCIONES.put("APORTE_CAJA_CHICA_RECIBIDO", "Recibió en persona el aporte a caja chica de");
        ACCIONES.put("GASTO_CAJA_CHICA", "Registró un gasto de caja chica");
        ACCIONES.put("DESCUENTO_APORTE_MENSUAL", "Descontó de las ganancias el aporte mensual de");
        ACCIONES.put("DESCUENTO_CAJA_CHICA", "Descontó de las ganancias la caja chica de");
        ACCIONES.put("DESCUENTO_CUOTA", "Descontó de las ganancias una cuota de");
        ACCIONES.put("RETIRO_AUTORIZADO", "Autorizó el retiro de ganancias de");
        ACCIONES.put("RETIRO_REVOCADO", "Quitó la autorización de retiro de ganancias de");
        ACCIONES.put("RETIRO_GANANCIA", "Retiró sus ganancias");
        ACCIONES.put("COMPRAVENTA_CAPITAL", "Registró una compraventa de capital");
        ACCIONES.put("RESULTADO_GANANCIA", "Registró una ganancia");
        ACCIONES.put("RESULTADO_PERDIDA", "Registró una pérdida");
        ACCIONES.put("CIERRE_ANUAL", "Cerró el año contable");
        ACCIONES.put("ASIENTO_MANUAL", "Hizo un registro contable manual");
        ACCIONES.put("ASIENTO_REVERTIDO", "Revirtió un registro contable manual");
        ACCIONES.put("PARAMETRO_CAMBIADO", "Cambió un parámetro del sistema");
        ACCIONES.put("INFORME_EXPORTADO", "Descargó un informe");
        ACCIONES.put("RELOJ_AVANZADO", "Avanzó el reloj de pruebas");
        ACCIONES.put("CREDITO_TRAMITADO", "Tramitó en ventanilla el crédito de");
        ACCIONES.put("DATOS_CREDITO_TRAMITADOS", "Registró en ventanilla los datos para crédito de");
        ACCIONES.put("ANUNCIO_PUBLICADO", "Publicó el aviso de la cabecera");
        ACCIONES.put("ANUNCIO_QUITADO", "Quitó el aviso de la cabecera");
        ACCIONES.put("SIMULACION_INICIADA", "Inició una simulación con una copia de los datos");
        ACCIONES.put("SIMULACION_FINALIZADA", "Finalizó la simulación y descartó lo probado");
        ACCIONES.put("PERSONA_PRUEBA_CREADA", "Creó una persona de prueba en el simulador");
    }

    private static final String PREFIJO_CREDITO = """
            CASE familia WHEN 'LETRAS' THEN 'CL-' WHEN 'EMERGENTE_CLIENTE' THEN 'CE-' WHEN 'EMERGENTE_SOCIO' THEN 'CS-'
                         WHEN 'EMERGENTE_ESTACIONAL' THEN 'CP-' ELSE 'CR-' END""";

    /**
     * Primer nombre y primer apellido de la persona "p", con sus partículas: «Robert De La Cruz», no «ROBERT DE». Sin
     * grupos de captura: así substring devuelve todo lo encontrado.
     */
    public static String nombreCorto(String p) {
        return ("initcap(split_part(%1$s.nombres, ' ', 1) || ' ' "
                + "|| substring(%1$s.apellidos from '(?i)^(?:(?:de|del|la|las|los|san|santa|y)\\s+)*\\S+'))").formatted(p);
    }

    /** Columnas legibles de un evento "e"; los alias son los títulos de la tabla. */
    public static final String COLUMNAS = """
            to_char(e.creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM-DD HH24:MI') AS "Fecha",
            COALESCE(%s, u.correo, 'Sistema') AS "Quién",
            (SELECT string_agg(c.nombre, ', ' ORDER BY c.nombre) FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
             WHERE uc.usuario_id = u.id AND uc.hasta IS NULL) AS "Cargos",
            COALESCE(acc.texto, initcap(replace(lower(e.accion), '_', ' '))) AS "Qué hizo",
            COALESCE(%s, '') AS "A quién",
            ref.codigo AS "Código",
            concat_ws(' → ', NULLIF(e.antes, ''), NULLIF(e.despues, '')) AS "Detalle",
            e.motivo AS "Motivo"
            """.formatted(nombreCorto("pa"), nombreCorto("pp"));

    /** FROM con todo lo que necesitan las columnas. */
    public static String desde() {
        String acciones = ACCIONES.entrySet().stream()
                .map(a -> "('%s', '%s')".formatted(a.getKey(), a.getValue().replace("'", "''")))
                .collect(Collectors.joining(", "));
        return """
                FROM auditoria_eventos e
                LEFT JOIN usuarios u ON u.id = e.actor_id
                LEFT JOIN personas pa ON pa.usuario_id = u.id
                LEFT JOIN (VALUES %s) AS acc (codigo, texto) ON acc.codigo = e.accion
                LEFT JOIN LATERAL (SELECT CASE WHEN e.entidad_id ~ '^[0-9]+$' THEN e.entidad_id::bigint END AS id) x ON true
                LEFT JOIN LATERAL (
                    SELECT CASE e.entidad
                        WHEN 'usuario' THEN (SELECT id FROM personas WHERE usuario_id = x.id)
                        WHEN 'persona' THEN x.id
                        WHEN 'cuenta' THEN (SELECT persona_id FROM cuentas_principales WHERE id = x.id)
                        WHEN 'solicitud_deposito' THEN (SELECT persona_id FROM solicitudes_deposito WHERE id = x.id)
                        WHEN 'solicitud_retiro' THEN (SELECT persona_id FROM solicitudes_retiro WHERE id = x.id)
                        WHEN 'ahorro' THEN (SELECT persona_id FROM cuentas_ahorro WHERE id = x.id)
                        WHEN 'credito' THEN (SELECT persona_id FROM creditos WHERE id = x.id)
                        WHEN 'solicitud_cupo' THEN (SELECT persona_id FROM solicitudes_cupo WHERE id = x.id)
                        WHEN 'socio' THEN (SELECT persona_id FROM socios WHERE id = x.id)
                    END AS persona_id,
                    CASE e.entidad
                        WHEN 'cuenta' THEN (SELECT numero FROM cuentas_principales WHERE id = x.id)
                        WHEN 'solicitud_deposito' THEN 'DP-' || lpad(x.id::text, 5, '0')
                        WHEN 'solicitud_retiro' THEN 'RT-' || lpad(x.id::text, 5, '0')
                        WHEN 'ahorro' THEN (SELECT CASE WHEN tipo = 'PLAZO_FIJO' THEN 'PF-' ELSE 'AH-' END || lpad(id::text, 5, '0')
                                            FROM cuentas_ahorro WHERE id = x.id)
                        WHEN 'credito' THEN (SELECT %s || lpad(id::text, 5, '0') FROM creditos WHERE id = x.id)
                        WHEN 'solicitud_cupo' THEN 'SC-' || lpad(x.id::text, 5, '0')
                        WHEN 'socio' THEN 'SO-' || lpad(x.id::text, 5, '0')
                        WHEN 'usuario' THEN NULL
                        WHEN 'persona' THEN NULL
                        ELSE COALESCE(CASE WHEN x.id IS NULL THEN e.entidad_id END, CASE WHEN e.entidad ~ '^[A-Z_]+$' THEN e.entidad END)
                    END AS codigo
                ) ref ON true
                LEFT JOIN personas pp ON pp.id = ref.persona_id
                """.formatted(acciones, PREFIJO_CREDITO);
    }
}
