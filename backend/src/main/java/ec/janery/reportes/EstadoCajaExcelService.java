package ec.janery.reportes;

import static ec.janery.reportes.LibroExcel.dinero;
import static ec.janery.reportes.LibroExcel.numero;
import static ec.janery.reportes.LibroExcel.texto;
import static ec.janery.reportes.LibroExcel.total;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.OrdenPersonas;
import ec.janery.contabilidad.ContabilidadService;
import ec.janery.reportes.LibroExcel.Estilo;
import ec.janery.reportes.LibroExcel.Hoja;
import ec.janery.socio.ParticipacionService;
import lombok.RequiredArgsConstructor;

/**
 * Foto de la caja en el momento en que se pide: resumen, balance, dinero de cada persona, créditos, ahorros y aportes,
 * en un libro de Excel. Todo se lee en una sola transacción, para que las hojas cuadren entre sí.
 */
@Service
@RequiredArgsConstructor
public class EstadoCajaExcelService {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", new java.util.Locale("es", "EC"));
    private static final int MAXIMO_DIARIO = 50_000;
    private static final BigDecimal CERO = BigDecimal.ZERO;
    private static final Map<String, String> FAMILIAS = Map.of("LETRAS", "Crédito a letras", "EMERGENTE_CLIENTE", "Emergente cliente",
            "EMERGENTE_SOCIO", "Emergente socio", "EMERGENTE_ESTACIONAL", "Emergente estacional");
    private static final Map<String, String> UNIDADES = Map.of("PCT_DIARIO", "% diario", "PCT_MENSUAL", "% mensual");

    private record PorPersona(String nombre, boolean socio, BigDecimal apertura, BigDecimal cuenta, BigDecimal normales,
            BigDecimal plazos, BigDecimal encajes, BigDecimal letras, BigDecimal creditos, BigDecimal aportes, BigDecimal fondo) {

        BigDecimal seLeDebe() {
            return apertura.add(cuenta).add(normales).add(plazos).add(encajes).add(letras);
        }
    }

    private record Credito(String codigo, String titular, String familia, String modalidad, LocalDate inicio, BigDecimal importe,
            Integer plazo, String tasa, BigDecimal capital, int vencidas, BigDecimal vencido, LocalDate proxima) {
    }

    private final JdbcClient jdbc;
    private final ContabilidadService contabilidad;
    private final ParticipacionService participacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    /** REPEATABLE READ: todas las consultas ven la base en el mismo instante, aunque alguien opere mientras tanto. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReporteService.Archivo generar(Long actorId) {
        ZonedDateTime ahora = ZonedDateTime.now(reloj.withZone(ZONA));
        LocalDate hoy = ahora.toLocalDate();

        List<PorPersona> personas = porPersona();
        List<Credito> creditos = creditos(hoy);

        LibroExcel libro = new LibroExcel();
        resumen(libro.hoja("Resumen"), ahora, personas, creditos);
        balance(libro.hoja("Balance"), ahora);
        hojaPersonas(libro.hoja("Por persona"), ahora, personas);
        hojaCreditos(libro.hoja("Créditos"), ahora, creditos);
        ahorros(libro.hoja("Ahorros"), ahora);
        aportes(libro.hoja("Aportes"), ahora);
        libroDiario(libro.hoja("Libro diario"), ahora);

        auditoria.registrar(actorId, "INFORME_EXPORTADO", "REPORTES", "informe", "ESTADO_CAJA", null,
                String.valueOf(personas.size()), null);
        return new ReporteService.Archivo("estado_de_caja_" + ahora.format(ARCHIVO) + ".xlsx", libro.escribir());
    }

    // --- Hojas

    private void resumen(Hoja h, ZonedDateTime ahora, List<PorPersona> personas, List<Credito> creditos) {
        BigDecimal principal = saldoCaja("PRINCIPAL");
        BigDecimal chica = saldoCaja("CHICA");
        BigDecimal dePersonas = suma(personas, PorPersona::seLeDebe);
        BigDecimal reserva = jdbc.sql("SELECT COALESCE(SUM(importe), 0) FROM movimientos_reserva").query(BigDecimal.class).single();
        var enEspera = participacion.enEspera();
        long socios = jdbc.sql("SELECT COUNT(*) FROM socios WHERE estado = 'ACTIVO'").query(Long.class).single();

        encabezadoHoja(h, "Estado de caja", ahora).anchos(46, 18);
        h.fila(texto("Cajas", Estilo.SUBTITULO));
        h.fila(texto("Caja principal"), dinero(principal));
        h.fila(texto("Caja chica"), dinero(chica));
        h.fila(texto("Total en cajas", Estilo.NEGRITA), total(principal.add(chica)));
        h.vacia();
        h.fila(texto("A quién pertenece la caja principal", Estilo.SUBTITULO));
        h.fila(texto("Dinero de las personas (se les debe)"), dinero(dePersonas));
        BigDecimal diferencia = principal.subtract(dePersonas);
        h.fila(texto(diferencia.signum() >= 0 ? "Dinero de Janery" : "Prestado del dinero de las personas"), dinero(diferencia.abs()));
        h.vacia();
        h.fila(texto("Créditos", Estilo.SUBTITULO));
        h.fila(texto("Créditos activos"), numero(creditos.size()));
        h.fila(texto("Capital prestado por cobrar"), dinero(suma(creditos, Credito::capital)));
        h.fila(texto("Cuotas vencidas sin pagar"), numero(creditos.stream().mapToInt(Credito::vencidas).sum()));
        h.fila(texto("Valor vencido sin pagar"), dinero(suma(creditos, Credito::vencido)));
        h.vacia();
        h.fila(texto("Socios", Estilo.SUBTITULO));
        h.fila(texto("Socios activos"), numero(socios));
        h.fila(texto("Aportes recibidos de socios"), dinero(suma(personas, PorPersona::aportes)));
        h.fila(texto("Fondos personales de los socios"), dinero(suma(personas, PorPersona::fondo)));
        h.fila(texto("Fondo de reserva"), dinero(reserva));
        h.fila(texto("Ganancias en espera de reparto"), dinero(enEspera.ganancia()));
        h.fila(texto("Pérdidas en espera de reparto"), dinero(enEspera.perdida()));
    }

    private void balance(Hoja h, ZonedDateTime ahora) {
        var e = contabilidad.estado(null);
        encabezadoHoja(h, "Balance general", ahora).anchos(10, 46, 18);
        h.encabezado("Código", "Cuenta", "Valor");
        seccion(h, e.activoCorriente());
        seccion(h, e.activosFijos());
        h.fila(texto(""), texto("TOTAL ACTIVOS", Estilo.NEGRITA), total(e.totalActivos()));
        h.vacia();
        seccion(h, e.pasivos());
        seccion(h, e.capital());
        h.fila(texto(""), texto("TOTAL PASIVO + CAPITAL", Estilo.NEGRITA), total(e.totalPasivoCapital()));
        h.fila(texto(""), texto("Diferencia (debe ser cero)"), dinero(e.diferencia()));
        h.vacia();
        h.fila(texto("Estado de resultados del mes " + e.periodo(), Estilo.SUBTITULO));
        seccion(h, e.ganancias());
        seccion(h, e.gastos());
        h.fila(texto(""), texto("RESULTADO DEL MES", Estilo.NEGRITA), total(e.resultadoMes()));
        h.vacia();
        h.fila(texto("Indicadores", Estilo.SUBTITULO));
        h.encabezado("", "Indicador", "Valor (%)", "Cómo se calcula");
        for (var i : e.indicadores()) {
            h.fila(texto(""), texto(i.nombre()), i.valor() == null ? texto("Sin datos") : numero(i.valor()), texto(i.explicacion()));
        }
    }

    private static void seccion(Hoja h, ContabilidadService.Seccion s) {
        h.fila(texto(""), texto(s.titulo(), Estilo.SUBTITULO));
        for (var l : s.lineas()) {
            h.fila(texto(l.codigo()), texto(l.detalle() ? "      " + l.nombre() : l.nombre()), dinero(l.valor()));
        }
        h.fila(texto(""), texto("Total " + s.titulo().toLowerCase(), Estilo.NEGRITA), total(s.total()));
    }

    private static void hojaPersonas(Hoja h, ZonedDateTime ahora, List<PorPersona> personas) {
        encabezadoHoja(h, "Dinero de cada persona", ahora).anchos(34, 8, 14, 14, 14, 14, 14, 14, 16, 16, 14, 16);
        h.encabezado("Persona", "Socio", "Apertura", "Cuenta", "Ahorro normal", "Plazo fijo", "Encajes", "Ahorro letras",
                "Total que se le debe", "Créditos por pagar", "Aportes", "Fondo personal").fijarHastaAqui();
        for (PorPersona p : personas) {
            h.fila(texto(p.nombre()), texto(p.socio() ? "Sí" : "No"), dinero(p.apertura()), dinero(p.cuenta()), dinero(p.normales()),
                    dinero(p.plazos()), dinero(p.encajes()), dinero(p.letras()), dinero(p.seLeDebe()), dinero(p.creditos()),
                    dinero(p.aportes()), dinero(p.fondo()));
        }
        h.fila(texto("TOTAL", Estilo.NEGRITA), texto("", Estilo.NEGRITA), total(suma(personas, PorPersona::apertura)),
                total(suma(personas, PorPersona::cuenta)), total(suma(personas, PorPersona::normales)),
                total(suma(personas, PorPersona::plazos)), total(suma(personas, PorPersona::encajes)),
                total(suma(personas, PorPersona::letras)), total(suma(personas, PorPersona::seLeDebe)),
                total(suma(personas, PorPersona::creditos)), total(suma(personas, PorPersona::aportes)),
                total(suma(personas, PorPersona::fondo)));
    }

    private static void hojaCreditos(Hoja h, ZonedDateTime ahora, List<Credito> creditos) {
        encabezadoHoja(h, "Créditos activos", ahora).anchos(11, 34, 20, 14, 12, 14, 9, 14, 16, 10, 14, 13);
        h.encabezado("Código", "Titular", "Tipo", "Modalidad", "Inicio", "Importe", "Plazo (meses)", "Tasa", "Capital por cobrar",
                "Cuotas vencidas", "Valor vencido", "Próxima cuota").fijarHastaAqui();
        for (Credito c : creditos) {
            h.fila(texto(c.codigo()), texto(c.titular()), texto(c.familia()), texto(c.modalidad()), texto(fecha(c.inicio())),
                    dinero(c.importe()), c.plazo() == null ? texto("") : numero(c.plazo()), texto(c.tasa()), dinero(c.capital()),
                    numero(c.vencidas()), dinero(c.vencido()), texto(fecha(c.proxima())));
        }
        h.fila(texto("TOTAL", Estilo.NEGRITA), texto(""), texto(""), texto(""), texto(""), total(suma(creditos, Credito::importe)),
                texto(""), texto(""), total(suma(creditos, Credito::capital)),
                numero(creditos.stream().mapToInt(Credito::vencidas).sum()), total(suma(creditos, Credito::vencido)));
    }

    private void ahorros(Hoja h, ZonedDateTime ahora) {
        encabezadoHoja(h, "Ahorros activos", ahora).anchos(11, 34, 12, 26, 16, 12, 9, 13);
        h.encabezado("Código", "Titular", "Tipo", "Nombre", "Saldo", "Tasa anual (%)", "Plazo (meses)", "Vence").fijarHastaAqui();
        List<BigDecimal> saldos = new ArrayList<>();
        jdbc.sql("""
                SELECT CASE WHEN a.tipo = 'PLAZO_FIJO' THEN 'PF-' ELSE 'AH-' END || lpad(a.id::text, 5, '0') AS codigo,
                       p.apellidos || ' ' || p.nombres AS titular, a.tipo, a.nombre, a.saldo, a.tasa_anual, a.plazo_meses, a.vence_en
                FROM cuentas_ahorro a JOIN personas p ON p.id = a.persona_id
                WHERE a.estado = 'ACTIVA'
                ORDER BY a.tipo, %s, a.id
                """.formatted(OrdenPersonas.ORDEN)).query((r, i) -> {
                    BigDecimal saldo = r.getBigDecimal("saldo");
                    saldos.add(saldo);
                    Object plazo = r.getObject("plazo_meses");
                    BigDecimal tasa = r.getBigDecimal("tasa_anual");
                    h.fila(texto(r.getString("codigo")), texto(r.getString("titular")),
                            texto("PLAZO_FIJO".equals(r.getString("tipo")) ? "Plazo fijo" : "Normal"), texto(r.getString("nombre")),
                            dinero(saldo), tasa == null ? texto("") : numero(tasa), plazo == null ? texto("") : numero((Number) plazo),
                            texto(fecha(fechaDe(r.getObject("vence_en")))));
                    return 0;
                }).list();
        h.fila(texto("TOTAL", Estilo.NEGRITA), texto(""), texto(""), texto(""), total(saldos.stream().reduce(CERO, BigDecimal::add)));
    }

    private void aportes(Hoja h, ZonedDateTime ahora) {
        encabezadoHoja(h, "Aportes de los socios", ahora).anchos(34, 10, 12, 14, 14, 14, 14, 16, 14, 16);
        h.encabezado("Socio", "Estado", "Socio desde", "Aporte inicial", "Aportes mensuales", "Aportes personales", "Caja chica",
                "Total aportado", "Último mes pagado", "Fondo personal").fijarHastaAqui();
        BigDecimal[] totales = { CERO, CERO, CERO, CERO, CERO, CERO };
        jdbc.sql("""
                SELECT p.apellidos || ' ' || p.nombres AS nombre, s.estado, s.desde,
                    (SELECT COALESCE(SUM(a.importe), 0) FROM aportes_socios a WHERE a.socio_id = s.id AND a.tipo = 'INICIAL') AS inicial,
                    (SELECT COALESCE(SUM(a.importe), 0) FROM aportes_socios a WHERE a.socio_id = s.id AND a.tipo = 'OBLIGATORIO') AS mensual,
                    (SELECT COALESCE(SUM(a.importe), 0) FROM aportes_socios a WHERE a.socio_id = s.id AND a.tipo = 'PERSONAL') AS personal,
                    (SELECT COALESCE(SUM(a.importe), 0) FROM aportes_caja_chica a WHERE a.socio_id = s.id) AS chica,
                    (SELECT MAX(a.periodo) FROM aportes_socios a WHERE a.socio_id = s.id AND a.tipo = 'OBLIGATORIO') AS ultimo,
                    (SELECT COALESCE(SUM(v.importe), 0) FROM variaciones_participacion v WHERE v.socio_id = s.id) AS fondo
                FROM socios s JOIN personas p ON p.id = s.persona_id
                ORDER BY s.estado, %s
                """.formatted(OrdenPersonas.ALFABETICO)).query((r, i) -> {
                    BigDecimal[] fila = { r.getBigDecimal("inicial"), r.getBigDecimal("mensual"), r.getBigDecimal("personal"),
                            r.getBigDecimal("chica"), null, r.getBigDecimal("fondo") };
                    fila[4] = fila[0].add(fila[1]).add(fila[2]).add(fila[3]);
                    for (int k = 0; k < totales.length; k++) {
                        totales[k] = totales[k].add(fila[k]);
                    }
                    var desde = r.getTimestamp("desde");
                    h.fila(texto(r.getString("nombre")), texto("ACTIVO".equals(r.getString("estado")) ? "Activo" : "Inactivo"),
                            texto(desde == null ? "" : fecha(desde.toInstant().atZone(ZONA).toLocalDate())), dinero(fila[0]),
                            dinero(fila[1]), dinero(fila[2]), dinero(fila[3]), dinero(fila[4]), texto(r.getString("ultimo")),
                            dinero(fila[5]));
                    return 0;
                }).list();
        h.fila(texto("TOTAL", Estilo.NEGRITA), texto(""), texto(""), total(totales[0]), total(totales[1]), total(totales[2]),
                total(totales[3]), total(totales[4]), texto(""), total(totales[5]));
    }

    /**
     * Libro diario del mes en curso, del más antiguo al más reciente. Cada operación mueve dos o más rubros; en los
     * movimientos de la caja (línea 1.1) se dice si fue ingreso o egreso y cuánto quedó en la caja justo después.
     */
    private void libroDiario(Hoja h, ZonedDateTime ahora) {
        var d = contabilidad.diario(null, 0, MAXIMO_DIARIO);
        encabezadoHoja(h, "Libro diario de " + ahora.format(MES), ahora).anchos(17, 36, 28, 30, 14, 14, 16, 14, 14, 16);
        h.fila(texto("Dinero en la caja ahora"), texto(""), total(d.saldoCaja()));
        h.fila(texto("Ingresó a la caja este mes"), texto(""), dinero(d.entradasCaja()));
        h.fila(texto("Salió de la caja este mes"), texto(""), dinero(d.salidasCaja()));
        h.vacia();
        h.encabezado("Fecha", "Movimiento", "De quién", "Rubro", "Sube", "Baja", "Ingreso o egreso", "Ingreso a caja",
                "Egreso de caja", "Queda en caja").fijarHastaAqui();
        List<ContabilidadService.FilaLibro> filas = new ArrayList<>(d.movimientos().contenido());
        java.util.Collections.reverse(filas);
        for (var f : filas) {
            boolean esCaja = f.cajaDespues() != null;
            boolean ingreso = esCaja && f.sube().signum() > 0;
            boolean egreso = esCaja && f.baja().signum() > 0;
            h.fila(texto(f.fecha().atZone(ZONA).format(FECHA_HORA)), texto(NombresConceptos.de(f.concepto())),
                    texto(f.quien() == null ? "" : f.quien()), texto(f.linea() + " " + f.nombre()),
                    f.sube().signum() > 0 ? dinero(f.sube()) : texto(""), f.baja().signum() > 0 ? dinero(f.baja()) : texto(""),
                    texto(ingreso ? "Ingreso" : egreso ? "Egreso" : "No mueve caja"),
                    ingreso ? dinero(f.sube()) : texto(""), egreso ? dinero(f.baja()) : texto(""),
                    esCaja ? total(f.cajaDespues()) : texto(""));
        }
        if (filas.isEmpty()) {
            h.fila(texto("No hay movimientos en este mes."));
        } else if (d.movimientos().total() > filas.size()) {
            h.fila(texto("Solo se muestran los últimos " + filas.size() + " movimientos del mes."));
        }
    }

    // --- Consultas

    private List<PorPersona> porPersona() {
        return jdbc.sql("""
                SELECT * FROM (
                    SELECT p.id, p.nombres, p.apellidos, p.apellidos || ' ' || p.nombres AS nombre,
                        EXISTS (SELECT 1 FROM socios so WHERE so.persona_id = p.id AND so.estado = 'ACTIVO') AS socio,
                        (SELECT COALESCE(SUM(d.importe_reembolsable), 0) FROM depositos_apertura d
                         JOIN cuentas_principales c ON c.id = d.cuenta_id WHERE c.persona_id = p.id AND d.devuelto_en IS NULL) AS apertura,
                        (SELECT COALESCE(SUM(c.saldo_disponible), 0) FROM cuentas_principales c WHERE c.persona_id = p.id) AS cuenta,
                        (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a WHERE a.persona_id = p.id AND a.tipo = 'NORMAL' AND a.estado = 'ACTIVA') AS normales,
                        (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a WHERE a.persona_id = p.id AND a.tipo = 'PLAZO_FIJO' AND a.estado = 'ACTIVA') AS plazos,
                        (SELECT COALESCE(SUM(e.importe), 0) FROM encajes_credito e
                         JOIN creditos c ON c.id = e.credito_id WHERE c.persona_id = p.id AND e.devuelto_en IS NULL) AS encajes,
                        (SELECT COALESCE(SUM(l.saldo), 0) FROM ahorro_letras_credito l
                         JOIN creditos c ON c.id = l.credito_id WHERE c.persona_id = p.id) AS letras,
                        (SELECT COALESCE(SUM(c.capital_pendiente), 0) FROM creditos c WHERE c.persona_id = p.id AND c.estado = 'ACTIVO') AS creditos,
                        (SELECT COALESCE(SUM(a.importe), 0) FROM aportes_socios a JOIN socios s ON s.id = a.socio_id WHERE s.persona_id = p.id) AS aportes,
                        (SELECT COALESCE(SUM(v.importe), 0) FROM variaciones_participacion v JOIN socios s ON s.id = v.socio_id WHERE s.persona_id = p.id) AS fondo
                    FROM personas p
                ) p WHERE apertura <> 0 OR cuenta <> 0 OR normales <> 0 OR plazos <> 0 OR encajes <> 0 OR letras <> 0
                       OR creditos <> 0 OR aportes <> 0 OR fondo <> 0
                ORDER BY %s
                """.formatted(OrdenPersonas.ORDEN)).query((r, i) -> new PorPersona(r.getString("nombre"), r.getBoolean("socio"),
                r.getBigDecimal("apertura"), r.getBigDecimal("cuenta"), r.getBigDecimal("normales"), r.getBigDecimal("plazos"),
                r.getBigDecimal("encajes"), r.getBigDecimal("letras"), r.getBigDecimal("creditos"), r.getBigDecimal("aportes"),
                r.getBigDecimal("fondo"))).list();
    }

    private List<Credito> creditos(LocalDate hoy) {
        return jdbc.sql("""
                SELECT CASE c.familia WHEN 'LETRAS' THEN 'CL-' WHEN 'EMERGENTE_CLIENTE' THEN 'CE-' WHEN 'EMERGENTE_SOCIO' THEN 'CS-'
                            WHEN 'EMERGENTE_ESTACIONAL' THEN 'CP-' ELSE 'CR-' END || lpad(c.id::text, 5, '0') AS codigo,
                       p.apellidos || ' ' || p.nombres AS titular, c.familia, c.modalidad, c.fecha_inicio, c.importe, c.plazo_meses,
                       c.tasa_interes, c.unidad_tasa, c.capital_pendiente,
                       (SELECT COUNT(*) FROM cuotas_credito q
                        WHERE q.credito_id = c.id AND q.pagada_en IS NULL AND q.vencimiento < :hoy) AS vencidas,
                       (SELECT COALESCE(SUM(q.total - q.capital_pagado - q.interes_pagado - q.desgravamen_pagado - q.ahorro_pagado), 0)
                        FROM cuotas_credito q WHERE q.credito_id = c.id AND q.pagada_en IS NULL AND q.vencimiento < :hoy) AS vencido,
                       (SELECT MIN(q.vencimiento) FROM cuotas_credito q
                        WHERE q.credito_id = c.id AND q.pagada_en IS NULL AND q.vencimiento >= :hoy) AS proxima
                FROM creditos c JOIN personas p ON p.id = c.persona_id
                WHERE c.estado = 'ACTIVO'
                ORDER BY %s, c.id
                """.formatted(OrdenPersonas.ORDEN)).param("hoy", Date.valueOf(hoy)).query((r, i) -> {
                    BigDecimal tasa = r.getBigDecimal("tasa_interes");
                    Object plazo = r.getObject("plazo_meses");
                    return new Credito(r.getString("codigo"), r.getString("titular"),
                            FAMILIAS.getOrDefault(r.getString("familia"), r.getString("familia")), legible(r.getString("modalidad")),
                            fechaDe(r.getObject("fecha_inicio")), r.getBigDecimal("importe"), plazo == null ? null : ((Number) plazo).intValue(),
                            tasa == null ? "" : tasa.stripTrailingZeros().toPlainString() + " "
                                    + UNIDADES.getOrDefault(r.getString("unidad_tasa"), r.getString("unidad_tasa")),
                            r.getBigDecimal("capital_pendiente"), r.getInt("vencidas"), r.getBigDecimal("vencido"),
                            fechaDe(r.getObject("proxima")));
                }).list();
    }

    private BigDecimal saldoCaja(String codigo) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0)
                FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = :codigo
                """).param("codigo", codigo).query(BigDecimal.class).single();
    }

    // --- Ayudas

    private static Hoja encabezadoHoja(Hoja h, String titulo, ZonedDateTime ahora) {
        h.fila(texto("Janery — " + titulo, Estilo.TITULO));
        h.fila(texto("Corte: " + ahora.format(FECHA_HORA) + " (hora de Ecuador)"));
        return h.vacia();
    }

    private static <T> BigDecimal suma(List<T> lista, java.util.function.Function<T, BigDecimal> campo) {
        return lista.stream().map(campo).filter(java.util.Objects::nonNull).reduce(CERO, BigDecimal::add);
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FECHA);
    }

    /** Las columnas de fecha pueden venir como date o timestamp, según la tabla. */
    private static LocalDate fechaDe(Object valor) {
        if (valor instanceof Date d) {
            return d.toLocalDate();
        }
        if (valor instanceof java.sql.Timestamp t) {
            return t.toInstant().atZone(ZONA).toLocalDate();
        }
        return null;
    }

    private static String legible(String codigo) {
        if (codigo == null) {
            return "";
        }
        String t = codigo.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
