package ec.janery.caja;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Totales de los gráficos de caja: se calculan de los movimientos, sin guardar otra copia. */
@Service
@RequiredArgsConstructor
public class EstadoCajaService {

    private static final int MESES = 12;
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    public record SaldoCaja(String codigo, BigDecimal saldo) {
    }

    public record MesCaja(String periodo, BigDecimal entradas, BigDecimal salidas) {
    }

    public record MesResultado(String periodo, BigDecimal ganancias, BigDecimal perdidas) {
    }

    public record TotalConcepto(String concepto, BigDecimal importe) {
    }

    /** A quién pertenece el dinero: sigue en la caja y se puede prestar, pero se le debe a esa persona. */
    public record DuenoDinero(String nombre, BigDecimal apertura, BigDecimal cuenta, BigDecimal ahorrosNormales,
            BigDecimal plazosFijos, BigDecimal encajes, BigDecimal ahorroLetras) {
    }

    public record EstadoCaja(List<SaldoCaja> saldos, List<MesCaja> meses, List<MesResultado> resultados,
            List<TotalConcepto> entradas, List<TotalConcepto> salidas, List<DuenoDinero> duenos) {
    }

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    /** Saldo de cada caja y, de la principal, lo ocurrido en los últimos doce meses, incluido el mes en curso. */
    @Transactional(readOnly = true)
    public EstadoCaja consultar() {
        YearMonth actual = YearMonth.now(reloj.withZone(ZONA));
        YearMonth primero = actual.minusMonths(MESES - 1L);
        LocalDate desde = primero.atDay(1);

        List<SaldoCaja> saldos = jdbc.query("""
                SELECT c.codigo, COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0) AS saldo
                FROM cajas c LEFT JOIN movimientos_caja m ON m.caja_id = c.id GROUP BY c.codigo ORDER BY c.codigo
                """, (r, i) -> new SaldoCaja(r.getString("codigo"), r.getBigDecimal("saldo")));

        Map<String, BigDecimal[]> caja = new HashMap<>();
        jdbc.query("""
                SELECT to_char(m.creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM') AS periodo,
                       COALESCE(SUM(m.importe) FILTER (WHERE m.sentido = 'ENTRADA'), 0) AS entradas,
                       COALESCE(SUM(m.importe) FILTER (WHERE m.sentido = 'SALIDA'), 0) AS salidas
                FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id
                WHERE c.codigo = 'PRINCIPAL' AND m.creado_en AT TIME ZONE 'America/Guayaquil' >= ?
                GROUP BY 1
                """, r -> {
                    caja.put(r.getString("periodo"), new BigDecimal[] { r.getBigDecimal("entradas"), r.getBigDecimal("salidas") });
                }, desde);

        Map<String, BigDecimal[]> resultado = new HashMap<>();
        jdbc.query("""
                SELECT to_char(fecha, 'YYYY-MM') AS periodo,
                       COALESCE(SUM(importe) FILTER (WHERE tipo = 'GANANCIA'), 0) AS ganancias,
                       COALESCE(SUM(importe) FILTER (WHERE tipo = 'PERDIDA'), 0) AS perdidas
                FROM resultados WHERE fecha >= ? GROUP BY 1
                """, r -> {
                    resultado.put(r.getString("periodo"), new BigDecimal[] { r.getBigDecimal("ganancias"), r.getBigDecimal("perdidas") });
                }, desde);

        // Un mes sin movimientos también se muestra: un hueco en el eje engañaría.
        List<MesCaja> meses = new ArrayList<>();
        List<MesResultado> resultados = new ArrayList<>();
        BigDecimal[] vacio = { BigDecimal.ZERO, BigDecimal.ZERO };
        for (YearMonth mes = primero; !mes.isAfter(actual); mes = mes.plusMonths(1)) {
            BigDecimal[] c = caja.getOrDefault(mes.toString(), vacio);
            BigDecimal[] x = resultado.getOrDefault(mes.toString(), vacio);
            meses.add(new MesCaja(mes.toString(), c[0], c[1]));
            resultados.add(new MesResultado(mes.toString(), x[0], x[1]));
        }
        return new EstadoCaja(saldos, meses, resultados, porConcepto("ENTRADA", desde), porConcepto("SALIDA", desde), duenos());
    }

    private List<DuenoDinero> duenos() {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT p.nombres || ' ' || p.apellidos AS nombre,
                        (SELECT COALESCE(SUM(d.importe_reembolsable), 0) FROM depositos_apertura d
                         JOIN cuentas_principales c ON c.id = d.cuenta_id WHERE c.persona_id = p.id AND d.devuelto_en IS NULL) AS apertura,
                        (SELECT COALESCE(SUM(c.saldo_disponible), 0) FROM cuentas_principales c WHERE c.persona_id = p.id) AS cuenta,
                        (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a WHERE a.persona_id = p.id AND a.tipo = 'NORMAL' AND a.estado = 'ACTIVA') AS normales,
                        (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a WHERE a.persona_id = p.id AND a.tipo = 'PLAZO_FIJO' AND a.estado = 'ACTIVA') AS plazos,
                        (SELECT COALESCE(SUM(e.importe), 0) FROM encajes_credito e
                         JOIN creditos c ON c.id = e.credito_id WHERE c.persona_id = p.id AND e.devuelto_en IS NULL) AS encajes,
                        (SELECT COALESCE(SUM(l.saldo), 0) FROM ahorro_letras_credito l
                         JOIN creditos c ON c.id = l.credito_id WHERE c.persona_id = p.id) AS letras
                    FROM personas p
                ) x WHERE apertura + cuenta + normales + plazos + encajes + letras > 0 ORDER BY nombre
                """, (r, i) -> new DuenoDinero(r.getString("nombre"), r.getBigDecimal("apertura"), r.getBigDecimal("cuenta"),
                r.getBigDecimal("normales"), r.getBigDecimal("plazos"), r.getBigDecimal("encajes"), r.getBigDecimal("letras")));
    }

    private List<TotalConcepto> porConcepto(String sentido, LocalDate desde) {
        return jdbc.query("""
                SELECT m.concepto, SUM(m.importe) AS importe
                FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id
                WHERE c.codigo = 'PRINCIPAL' AND m.sentido = ? AND m.creado_en AT TIME ZONE 'America/Guayaquil' >= ?
                GROUP BY m.concepto ORDER BY importe DESC, m.concepto
                """, (r, i) -> new TotalConcepto(r.getString("concepto"), r.getBigDecimal("importe")), sentido, desde);
    }
}
