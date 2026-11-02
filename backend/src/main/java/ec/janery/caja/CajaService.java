package ec.janery.caja;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Fondos independientes: cada movimiento pertenece a una sola caja. */
@Service
@RequiredArgsConstructor
public class CajaService {

    public static final String PRINCIPAL = "PRINCIPAL";
    public static final String CHICA = "CHICA";
    public static final String FONDO_RESERVA = "FONDO_RESERVA";

    interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {
        @Query(value = "SELECT id FROM cajas WHERE codigo = :codigo", nativeQuery = true)
        Long idDe(@Param("codigo") String codigo);

        @Query(value = "SELECT id FROM cajas WHERE codigo = :codigo FOR UPDATE", nativeQuery = true)
        Long bloquear(@Param("codigo") String codigo);

        @Query("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from MovimientoCaja m where m.cajaId = :cajaId
                """)
        BigDecimal saldo(@Param("cajaId") Long cajaId);

        Page<MovimientoCaja> findByCajaId(Long cajaId, Pageable pageable);

        /** Entradas menos salidas de cada mes, en la hora de Ecuador. */
        @Query(value = """
                SELECT to_char(creado_en AT TIME ZONE 'America/Guayaquil', 'YYYY-MM') AS mes,
                       SUM(CASE WHEN sentido = 'ENTRADA' THEN importe ELSE -importe END) AS neto
                FROM movimientos_caja WHERE caja_id = :cajaId GROUP BY 1 ORDER BY 1
                """, nativeQuery = true)
        List<Object[]> netoPorMes(@Param("cajaId") Long cajaId);
    }

    private final MovimientoCajaRepository repository;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private final Clock reloj;

    @Transactional
    public void registrar(String caja, Long operacionId, String sentido, BigDecimal importe, String concepto,
            String detalle) {
        repository.save(new MovimientoCaja(null, repository.idDe(caja), operacionId, sentido, importe, concepto, detalle,
                Instant.now(reloj)));
    }

    @Transactional
    public void registrarEnPrincipal(Long operacionId, String sentido, BigDecimal importe, String concepto) {
        registrar(PRINCIPAL, operacionId, sentido, importe, concepto, null);
    }

    /** Saldo bajo bloqueo de la caja, para validar una salida sin carreras. Requiere transacción. */
    @Transactional
    public BigDecimal saldoBloqueado(String caja) {
        return repository.saldo(repository.bloquear(caja));
    }

    @Transactional(readOnly = true)
    public BigDecimal saldo(String caja) {
        return repository.saldo(repository.idDe(caja));
    }

    @Transactional(readOnly = true)
    public BigDecimal saldoPrincipal() {
        return saldo(PRINCIPAL);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoCaja> movimientos(String caja, int pagina, int tamano) {
        return repository.findByCajaId(repository.idDe(caja),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
    }

    @Transactional(readOnly = true)
    public Page<MovimientoCaja> movimientosPrincipal(int pagina, int tamano) {
        return movimientos(PRINCIPAL, pagina, tamano);
    }

    /** De quién es cada movimiento de una página: la persona, o un grupo si no es de alguien en especial. */
    @Transactional(readOnly = true)
    public Map<Long, String> quienes(List<MovimientoCaja> lista) {
        Map<Long, String> nombres = new HashMap<>();
        List<Long> operaciones = lista.stream().map(MovimientoCaja::getOperacionId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!operaciones.isEmpty()) {
            jdbc.sql("SELECT o.id, " + ec.janery.auditoria.HistorialSql.nombreCorto("p")
                    + " FROM unnest(:ops) AS o(id) JOIN personas p ON p.id = persona_de_operacion(o.id)")
                    .param("ops", operaciones.toArray(Long[]::new))
                    .query(rs -> {
                        nombres.put(rs.getLong(1), rs.getString(2));
                    });
        }
        Map<Long, String> resultado = new HashMap<>();
        lista.forEach(m -> resultado.put(m.getId(), ec.janery.common.Quien.de(nombres.get(m.getOperacionId()), m.getConcepto(), null)));
        return resultado;
    }

    /** cerrado: el mes ya terminó. Mientras corre, el porcentaje cambia con cada movimiento. Null si empezó sin saldo. */
    public record MesVariacion(String periodo, BigDecimal saldoInicial, BigDecimal saldoFinal, BigDecimal variacion,
            BigDecimal porcentaje, boolean cerrado) {
    }

    /** Cuánto subió o bajó la caja principal en cada uno de los últimos 12 meses, el actual primero. */
    @Transactional(readOnly = true)
    public List<MesVariacion> variacionMensualPrincipal() {
        YearMonth actual = YearMonth.now(reloj);
        Map<YearMonth, BigDecimal> netoPorMes = new HashMap<>();
        BigDecimal antes = BigDecimal.ZERO;
        YearMonth desde = actual.minusMonths(11);
        for (Object[] fila : repository.netoPorMes(repository.idDe(PRINCIPAL))) {
            YearMonth mes = YearMonth.parse((String) fila[0]);
            BigDecimal neto = (BigDecimal) fila[1];
            if (mes.isBefore(desde)) {
                antes = antes.add(neto);
            } else {
                netoPorMes.put(mes, neto);
            }
        }
        List<MesVariacion> meses = new ArrayList<>();
        BigDecimal saldo = antes;
        for (YearMonth mes = desde; !mes.isAfter(actual); mes = mes.plusMonths(1)) {
            BigDecimal neto = netoPorMes.getOrDefault(mes, BigDecimal.ZERO);
            BigDecimal fin = saldo.add(neto);
            BigDecimal porcentaje = saldo.signum() == 0 ? null
                    : neto.multiply(BigDecimal.valueOf(100)).divide(saldo, 2, RoundingMode.HALF_UP);
            meses.add(new MesVariacion(mes.toString(), saldo, fin, neto, porcentaje, mes.isBefore(actual)));
            saldo = fin;
        }
        Collections.reverse(meses);
        return meses;
    }
}
