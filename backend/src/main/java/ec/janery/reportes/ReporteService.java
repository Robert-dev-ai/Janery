package ec.janery.reportes;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import lombok.RequiredArgsConstructor;

/** Informes de solo lectura, calculados del libro. Consultas fijas: el usuario solo elige el informe y las fechas. */
@Service
@RequiredArgsConstructor
public class ReporteService {

    public record Definicion(String codigo, String titulo, String descripcion, boolean usaFechas) {
    }

    public record Tabla(List<String> columnas, List<Boolean> numericas, List<List<String>> filas, boolean truncado) {
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    private record Reporte(Definicion definicion, String sql) {
    }

    private static final int LIMITE_PANTALLA = 1000;
    private static final int LIMITE_ARCHIVO = 100_000;
    private static final Set<Integer> TIPOS_NUMERICOS = Set.of(Types.NUMERIC, Types.DECIMAL, Types.BIGINT, Types.INTEGER);

    private static final String ESTADO_CUENTA_SQL = """
            SELECT to_char(m.creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM-DD HH24:MI') AS "Fecha",
                   c.numero AS "Cuenta", p.nombres || ' ' || p.apellidos AS "Titular", m.concepto AS "Concepto",
                   m.sentido AS "Sentido", m.importe AS "Importe", m.saldo_resultante AS "Saldo"
            FROM movimientos_cuenta m
            JOIN cuentas_principales c ON c.id = m.cuenta_id
            JOIN personas p ON p.id = c.persona_id
            WHERE CAST(m.creado_en AT TIME ZONE 'America/Guayaquil' AS date) BETWEEN :desde AND :hasta
            """;

    private static String caja(String codigo) {
        return """
                SELECT to_char(m.creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM-DD HH24:MI') AS "Fecha",
                       m.concepto AS "Concepto", m.detalle AS "Detalle", m.sentido AS "Sentido", m.importe AS "Importe",
                       SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END) OVER (ORDER BY m.id) AS "Saldo"
                FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id
                WHERE c.codigo = '%s'
                  AND CAST(m.creado_en AT TIME ZONE 'America/Guayaquil' AS date) BETWEEN :desde AND :hasta
                ORDER BY m.id
                """.formatted(codigo);
    }

    private static final List<Reporte> REPORTES = List.of(
            new Reporte(new Definicion("ESTADO_CUENTAS", "Estado de cuentas", "Movimientos de todas las cuentas principales", true),
                    ESTADO_CUENTA_SQL + " ORDER BY m.id"),
            new Reporte(new Definicion("CONCILIACION", "Conciliación de saldos", "Saldo guardado de cada cuenta frente a la suma de su libro. La diferencia debe ser cero", false), """
                    SELECT c.numero AS "Cuenta", p.nombres || ' ' || p.apellidos AS "Titular", c.estado AS "Estado",
                           c.saldo_disponible AS "Saldo guardado",
                           COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0) AS "Saldo según libro",
                           c.saldo_disponible - COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0) AS "Diferencia"
                    FROM cuentas_principales c
                    JOIN personas p ON p.id = c.persona_id
                    LEFT JOIN movimientos_cuenta m ON m.cuenta_id = c.id
                    GROUP BY c.id, c.numero, p.nombres, p.apellidos, c.estado, c.saldo_disponible
                    ORDER BY c.numero
                    """),
            new Reporte(new Definicion("CREDITOS", "Créditos", "Todos los créditos y su saldo", false), """
                    SELECT p.nombres || ' ' || p.apellidos AS "Titular", c.familia AS "Familia", c.modalidad AS "Modalidad",
                           c.perfil AS "Perfil", c.estado AS "Estado", c.importe AS "Importe", c.plazo_meses AS "Plazo (meses)",
                           c.tasa_interes AS "Tasa interés", c.tasa_desgravamen AS "Tasa desgravamen", c.unidad_tasa AS "Unidad",
                           c.capital_pendiente AS "Capital pendiente", c.fecha_inicio AS "Inicio"
                    FROM creditos c JOIN personas p ON p.id = c.persona_id
                    ORDER BY c.id
                    """),
            new Reporte(new Definicion("CUOTAS", "Cuotas de crédito", "Letras y su desglose, por fecha de vencimiento", true), """
                    SELECT p.nombres || ' ' || p.apellidos AS "Titular", c.familia AS "Familia", q.numero AS "N.º",
                           q.vencimiento AS "Vencimiento", q.capital AS "Capital", q.interes AS "Interés",
                           q.fondo_desgravamen AS "Desgravamen", q.ahorro_letras AS "Ahorro letras", q.total AS "Total",
                           q.capital_pagado + q.interes_pagado + q.desgravamen_pagado + q.ahorro_pagado AS "Pagado",
                           CASE WHEN q.pagada_en IS NOT NULL THEN 'PAGADA'
                                WHEN c.precancelado_en IS NOT NULL THEN 'PRECANCELADA'
                                WHEN q.capital_pagado > 0 THEN 'PARCIAL' ELSE 'PENDIENTE' END AS "Estado"
                    FROM cuotas_credito q
                    JOIN creditos c ON c.id = q.credito_id
                    JOIN personas p ON p.id = c.persona_id
                    WHERE q.vencimiento BETWEEN :desde AND :hasta
                    ORDER BY q.vencimiento, q.id
                    """),
            new Reporte(new Definicion("INTERESES_DESGRAVAMEN", "Intereses y desgravamen cobrados", "Total mensual por concepto, según los pagos recibidos", true), """
                    SELECT to_char(g.creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM') AS "Mes", d.concepto AS "Concepto",
                           SUM(d.importe) AS "Importe"
                    FROM detalles_pago d JOIN pagos_credito g ON g.id = d.pago_id
                    WHERE d.concepto IN ('INTERES', 'FONDO_DESGRAVAMEN')
                      AND CAST(g.creado_en AT TIME ZONE 'America/Guayaquil' AS date) BETWEEN :desde AND :hasta
                    GROUP BY 1, 2 ORDER BY 1, 2
                    """),
            new Reporte(new Definicion("AHORROS", "Ahorros", "Ahorros normales y a plazo fijo", false), """
                    SELECT p.nombres || ' ' || p.apellidos AS "Titular", a.tipo AS "Tipo", a.nombre AS "Nombre", a.estado AS "Estado",
                           a.saldo AS "Saldo", a.tasa_anual AS "Tasa anual", a.plazo_meses AS "Plazo (meses)", a.vence_en AS "Vence"
                    FROM cuentas_ahorro a JOIN personas p ON p.id = a.persona_id
                    ORDER BY a.id
                    """),
            new Reporte(new Definicion("ENCAJES_LETRAS", "Encajes y ahorro letras", "Por cada crédito a letras aprobado", false), """
                    SELECT p.nombres || ' ' || p.apellidos AS "Titular", c.modalidad AS "Modalidad", c.estado AS "Estado crédito",
                           e.importe AS "Encaje", CASE WHEN e.devuelto_en IS NULL THEN 'RETENIDO' ELSE 'DEVUELTO' END AS "Estado encaje",
                           a.saldo AS "Ahorro letras"
                    FROM encajes_credito e
                    JOIN creditos c ON c.id = e.credito_id
                    JOIN personas p ON p.id = c.persona_id
                    LEFT JOIN ahorro_letras_credito a ON a.credito_id = c.id
                    ORDER BY c.id
                    """),
            new Reporte(new Definicion("CAJA_PRINCIPAL", "Caja principal", "Movimientos y saldo acumulado", true), caja("PRINCIPAL")),
            new Reporte(new Definicion("CAJA_CHICA", "Caja chica", "Movimientos y saldo acumulado", true), caja("CHICA")),
            new Reporte(new Definicion("FONDO_RESERVA", "Fondo de reserva", "Movimientos y saldo acumulado", true), caja("FONDO_RESERVA")),
            new Reporte(new Definicion("APORTES_SOCIOS", "Aportes por socio", "Obligatorios, personales y de caja chica, por separado", true), """
                    SELECT nombre AS "Socio", tipo AS "Tipo", periodo AS "Periodo", importe AS "Importe",
                           to_char(creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM-DD') AS "Fecha"
                    FROM (
                        SELECT p.nombres || ' ' || p.apellidos AS nombre, a.tipo, a.periodo, a.importe, a.creado_en
                        FROM aportes_socios a JOIN socios s ON s.id = a.socio_id JOIN personas p ON p.id = s.persona_id
                        UNION ALL
                        SELECT p.nombres || ' ' || p.apellidos, 'CAJA_CHICA', a.periodo, a.importe, a.creado_en
                        FROM aportes_caja_chica a JOIN socios s ON s.id = a.socio_id JOIN personas p ON p.id = s.persona_id
                    ) t
                    WHERE CAST(creado_en AT TIME ZONE 'America/Guayaquil' AS date) BETWEEN :desde AND :hasta
                    ORDER BY creado_en
                    """),
            new Reporte(new Definicion("PARTICIPACION_MENSUAL", "Evolución de la participación", "Por socio y mes con movimientos: valor inicial, variación y valor final", false), """
                    SELECT p.nombres || ' ' || p.apellidos AS "Socio", t.mes AS "Mes",
                           COALESCE(SUM(t.variacion) OVER w - t.variacion, 0) AS "Valor inicial",
                           t.variacion AS "Variación", SUM(t.variacion) OVER w AS "Valor final"
                    FROM (
                        SELECT socio_id, to_char(fecha, 'YYYY-MM') AS mes, SUM(importe) AS variacion
                        FROM variaciones_participacion GROUP BY 1, 2
                    ) t
                    JOIN socios s ON s.id = t.socio_id
                    JOIN personas p ON p.id = s.persona_id
                    WINDOW w AS (PARTITION BY t.socio_id ORDER BY t.mes)
                    ORDER BY "Socio", t.mes
                    """),
            new Reporte(new Definicion("FONDO_PERSONAL", "Fondo personal por origen", "Valor de cada socio, separado en aportes iniciales, mensuales, personales y ganancias", false), """
                    SELECT p.nombres || ' ' || p.apellidos AS "Socio",
                           COALESCE(SUM(v.importe) FILTER (WHERE v.tipo = 'APORTE_INICIAL'), 0) AS "Aportes iniciales",
                           COALESCE(SUM(v.importe) FILTER (WHERE v.tipo = 'APORTE_OBLIGATORIO'), 0) AS "Aportes mensuales",
                           COALESCE(SUM(v.importe) FILTER (WHERE v.tipo = 'APORTE_PERSONAL'), 0) AS "Aportes personales",
                           COALESCE(SUM(v.importe) FILTER (WHERE v.tipo IN ('APORTE_COMPRA', 'APORTE_VENTA')), 0) AS "Compraventas",
                           COALESCE(SUM(v.importe) FILTER (WHERE v.tipo NOT LIKE 'APORTE%'), 0) AS "Ganancias netas",
                           COALESCE(SUM(v.importe), 0) AS "Fondo personal"
                    FROM socios s
                    JOIN personas p ON p.id = s.persona_id
                    LEFT JOIN variaciones_participacion v ON v.socio_id = s.id
                    GROUP BY s.id, p.nombres, p.apellidos
                    ORDER BY "Socio"
                    """),
            new Reporte(new Definicion("RESERVA_DISTRIBUCION", "Cierres anuales", "Resultado, reserva separada y lo que llegó a los fondos personales en cada año cerrado", false), """
                    SELECT anio AS "Año", resultado_neto AS "Resultado neto", reserva AS "Reserva",
                           distribuido AS "A fondos personales"
                    FROM cierres_anuales ORDER BY anio
                    """),
            new Reporte(new Definicion("ACTIVIDAD", "Actividad", "Acciones registradas, con el usuario y sus cargos actuales", true), """
                    SELECT to_char(e.creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM-DD HH24:MI') AS "Fecha",
                           u.correo AS "Usuario",
                           (SELECT string_agg(c.codigo, ', ') FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                            WHERE uc.usuario_id = u.id AND uc.hasta IS NULL) AS "Cargos",
                           e.modulo AS "Módulo", e.accion AS "Acción", e.entidad AS "Entidad", e.entidad_id AS "Id",
                           e.antes AS "Antes", e.despues AS "Después", e.motivo AS "Motivo"
                    FROM auditoria_eventos e LEFT JOIN usuarios u ON u.id = e.actor_id
                    WHERE CAST(e.creado_en AT TIME ZONE 'America/Guayaquil' AS date) BETWEEN :desde AND :hasta
                    ORDER BY e.id
                    """));

    private static final Map<String, Reporte> POR_CODIGO =
            REPORTES.stream().collect(Collectors.toMap(r -> r.definicion().codigo(), r -> r));

    private final JdbcClient jdbc;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public List<Definicion> catalogo() {
        return REPORTES.stream().map(Reporte::definicion).toList();
    }

    @Transactional(readOnly = true)
    public Tabla consultar(String codigo, LocalDate desde, LocalDate hasta) {
        return ejecutar(reporte(codigo).sql(), inicio(desde), fin(hasta), null, LIMITE_PANTALLA);
    }

    @Transactional
    public Archivo exportar(Long actorId, String codigo, LocalDate desde, LocalDate hasta) {
        Tabla tabla = ejecutar(reporte(codigo).sql(), inicio(desde), fin(hasta), null, LIMITE_ARCHIVO);
        auditoria.registrar(actorId, "INFORME_EXPORTADO", "REPORTES", "informe", codigo, null,
                String.valueOf(tabla.filas().size()), null);
        return new Archivo(codigo.toLowerCase() + ".csv", csv(tabla));
    }

    /** Estado de cuenta de la propia persona: la consulta filtra por el usuario autenticado. */
    @Transactional(readOnly = true)
    public Archivo miEstadoDeCuenta(Long usuarioId, LocalDate desde, LocalDate hasta) {
        Tabla tabla = ejecutar(ESTADO_CUENTA_SQL + " AND p.usuario_id = :usuario ORDER BY m.id",
                inicio(desde), fin(hasta), usuarioId, LIMITE_ARCHIVO);
        return new Archivo("estado_de_cuenta.csv", csv(tabla));
    }

    private Tabla ejecutar(String sql, LocalDate desde, LocalDate hasta, Long usuario, int limite) {
        if (desde.isAfter(hasta)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "FECHAS_INVALIDAS", "La fecha inicial es posterior a la final");
        }
        var consulta = jdbc.sql(sql + " LIMIT " + (limite + 1)).param("desde", desde).param("hasta", hasta);
        if (usuario != null) {
            consulta = consulta.param("usuario", usuario);
        }
        return consulta.query((ResultSetExtractor<Tabla>) rs -> {
            var meta = rs.getMetaData();
            List<String> columnas = new ArrayList<>();
            List<Boolean> numericas = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columnas.add(meta.getColumnLabel(i));
                numericas.add(TIPOS_NUMERICOS.contains(meta.getColumnType(i)));
            }
            List<List<String>> filas = new ArrayList<>();
            boolean truncado = false;
            while (rs.next()) {
                if (filas.size() == limite) {
                    truncado = true;
                    break;
                }
                List<String> fila = new ArrayList<>();
                for (int i = 1; i <= columnas.size(); i++) {
                    fila.add(rs.getString(i));
                }
                filas.add(fila);
            }
            return new Tabla(columnas, numericas, filas, truncado);
        });
    }

    /** Punto y coma, coma decimal y BOM: es lo que abre bien Excel en configuración regional de Ecuador. */
    static byte[] csv(Tabla tabla) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(tabla.columnas().stream().map(ReporteService::celda).collect(Collectors.joining(";"))).append("\r\n");
        for (List<String> fila : tabla.filas()) {
            List<String> celdas = new ArrayList<>();
            for (int i = 0; i < fila.size(); i++) {
                String valor = fila.get(i);
                celdas.add(valor != null && tabla.numericas().get(i) ? valor.replace('.', ',') : celda(valor));
            }
            sb.append(String.join(";", celdas)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        // Un texto que empieza así se ejecutaría como fórmula al abrirlo en Excel.
        String seguro = !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        return "\"" + seguro.replace("\"", "\"\"") + "\"";
    }

    private Reporte reporte(String codigo) {
        Reporte reporte = POR_CODIGO.get(codigo);
        if (reporte == null) {
            throw NegocioException.noEncontrado("Informe no encontrado");
        }
        return reporte;
    }

    private static LocalDate inicio(LocalDate desde) {
        return desde == null ? LocalDate.of(2000, 1, 1) : desde;
    }

    private LocalDate fin(LocalDate hasta) {
        // Sin fecha final se incluye el futuro: las cuotas por vencer también cuentan.
        return hasta == null ? LocalDate.now(reloj).plusYears(50) : hasta;
    }
}
