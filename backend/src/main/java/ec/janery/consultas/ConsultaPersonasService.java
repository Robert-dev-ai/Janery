package ec.janery.consultas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.common.NegocioException;
import ec.janery.common.Pagina;
import lombok.RequiredArgsConstructor;

/** Lecturas de varias personas. Quién puede usar cada una lo decide su controlador. */
@Service
@RequiredArgsConstructor
public class ConsultaPersonasService {

    public record ResumenGeneral(BigDecimal cajaPrincipal, BigDecimal fondoReserva, long personasConCuenta, long creditosActivos,
            BigDecimal capitalPrestado, BigDecimal saldosEnCuentas, BigDecimal ahorros) {
    }

    /** Sin identificación, teléfono ni dirección: es lo que ven los socios de cada persona. */
    public record PersonaFinanciera(Long id, String nombre, String cuenta, BigDecimal saldoCuenta, BigDecimal ahorros,
            long creditosActivos, BigDecimal capitalPendiente, long cuotasVencidas, boolean socio) {
    }

    public record CuotaDetalle(int numero, LocalDate vencimiento, BigDecimal total, BigDecimal pagado, Instant pagadaEn) {
    }

    public record CreditoDetalle(Long id, String familia, String modalidad, String estado, BigDecimal importe, BigDecimal capitalPendiente,
            Instant solicitadoEn, Instant finalizadoEn, String motivoRechazo, List<CuotaDetalle> cuotas) {
    }

    public record AhorroDetalle(Long id, String nombre, String tipo, String estado, BigDecimal saldo) {
    }

    public record MovimientoGeneral(Long id, Instant fecha, String persona, String concepto, String sentido, BigDecimal importe) {
    }

    public record DetalleFinanciero(PersonaFinanciera persona, List<CreditoDetalle> creditos, List<AhorroDetalle> ahorros,
            List<MovimientoGeneral> movimientos) {
    }

    public record PersonaContacto(Long id, String nombres, String apellidos, String identificacion, String telefono, String direccion,
            String correo, String estadoCuenta, boolean socio, Instant registradaEn) {
    }

    public record HistorialCrediticio(Long id, String nombre, long total, long activos, long finalizados, long rechazados,
            BigDecimal capitalPendiente, long cuotasVencidas, long cuotasPagadasConRetraso) {
    }

    private static final String FILTRO = "(:texto = '' OR lower(p.nombres || ' ' || p.apellidos) LIKE '%' || :texto || '%')";

    private final JdbcClient jdbc;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public ResumenGeneral resumenGeneral() {
        return jdbc.sql("""
                SELECT (SELECT COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0) FROM movimientos_caja m
                        JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'PRINCIPAL') AS principal,
                       (SELECT COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0) FROM movimientos_caja m
                        JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA') AS reserva,
                       (SELECT COUNT(*) FROM cuentas_principales WHERE estado IN ('ACTIVA', 'PENDIENTE_CIERRE')) AS personas,
                       (SELECT COUNT(*) FROM creditos WHERE estado = 'ACTIVO') AS creditos,
                       (SELECT COALESCE(SUM(capital_pendiente), 0) FROM creditos WHERE estado = 'ACTIVO') AS prestado,
                       (SELECT COALESCE(SUM(saldo_disponible), 0) FROM cuentas_principales WHERE estado IN ('ACTIVA', 'PENDIENTE_CIERRE')) AS cuentas,
                       (SELECT COALESCE(SUM(saldo), 0) FROM cuentas_ahorro WHERE estado = 'ACTIVA') AS ahorros
                """).query((rs, n) -> new ResumenGeneral(rs.getBigDecimal("principal"), rs.getBigDecimal("reserva"), rs.getLong("personas"),
                rs.getLong("creditos"), rs.getBigDecimal("prestado"), rs.getBigDecimal("cuentas"), rs.getBigDecimal("ahorros"))).single();
    }

    @Transactional(readOnly = true)
    public Pagina<PersonaFinanciera> personasFinancieras(String texto, int pagina, int tamano) {
        String t = normalizar(texto);
        long total = jdbc.sql("SELECT COUNT(*) FROM personas p JOIN cuentas_principales c ON c.persona_id = p.id AND c.estado IN ('ACTIVA', 'PENDIENTE_CIERRE') WHERE " + FILTRO)
                .param("texto", t).query(Long.class).single();
        List<PersonaFinanciera> filas = jdbc.sql(FINANCIERA + " WHERE " + FILTRO + " ORDER BY p.apellidos, p.nombres LIMIT :limite OFFSET :salto")
                .param("texto", t).param("hoy", java.sql.Date.valueOf(LocalDate.now(reloj))).param("limite", tamano).param("salto", pagina * tamano)
                .query((rs, n) -> financiera(rs)).list();
        return new Pagina<>(filas, pagina, tamano, total);
    }

    @Transactional(readOnly = true)
    public DetalleFinanciero detalleFinanciero(Long personaId) {
        PersonaFinanciera persona = jdbc.sql(FINANCIERA + " WHERE p.id = :id").param("id", personaId)
                .param("hoy", java.sql.Date.valueOf(LocalDate.now(reloj))).query((rs, n) -> financiera(rs)).optional()
                .orElseThrow(() -> NegocioException.noEncontrado("Persona no encontrada"));
        List<AhorroDetalle> ahorros = jdbc.sql("SELECT id, nombre, tipo, estado, saldo FROM cuentas_ahorro WHERE persona_id = :id ORDER BY id DESC")
                .param("id", personaId).query((rs, n) -> new AhorroDetalle(rs.getLong("id"), rs.getString("nombre"), rs.getString("tipo"),
                        rs.getString("estado"), rs.getBigDecimal("saldo"))).list();
        List<MovimientoGeneral> movimientos = jdbc.sql(MOVIMIENTOS + " WHERE p.id = :id ORDER BY m.id DESC LIMIT 20").param("id", personaId)
                .query((rs, n) -> movimiento(rs)).list();
        return new DetalleFinanciero(persona, creditosDe(personaId), ahorros, movimientos);
    }

    @Transactional(readOnly = true)
    public Pagina<MovimientoGeneral> movimientos(int pagina, int tamano) {
        long total = jdbc.sql("SELECT COUNT(*) FROM movimientos_cuenta").query(Long.class).single();
        return new Pagina<>(jdbc.sql(MOVIMIENTOS + " ORDER BY m.id DESC LIMIT :limite OFFSET :salto").param("limite", tamano)
                .param("salto", pagina * tamano).query((rs, n) -> movimiento(rs)).list(), pagina, tamano, total);
    }

    @Transactional(readOnly = true)
    public Pagina<PersonaContacto> personasContacto(String texto, int pagina, int tamano) {
        String t = normalizar(texto);
        long total = jdbc.sql("SELECT COUNT(*) FROM personas p JOIN usuarios u ON u.id = p.usuario_id WHERE (" + FILTRO + " OR p.identificacion LIKE '%' || :texto || '%' OR lower(u.correo) LIKE '%' || :texto || '%')")
                .param("texto", t).query(Long.class).single();
        List<PersonaContacto> filas = jdbc.sql("""
                SELECT p.id, p.nombres, p.apellidos, p.identificacion, p.telefono, p.direccion, p.creado_en, u.correo,
                       (SELECT c.estado FROM cuentas_principales c WHERE c.persona_id = p.id AND c.estado NOT IN ('CERRADA', 'RECHAZADA') LIMIT 1) AS estado_cuenta,
                       EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO') AS socio
                FROM personas p JOIN usuarios u ON u.id = p.usuario_id
                """ + " WHERE (" + FILTRO + " OR p.identificacion LIKE '%' || :texto || '%' OR lower(u.correo) LIKE '%' || :texto || '%') ORDER BY p.apellidos, p.nombres LIMIT :limite OFFSET :salto")
                .param("texto", t).param("limite", tamano).param("salto", pagina * tamano)
                .query((rs, n) -> new PersonaContacto(rs.getLong("id"), rs.getString("nombres"), rs.getString("apellidos"), rs.getString("identificacion"),
                        rs.getString("telefono"), rs.getString("direccion"), rs.getString("correo"), rs.getString("estado_cuenta"), rs.getBoolean("socio"),
                        rs.getTimestamp("creado_en").toInstant())).list();
        return new Pagina<>(filas, pagina, tamano, total);
    }

    @Transactional(readOnly = true)
    public Pagina<HistorialCrediticio> historiales(String texto, int pagina, int tamano) {
        String t = normalizar(texto);
        long total = jdbc.sql("SELECT COUNT(*) FROM personas p WHERE EXISTS (SELECT 1 FROM creditos c WHERE c.persona_id = p.id) AND " + FILTRO)
                .param("texto", t).query(Long.class).single();
        List<HistorialCrediticio> filas = jdbc.sql("""
                SELECT p.id, p.nombres || ' ' || p.apellidos AS nombre, COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE c.estado = 'ACTIVO') AS activos,
                       COUNT(*) FILTER (WHERE c.estado = 'FINALIZADO') AS finalizados,
                       COUNT(*) FILTER (WHERE c.estado = 'RECHAZADO') AS rechazados,
                       COALESCE(SUM(c.capital_pendiente) FILTER (WHERE c.estado = 'ACTIVO'), 0) AS capital,
                       (SELECT COUNT(*) FROM cuotas_credito q JOIN creditos x ON x.id = q.credito_id
                        WHERE x.persona_id = p.id AND x.estado = 'ACTIVO' AND q.pagada_en IS NULL AND q.vencimiento < :hoy) AS vencidas,
                       (SELECT COUNT(*) FROM cuotas_credito q JOIN creditos x ON x.id = q.credito_id
                        WHERE x.persona_id = p.id AND CAST(q.pagada_en AT TIME ZONE 'America/Guayaquil' AS date) > q.vencimiento) AS con_retraso
                FROM personas p JOIN creditos c ON c.persona_id = p.id
                """ + " WHERE " + FILTRO + " GROUP BY p.id, p.nombres, p.apellidos, p.apellidos ORDER BY p.apellidos, p.nombres LIMIT :limite OFFSET :salto")
                .param("texto", t).param("hoy", java.sql.Date.valueOf(LocalDate.now(reloj))).param("limite", tamano).param("salto", pagina * tamano)
                .query((rs, n) -> new HistorialCrediticio(rs.getLong("id"), rs.getString("nombre"), rs.getLong("total"), rs.getLong("activos"),
                        rs.getLong("finalizados"), rs.getLong("rechazados"), rs.getBigDecimal("capital"), rs.getLong("vencidas"), rs.getLong("con_retraso"))).list();
        return new Pagina<>(filas, pagina, tamano, total);
    }

    public record SolicitanteCredito(String nombre, String identificacion, String telefono, String direccion, String lugarTrabajo,
            String ocupacion, String antiguedadTrabajo, BigDecimal ingresosMensuales, LocalDate fechaNacimiento, String estadoCivil,
            String referenciaNombre, String referenciaTelefono, String banco, String tipoCuentaBanco, String numeroCuentaBanco) {
    }

    /** Lo que necesita quien aprueba un crédito para evaluarlo. */
    @Transactional(readOnly = true)
    public SolicitanteCredito solicitante(Long creditoId) {
        return jdbc.sql("""
                SELECT p.* FROM personas p JOIN creditos c ON c.persona_id = p.id WHERE c.id = :id
                """).param("id", creditoId).query((rs, n) -> new SolicitanteCredito(rs.getString("nombres") + " " + rs.getString("apellidos"),
                rs.getString("identificacion"), rs.getString("telefono"), rs.getString("direccion"), rs.getString("lugar_trabajo"),
                rs.getString("ocupacion"), rs.getString("antiguedad_trabajo"), rs.getBigDecimal("ingresos_mensuales"),
                rs.getDate("fecha_nacimiento") == null ? null : rs.getDate("fecha_nacimiento").toLocalDate(), rs.getString("estado_civil"),
                rs.getString("referencia_nombre"), rs.getString("referencia_telefono"), rs.getString("banco"), rs.getString("tipo_cuenta_banco"),
                rs.getString("numero_cuenta_banco"))).optional().orElseThrow(() -> NegocioException.noEncontrado("Crédito no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<CreditoDetalle> creditosDe(Long personaId) {
        return jdbc.sql("""
                SELECT id, familia, modalidad, estado, importe, capital_pendiente, creado_en, finalizado_en, motivo_rechazo
                FROM creditos WHERE persona_id = :id ORDER BY id DESC
                """).param("id", personaId).query((rs, n) -> {
            long id = rs.getLong("id");
            return new CreditoDetalle(id, rs.getString("familia"), rs.getString("modalidad"), rs.getString("estado"), rs.getBigDecimal("importe"),
                    rs.getBigDecimal("capital_pendiente"), rs.getTimestamp("creado_en").toInstant(),
                    rs.getTimestamp("finalizado_en") == null ? null : rs.getTimestamp("finalizado_en").toInstant(), rs.getString("motivo_rechazo"),
                    cuotasDe(id));
        }).list();
    }

    private List<CuotaDetalle> cuotasDe(long creditoId) {
        return jdbc.sql("""
                SELECT numero, vencimiento, total, capital_pagado + interes_pagado + desgravamen_pagado + ahorro_pagado AS pagado, pagada_en
                FROM cuotas_credito WHERE credito_id = :id ORDER BY numero
                """).param("id", creditoId).query((rs, n) -> new CuotaDetalle(rs.getInt("numero"), rs.getDate("vencimiento").toLocalDate(),
                rs.getBigDecimal("total"), rs.getBigDecimal("pagado"),
                rs.getTimestamp("pagada_en") == null ? null : rs.getTimestamp("pagada_en").toInstant())).list();
    }

    private static final String FINANCIERA = """
            SELECT p.id, p.nombres || ' ' || p.apellidos AS nombre, c.numero, COALESCE(c.saldo_disponible, 0) AS saldo,
                   (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a WHERE a.persona_id = p.id AND a.estado = 'ACTIVA') AS ahorros,
                   (SELECT COUNT(*) FROM creditos x WHERE x.persona_id = p.id AND x.estado = 'ACTIVO') AS creditos,
                   (SELECT COALESCE(SUM(x.capital_pendiente), 0) FROM creditos x WHERE x.persona_id = p.id AND x.estado = 'ACTIVO') AS capital,
                   (SELECT COUNT(*) FROM cuotas_credito q JOIN creditos x ON x.id = q.credito_id
                    WHERE x.persona_id = p.id AND x.estado = 'ACTIVO' AND q.pagada_en IS NULL AND q.vencimiento < :hoy) AS vencidas,
                   EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO') AS socio
            FROM personas p JOIN cuentas_principales c ON c.persona_id = p.id AND c.estado IN ('ACTIVA', 'PENDIENTE_CIERRE')
            """;

    private static final String MOVIMIENTOS = """
            SELECT m.id, m.creado_en, p.nombres || ' ' || p.apellidos AS persona, m.concepto, m.sentido, m.importe
            FROM movimientos_cuenta m JOIN cuentas_principales c ON c.id = m.cuenta_id JOIN personas p ON p.id = c.persona_id
            """;

    private static PersonaFinanciera financiera(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PersonaFinanciera(rs.getLong("id"), rs.getString("nombre"), rs.getString("numero"), rs.getBigDecimal("saldo"),
                rs.getBigDecimal("ahorros"), rs.getLong("creditos"), rs.getBigDecimal("capital"), rs.getLong("vencidas"), rs.getBoolean("socio"));
    }

    private static MovimientoGeneral movimiento(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MovimientoGeneral(rs.getLong("id"), rs.getTimestamp("creado_en").toInstant(), rs.getString("persona"), rs.getString("concepto"),
                rs.getString("sentido"), rs.getBigDecimal("importe"));
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase().replace("%", "").replace("_", "");
    }
}
