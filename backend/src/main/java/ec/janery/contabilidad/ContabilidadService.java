package ec.janery.contabilidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.caja.CajaService;
import ec.janery.common.NegocioException;
import ec.janery.common.Pagina;
import ec.janery.cuenta.CuentaService;
import ec.janery.socio.ParticipacionService;
import lombok.RequiredArgsConstructor;

/** Balance y libros en el formato del propietario. Todo sale de la vista libro_contable; solo se escribe el registro manual. */
@Service
@RequiredArgsConstructor
public class ContabilidadService {

    public record LineaValor(String codigo, String nombre, BigDecimal valor, boolean detalle) {
    }

    public record Seccion(String titulo, List<LineaValor> lineas, BigDecimal total) {
    }

    public record Indicador(String nombre, BigDecimal valor, String explicacion) {
    }

    public record Estado(String periodo, Seccion ganancias, Seccion gastos, BigDecimal resultadoMes, Seccion activoCorriente,
            Seccion activosFijos, Seccion pasivos, Seccion capital, BigDecimal totalActivos, BigDecimal totalPasivoCapital,
            BigDecimal diferencia, List<Indicador> indicadores) {
    }

    public record MesResultado(String periodo, BigDecimal ganancias, BigDecimal gastos, BigDecimal resultado) {
    }

    /** sube y baja: lo que la línea aumentó o disminuyó, en palabras simples. quien: de quién es el movimiento. */
    public record FilaLibro(Instant fecha, Long operacionId, String linea, String nombre, String concepto, BigDecimal debe,
            BigDecimal haber, BigDecimal saldo, BigDecimal sube, BigDecimal baja, String quien, BigDecimal cajaDespues) {
    }

    public record Mayor(String linea, String nombre, BigDecimal saldoInicial, BigDecimal saldoFinal, Pagina<FilaLibro> movimientos) {
    }

    public record Diario(BigDecimal totalDebe, BigDecimal totalHaber, Pagina<FilaLibro> movimientos, BigDecimal entradasCaja,
            BigDecimal salidasCaja, BigDecimal saldoCaja) {
    }

    public record LineaManual(String codigo, String nombre, boolean admiteDisminuir) {
    }

    public record AsientoVista(Long id, String linea, String nombre, BigDecimal importe, String descripcion, Long reversoDe,
            boolean revertido, Instant fecha) {
    }

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    /** Nombre corto de la persona de la operación de cada fila: «Ana Paz». Null si no es de una persona. */
    private static final String QUIEN = "(SELECT " + ec.janery.auditoria.HistorialSql.nombreCorto("qp")
            + " FROM personas qp WHERE qp.id = persona_de_operacion(operacion_id))";
    private static final String SALDO_LINEAS = "SELECT linea, SUM(importe) AS total FROM libro_contable WHERE fecha >= :desde AND fecha < :hasta GROUP BY linea";

    private static final Map<String, String> GANANCIAS = lineas("4.1", "Intereses ganados en créditos", "4.2", "Interés de mora cobradas",
            "4.3", "Multas cobradas", "4.4", "Intereses bancarios", "4.5", "Ingresos por actividades", "4.6", "Venta de formularios", "4.7", "Otros ingresos");
    private static final Map<String, String> GASTOS = lineas("5.1", "Intereses de los ahorros captados", "5.2", "Intereses de préstamos externos",
            "5.3", "Gastos bancarios", "5.4", "Gastos de personal", "5.5", "Gastos de suministros", "5.6", "Gastos de transportes",
            "5.7", "Gastos de alimentación", "5.8", "Otros gastos operativos", "5.9", "Gastos de provisión");
    private static final Map<String, String> ACTIVO_CORRIENTE = lineas("1.1", "Caja (dinero en efectivo)", "1.2", "Bancos (libreta de ahorros)",
            "1.3", "Inversiones (certificados en Coacs)", "1.4", "Créditos por cobrar", "1.99", "Provisión de cartera", "1.5", "Otras cuentas por cobrar");
    private static final Map<String, String> ACTIVO_FIJO = lineas("1.6", "Local", "1.7", "Muebles y equipos", "1.8", "Otros activos");
    private static final Map<String, String> PASIVOS = lineas("2.1", "Ahorros captados", "2.2", "Plazo fijo", "2.3", "Cuentas por pagar",
            "2.4", "Créditos externos por pagar", "2.5", "Fondo de desgravamen", "2.6", "Fondo solidario");
    private static final Map<String, String> CAPITAL = lineas("3.1", "Aportes iniciales", "3.2", "Aportes mensuales y adicionales", "3.3", "Capitalizaciones",
            "3.4", "Donaciones", "3.5", "Reservas", "3.6", "Resultado del mes", "3.7", "Resultado acumulado");

    /** Lo que el sistema ya calcula no se escribe a mano: se contaría dos veces. */
    private static final Map<String, Boolean> MANUALES = manuales();

    private final JdbcClient jdbc;
    private final CuentaService cuentas;
    private final CajaService caja;
    private final AuditoriaService auditoria;
    private final ParticipacionService participacion;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public Estado estado(String periodoTexto) {
        YearMonth periodo = periodo(periodoTexto);
        Timestamp inicio = inicio(periodo);
        Timestamp fin = inicio(periodo.plusMonths(1));
        Map<String, BigDecimal> acumulado = sumas(Timestamp.from(Instant.EPOCH), fin);
        Map<String, BigDecimal> mes = sumas(inicio, fin);
        Map<String, BigDecimal> previo = sumas(Timestamp.from(Instant.EPOCH), inicio);

        Seccion ganancias = seccion("Ganancias", GANANCIAS, mes);
        Seccion gastos = seccion("Gastos", GASTOS, mes);
        BigDecimal resultadoMes = ganancias.total().subtract(gastos.total());

        // El resultado de meses anteriores queda en 3.7 junto con lo ya retirado o separado a reserva.
        BigDecimal resultadoPrevio = sumar(previo, GANANCIAS).subtract(sumar(previo, GASTOS));
        Map<String, BigDecimal> balance = new HashMap<>(acumulado);
        balance.put("3.6", resultadoMes);
        balance.put("3.7", acumulado.getOrDefault("3.7", CERO).add(resultadoPrevio));

        BigDecimal cartera = acumulado.getOrDefault("1.4", CERO);
        BigDecimal conRetraso = carteraConRetraso(periodo, fin);
        Seccion activoCorriente = activoCorriente(balance, cartera, conRetraso);
        Seccion activosFijos = seccion("Activos fijos", ACTIVO_FIJO, balance);
        Seccion pasivos = seccion("Pasivos", PASIVOS, balance);
        Seccion capital = seccion("Capital (patrimonio)", CAPITAL, balance);

        BigDecimal activos = activoCorriente.total().add(activosFijos.total());
        BigDecimal pasivoCapital = pasivos.total().add(capital.total());
        return new Estado(periodo.toString(), ganancias, gastos, resultadoMes, activoCorriente, activosFijos, pasivos, capital,
                activos, pasivoCapital, activos.subtract(pasivoCapital),
                indicadores(balance.getOrDefault("1.1", CERO), cartera, conRetraso, activos, pasivos.total(), capital.total(), resultadoMes));
    }

    @Transactional(readOnly = true)
    public List<MesResultado> evolucion(int meses) {
        YearMonth actual = YearMonth.now(reloj.withZone(ZONA));
        List<MesResultado> filas = new ArrayList<>();
        for (int i = meses - 1; i >= 0; i--) {
            YearMonth p = actual.minusMonths(i);
            Map<String, BigDecimal> mes = sumas(inicio(p), inicio(p.plusMonths(1)));
            BigDecimal g = sumar(mes, GANANCIAS);
            BigDecimal c = sumar(mes, GASTOS);
            filas.add(new MesResultado(p.toString(), g, c, g.subtract(c)));
        }
        return filas;
    }

    @Transactional(readOnly = true)
    public Diario diario(String periodoTexto, int pagina, int tamano) {
        YearMonth periodo = periodo(periodoTexto);
        Timestamp desde = inicio(periodo);
        Timestamp hasta = inicio(periodo.plusMonths(1));
        long total = jdbc.sql("SELECT COUNT(*) FROM libro_contable WHERE fecha >= :desde AND fecha < :hasta")
                .param("desde", desde).param("hasta", hasta).query(Long.class).single();
        // caja_despues: en cada movimiento de la caja (línea 1.1), lo que queda en ella después, contando toda la historia.
        List<FilaLibro> filas = jdbc.sql("""
                SELECT t.*, %s AS quien FROM (
                    SELECT fecha, operacion_id, linea, concepto, importe,
                           CASE WHEN linea = '1.1' THEN SUM(CASE WHEN linea = '1.1' THEN importe ELSE 0 END)
                                OVER (ORDER BY fecha, operacion_id NULLS FIRST, linea, concepto, importe ROWS UNBOUNDED PRECEDING) END AS caja_despues
                    FROM libro_contable WHERE fecha < :hasta
                ) t WHERE fecha >= :desde ORDER BY fecha DESC, operacion_id DESC NULLS LAST, linea DESC, concepto DESC, importe DESC
                LIMIT :limite OFFSET :salto
                """.formatted(QUIEN)).param("desde", desde).param("hasta", hasta).param("limite", tamano).param("salto", pagina * tamano)
                .query((rs, n) -> fila(rs.getTimestamp("fecha").toInstant(), rs.getObject("operacion_id", Long.class), rs.getString("linea"),
                        rs.getString("concepto"), rs.getBigDecimal("importe"), null, rs.getString("quien"), rs.getBigDecimal("caja_despues")))
                .list();
        List<BigDecimal> totales = jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN (left(linea, 1) IN ('1', '5')) = (importe > 0) THEN ABS(importe) ELSE 0 END), 0) AS debe,
                       COALESCE(SUM(CASE WHEN (left(linea, 1) IN ('1', '5')) = (importe > 0) THEN 0 ELSE ABS(importe) END), 0) AS haber
                FROM libro_contable WHERE fecha >= :desde AND fecha < :hasta
                """).param("desde", desde).param("hasta", hasta)
                .query((rs, n) -> List.of(rs.getBigDecimal("debe"), rs.getBigDecimal("haber"))).single();
        List<BigDecimal> caja = jdbc.sql("""
                SELECT COALESCE(SUM(importe) FILTER (WHERE importe > 0), 0) AS entra, COALESCE(-SUM(importe) FILTER (WHERE importe < 0), 0) AS sale
                FROM libro_contable WHERE linea = '1.1' AND fecha >= :desde AND fecha < :hasta
                """).param("desde", desde).param("hasta", hasta)
                .query((rs, n) -> List.of(rs.getBigDecimal("entra"), rs.getBigDecimal("sale"))).single();
        BigDecimal saldoCaja = jdbc.sql("SELECT COALESCE(SUM(importe), 0) FROM libro_contable WHERE linea = '1.1'")
                .query(BigDecimal.class).single();
        return new Diario(totales.get(0), totales.get(1), new Pagina<>(filas, pagina, tamano, total), caja.get(0), caja.get(1),
                saldoCaja);
    }

    @Transactional(readOnly = true)
    public Mayor mayor(String linea, String periodoTexto, int pagina, int tamano) {
        String nombre = nombreLinea(linea);
        YearMonth periodo = periodo(periodoTexto);
        Timestamp desde = inicio(periodo);
        Timestamp hasta = inicio(periodo.plusMonths(1));
        BigDecimal inicial = jdbc.sql("SELECT COALESCE(SUM(importe), 0) FROM libro_contable WHERE linea = :linea AND fecha < :desde")
                .param("linea", linea).param("desde", desde).query(BigDecimal.class).single();
        BigDecimal enPeriodo = jdbc.sql("SELECT COALESCE(SUM(importe), 0) FROM libro_contable WHERE linea = :linea AND fecha >= :desde AND fecha < :hasta")
                .param("linea", linea).param("desde", desde).param("hasta", hasta).query(BigDecimal.class).single();
        long total = jdbc.sql("SELECT COUNT(*) FROM libro_contable WHERE linea = :linea AND fecha >= :desde AND fecha < :hasta")
                .param("linea", linea).param("desde", desde).param("hasta", hasta).query(Long.class).single();
        List<FilaLibro> filas = jdbc.sql("""
                SELECT t.*, %s AS quien FROM (
                    SELECT fecha, operacion_id, linea, concepto, importe,
                           SUM(importe) OVER (ORDER BY fecha, operacion_id NULLS FIRST, concepto) AS saldo
                    FROM libro_contable WHERE linea = :linea AND fecha < :hasta
                ) t WHERE fecha >= :desde ORDER BY fecha DESC, operacion_id DESC NULLS LAST LIMIT :limite OFFSET :salto
                """.formatted(QUIEN)).param("linea", linea).param("desde", desde).param("hasta", hasta).param("limite", tamano).param("salto", pagina * tamano)
                .query((rs, n) -> fila(rs.getTimestamp("fecha").toInstant(), rs.getObject("operacion_id", Long.class), rs.getString("linea"),
                        rs.getString("concepto"), rs.getBigDecimal("importe"), rs.getBigDecimal("saldo"), rs.getString("quien"), null)).list();
        return new Mayor(linea, nombre, inicial, inicial.add(enPeriodo), new Pagina<>(filas, pagina, tamano, total));
    }

    public List<LineaManual> lineasManuales() {
        return MANUALES.entrySet().stream().map(e -> new LineaManual(e.getKey(), nombreLinea(e.getKey()), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public Pagina<AsientoVista> asientos(int pagina, int tamano) {
        long total = jdbc.sql("SELECT COUNT(*) FROM asientos_manuales").query(Long.class).single();
        List<AsientoVista> filas = jdbc.sql("""
                SELECT a.id, a.linea, a.importe, a.descripcion, a.reverso_de, a.creado_en,
                       EXISTS (SELECT 1 FROM asientos_manuales r WHERE r.reverso_de = a.id) AS revertido
                FROM asientos_manuales a ORDER BY a.id DESC LIMIT :limite OFFSET :salto
                """).param("limite", tamano).param("salto", pagina * tamano)
                .query((rs, n) -> new AsientoVista(rs.getLong("id"), rs.getString("linea"), nombreLinea(rs.getString("linea")),
                        rs.getBigDecimal("importe"), rs.getString("descripcion"), rs.getObject("reverso_de", Long.class),
                        rs.getBoolean("revertido"), rs.getTimestamp("creado_en").toInstant())).list();
        return new Pagina<>(filas, pagina, tamano, total);
    }

    /** Ingresos y gastos solo aumentan; una corrección se hace con un reverso. La caja se mueve salvo en la provisión. */
    @Transactional
    public Long registrar(Long actorId, String linea, boolean aumenta, BigDecimal importe, String descripcion, String clave) {
        Boolean admiteDisminuir = MANUALES.get(linea);
        if (admiteDisminuir == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "LINEA_NO_MANUAL", "Esta línea la calcula el sistema");
        }
        if (!aumenta && !admiteDisminuir) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "EFECTO_INVALIDO", "Ingresos y gastos se corrigen con un reverso");
        }
        BigDecimal firmado = aumenta ? importe : importe.negate();
        if (!aumenta && saldoLinea(linea).compareTo(importe) < 0) {
            throw NegocioException.conflicto("SALDO_INSUFICIENTE", "La línea no tiene saldo suficiente para disminuir");
        }
        Long operacion = cuentas.crearOperacionInterna("ASIENTO_MANUAL", importe, clave, actorId);
        // La caja se mueve primero: la reserva de un ingreso sale del dinero que acaba de entrar.
        moverCaja(linea, firmado, descripcion, operacion);
        BigDecimal reserva = repartir(linea, importe, descripcion, actorId);
        Long id = insertar(linea, firmado, descripcion, operacion, null, actorId, reserva);
        auditoria.registrar(actorId, "ASIENTO_MANUAL", "CONTABILIDAD", "asiento", id, null, linea + " " + firmado.toPlainString(), descripcion);
        return id;
    }

    @Transactional
    public Long revertir(Long actorId, Long asientoId, String motivo) {
        var original = jdbc.sql("SELECT linea, importe, reverso_de, reserva FROM asientos_manuales WHERE id = :id FOR UPDATE").param("id", asientoId)
                .query((rs, n) -> new Object[] { rs.getString("linea"), rs.getBigDecimal("importe"), rs.getObject("reverso_de", Long.class), rs.getBigDecimal("reserva") })
                .optional().orElseThrow(() -> NegocioException.noEncontrado("Asiento no encontrado"));
        boolean revertido = jdbc.sql("SELECT EXISTS (SELECT 1 FROM asientos_manuales WHERE reverso_de = :id)").param("id", asientoId)
                .query(Boolean.class).single();
        if (original[2] != null || revertido) {
            throw NegocioException.conflicto("ASIENTO_YA_REVERTIDO", "Este asiento ya se revirtió");
        }
        String linea = (String) original[0];
        BigDecimal firmado = ((BigDecimal) original[1]).negate();
        String descripcion = "Reverso del asiento " + asientoId + ": " + motivo;
        Long operacion = cuentas.crearOperacionInterna("ASIENTO_MANUAL", firmado.abs(), null, actorId);
        moverCaja(linea, firmado, descripcion, operacion);
        if (linea.startsWith("4")) {
            participacion.revertirGanancia(firmado.abs(), (BigDecimal) original[3], recortar(descripcion), actorId);
        } else if (linea.startsWith("5")) {
            participacion.revertirGasto(firmado.abs(), recortar(descripcion), actorId);
        }
        Long id = insertar(linea, firmado, descripcion, operacion, asientoId, actorId, CERO);
        auditoria.registrar(actorId, "ASIENTO_REVERTIDO", "CONTABILIDAD", "asiento", id, null, asientoId.toString(), motivo);
        return id;
    }

    /** Solo ingresos y gastos cambian lo que es de los socios; un activo o un pasivo no. */
    private BigDecimal repartir(String linea, BigDecimal importe, String descripcion, Long actorId) {
        if (linea.startsWith("4")) {
            return participacion.repartirAutomatico("GANANCIA", importe, recortar(descripcion), actorId);
        }
        if (linea.startsWith("5")) {
            participacion.repartirAutomatico("PERDIDA", importe, recortar(descripcion), actorId);
        }
        return CERO;
    }

    private Long insertar(String linea, BigDecimal importe, String descripcion, Long operacion, Long reversoDe, Long actorId, BigDecimal reserva) {
        return jdbc.sql("""
                INSERT INTO asientos_manuales (linea, importe, descripcion, operacion_id, reverso_de, actor_id, creado_en, reserva)
                VALUES (:linea, :importe, :descripcion, :operacion, :reverso, :actor, :fecha, :reserva) RETURNING id
                """).param("linea", linea).param("importe", importe).param("descripcion", recortar(descripcion))
                .param("operacion", operacion).param("reverso", reversoDe, java.sql.Types.BIGINT).param("actor", actorId).param("reserva", reserva)
                .param("fecha", Timestamp.from(Instant.now(reloj))).query(Long.class).single();
    }

    /** Un activo o gasto que aumenta se paga de la caja; un pasivo, patrimonio o ingreso que aumenta entra a ella. */
    private void moverCaja(String linea, BigDecimal firmado, String descripcion, Long operacion) {
        if ("5.9".equals(linea)) {
            return;
        }
        boolean porDebe = linea.startsWith("1") || linea.startsWith("5");
        boolean sale = porDebe == (firmado.signum() > 0);
        BigDecimal importe = firmado.abs();
        if (sale && caja.saldoBloqueado(CajaService.PRINCIPAL).compareTo(importe) < 0) {
            throw NegocioException.conflicto("CAJA_INSUFICIENTE", "La caja principal no tiene dinero suficiente");
        }
        caja.registrar(CajaService.PRINCIPAL, operacion, sale ? "SALIDA" : "ENTRADA", importe, "ASIENTO_MANUAL", recortar(descripcion));
    }

    private BigDecimal saldoLinea(String linea) {
        return jdbc.sql("SELECT COALESCE(SUM(importe), 0) FROM libro_contable WHERE linea = :linea").param("linea", linea)
                .query(BigDecimal.class).single();
    }

    /** Capital pendiente, a esa fecha, de los créditos con alguna cuota vencida sin pagar. */
    private BigDecimal carteraConRetraso(YearMonth periodo, Timestamp fin) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA));
        LocalDate corte = periodo.plusMonths(1).atDay(1).isAfter(hoy) ? hoy : periodo.plusMonths(1).atDay(1);
        return jdbc.sql("""
                SELECT COALESCE(SUM(pendiente), 0) FROM (
                    SELECT (SELECT COALESCE(SUM(d.importe), 0) FROM desembolsos_credito d WHERE d.credito_id = c.id AND d.creado_en < :fin)
                         - (SELECT COALESCE(SUM(dp.importe), 0) FROM detalles_pago dp JOIN pagos_credito p ON p.id = dp.pago_id
                            WHERE p.credito_id = c.id AND dp.concepto = 'CAPITAL' AND p.creado_en < :fin) AS pendiente
                    FROM creditos c
                    WHERE EXISTS (SELECT 1 FROM cuotas_credito q WHERE q.credito_id = c.id AND q.vencimiento < :corte
                                  AND (q.pagada_en IS NULL OR q.pagada_en >= :fin))
                ) t
                """).param("fin", fin).param("corte", java.sql.Date.valueOf(corte)).query(BigDecimal.class).single().setScale(2);
    }

    private Seccion activoCorriente(Map<String, BigDecimal> balance, BigDecimal cartera, BigDecimal conRetraso) {
        List<LineaValor> lineas = new ArrayList<>();
        BigDecimal total = CERO;
        for (Map.Entry<String, String> l : ACTIVO_CORRIENTE.entrySet()) {
            BigDecimal valor = balance.getOrDefault(l.getKey(), CERO);
            lineas.add(new LineaValor(l.getKey(), l.getValue(), valor, false));
            total = total.add(valor);
            if ("1.4".equals(l.getKey())) {
                lineas.add(new LineaValor("1.41", "Cartera de crédito sin retraso", cartera.subtract(conRetraso), true));
                lineas.add(new LineaValor("1.42", "Saldo de cartera de crédito con retraso", conRetraso, true));
            }
        }
        return new Seccion("Activo corriente", lineas, total);
    }

    private static List<Indicador> indicadores(BigDecimal caja, BigDecimal cartera, BigDecimal conRetraso, BigDecimal activos,
            BigDecimal pasivos, BigDecimal capital, BigDecimal resultado) {
        return List.of(
                new Indicador("Morosidad", porcentaje(conRetraso, cartera), "Cartera con retraso sobre créditos por cobrar"),
                new Indicador("Liquidez", porcentaje(caja, pasivos), "Caja sobre total de pasivos"),
                new Indicador("Solvencia", porcentaje(capital, activos), "Patrimonio sobre total de activos"),
                new Indicador("Rentabilidad del mes", porcentaje(resultado, activos), "Resultado del mes sobre total de activos"));
    }

    /** Sin base no hay indicador: null, y la pantalla lo dice en lugar de mostrar cero. */
    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal base) {
        return base.signum() == 0 ? null : parte.multiply(CIEN).divide(base, 2, RoundingMode.HALF_UP);
    }

    private FilaLibro fila(Instant fecha, Long operacion, String linea, String concepto, BigDecimal importe, BigDecimal saldo,
            String persona, BigDecimal cajaDespues) {
        boolean alDebe = (linea.startsWith("1") || linea.startsWith("5")) == (importe.signum() > 0);
        BigDecimal valor = importe.abs();
        return new FilaLibro(fecha, operacion, linea, nombreLinea(linea), concepto, alDebe ? valor : CERO, alDebe ? CERO : valor, saldo,
                importe.signum() > 0 ? valor : CERO, importe.signum() < 0 ? valor : CERO, ec.janery.common.Quien.de(persona, concepto, linea),
                cajaDespues);
    }

    private Map<String, BigDecimal> sumas(Timestamp desde, Timestamp hasta) {
        Map<String, BigDecimal> resultado = new HashMap<>();
        jdbc.sql(SALDO_LINEAS).param("desde", desde).param("hasta", hasta)
                .query(rs -> {
                    resultado.put(rs.getString("linea"), rs.getBigDecimal("total"));
                });
        return resultado;
    }

    private static Seccion seccion(String titulo, Map<String, String> catalogo, Map<String, BigDecimal> valores) {
        List<LineaValor> lineas = catalogo.entrySet().stream()
                .map(l -> new LineaValor(l.getKey(), l.getValue(), valores.getOrDefault(l.getKey(), CERO), false)).toList();
        return new Seccion(titulo, lineas, lineas.stream().map(LineaValor::valor).reduce(CERO, BigDecimal::add));
    }

    private static BigDecimal sumar(Map<String, BigDecimal> valores, Map<String, String> catalogo) {
        return catalogo.keySet().stream().map(k -> valores.getOrDefault(k, CERO)).reduce(CERO, BigDecimal::add);
    }

    private YearMonth periodo(String texto) {
        YearMonth actual = YearMonth.now(reloj.withZone(ZONA));
        if (texto == null || texto.isBlank()) {
            return actual;
        }
        try {
            YearMonth p = YearMonth.parse(texto);
            if (p.isAfter(actual)) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODO_FUTURO", "El periodo aún no ha ocurrido");
            }
            return p;
        } catch (DateTimeParseException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODO_INVALIDO", "Indique el periodo como AAAA-MM");
        }
    }

    private static Timestamp inicio(YearMonth periodo) {
        return Timestamp.from(periodo.atDay(1).atStartOfDay(ZONA).toInstant());
    }

    private static String nombreLinea(String linea) {
        for (Map<String, String> c : List.of(GANANCIAS, GASTOS, ACTIVO_CORRIENTE, ACTIVO_FIJO, PASIVOS, CAPITAL)) {
            if (c.containsKey(linea)) {
                return c.get(linea);
            }
        }
        throw new NegocioException(HttpStatus.BAD_REQUEST, "LINEA_INVALIDA", "Esa línea no existe en el balance");
    }

    private static String recortar(String texto) {
        return texto.length() > 200 ? texto.substring(0, 200) : texto;
    }

    private static Map<String, String> lineas(String... pares) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(pares[i], pares[i + 1]);
        }
        return mapa;
    }

    /** Valor: si la línea admite disminuir. Los activos y pasivos sí; ingresos y gastos, solo con reverso. */
    private static Map<String, Boolean> manuales() {
        Map<String, Boolean> mapa = new LinkedHashMap<>();
        for (String l : List.of("4.2", "4.3", "4.4", "4.5", "4.6", "4.7", "5.2", "5.3", "5.4", "5.5", "5.6", "5.7", "5.8", "5.9")) {
            mapa.put(l, false);
        }
        for (String l : List.of("1.2", "1.3", "1.5", "1.6", "1.7", "1.8", "2.4", "2.6", "3.3", "3.4")) {
            mapa.put(l, true);
        }
        return mapa;
    }
}
