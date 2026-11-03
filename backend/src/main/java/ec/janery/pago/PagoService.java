package ec.janery.pago;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.common.Codigos;
import ec.janery.common.NegocioException;
import ec.janery.credito.CreditoService;
import ec.janery.cuenta.ComprobanteService;
import ec.janery.cuenta.DepositoService;
import ec.janery.parametros.ParametroService;
import ec.janery.socio.ParticipacionService.MesAporte;
import lombok.RequiredArgsConstructor;

/**
 * Pagar: la persona arma una lista de lo que paga (cuotas, ahorros, aportes, depósitos) y adjunta el comprobante. Queda
 * como un aviso de depósito con destinos; nada se paga hasta que el personal lo confirma.
 *
 * <p>Decisión del propietario: un cliente solo paga lo suyo; un socio puede pagar también lo de otras personas.
 *
 * <p>Cada consulta es un viaje a la base de datos: todo se lee de una vez para todas las personas del pago, así que el
 * número de consultas no crece con el largo de la lista.
 */
@Service
@RequiredArgsConstructor
public class PagoService {

    public record Persona(Long usuarioId, String nombre) {
    }

    public record CreditoOpcion(Long id, String codigo, String familia, String modalidad, BigDecimal proximaLetra,
            BigDecimal totalPendiente) {
    }

    public record AhorroOpcion(Long id, String codigo, String nombre) {
    }

    /** Lo que se le puede pagar a una persona. */
    public record Opciones(Long usuarioId, String nombre, boolean socio, List<CreditoOpcion> creditos,
            List<AhorroOpcion> ahorros, BigDecimal aporteMensual, BigDecimal aporteCajaChica, List<MesAporte> meses) {
    }

    public record Destino(Long usuarioId, String tipo, Long referenciaId, List<String> periodos, BigDecimal importe) {
    }

    public record Creado(Long id, String codigo, BigDecimal importe) {
    }

    static final String CUENTA = "CUENTA";
    static final String CUOTA_CREDITO = "CUOTA_CREDITO";
    static final String AHORRO = "AHORRO";
    static final String APORTE_OBLIGATORIO = "APORTE_OBLIGATORIO";
    static final String APORTE_PERSONAL = "APORTE_PERSONAL";
    static final String CAJA_CHICA = "CAJA_CHICA";
    private static final int MAX_DESTINOS = 20;
    private static final String PARAM_MENSUAL = "APORTE_OBLIGATORIO";
    private static final String PARAM_CAJA = "APORTE_CAJA_CHICA";

    private final JdbcClient jdbc;
    private final DepositoService depositos;
    private final ComprobanteService comprobantes;
    private final CreditoService creditos;
    private final ParametroService parametros;
    private final Clock reloj;

    /** Lo necesario de cada persona, en una consulta. */
    private record Info(Long usuarioId, Long personaId, String nombre, Long socioId, boolean cuentaActiva) {
        boolean socio() {
            return socioId != null;
        }
    }

    /** Solo nombre: quien paga por otra persona no necesita ver su cédula ni su número de cuenta. */
    @Transactional(readOnly = true)
    public List<Persona> buscar(Long solicitanteId, String texto) {
        exigirSocio(info(List.of(solicitanteId)).get(solicitanteId));
        String limpio = texto == null ? "" : texto.trim().toLowerCase();
        if (limpio.length() < 3) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT p.usuario_id, p.nombres || ' ' || p.apellidos
                FROM personas p JOIN cuentas_principales c ON c.persona_id = p.id AND c.estado = 'ACTIVA'
                WHERE lower(p.nombres || ' ' || p.apellidos) LIKE :filtro OR p.identificacion = :exacto
                ORDER BY p.apellidos, p.nombres LIMIT 10
                """).param("filtro", "%" + limpio + "%").param("exacto", limpio)
                .query((rs, n) -> new Persona(rs.getLong(1), rs.getString(2))).list();
    }

    @Transactional(readOnly = true)
    public Opciones opciones(Long solicitanteId, Long usuarioId) {
        // Sin Set.of: quien consulta lo suyo repite el mismo id.
        Map<Long, Info> infos = info(new LinkedHashSet<>(List.of(solicitanteId, usuarioId)));
        Info solicitante = infos.get(solicitanteId);
        if (!usuarioId.equals(solicitanteId)) {
            exigirSocio(solicitante);
        } else {
            exigirCuenta(solicitante);
        }
        Info p = beneficiario(infos, usuarioId);
        Datos datos = datos(List.of(p));
        return new Opciones(p.usuarioId(), p.nombre(), p.socio(),
                datos.creditos(p).stream().map(d -> new CreditoOpcion(d.id(), d.codigo(), d.familia(), d.modalidad(),
                        d.proximaLetra(), d.totalPendiente())).toList(),
                datos.ahorros(p), p.socio() ? datos.aporteMensual() : null, p.socio() ? datos.aporteCaja() : null,
                p.socio() ? datos.meses(p) : List.of());
    }

    /** Todo o nada: si un destino no es válido, no se registra el pago. El comprobante es obligatorio. */
    @Transactional
    public Creado crear(Long usuarioId, List<Destino> destinos, String medio, String referencia, String clave,
            List<MultipartFile> archivos) {
        List<MultipartFile> adjuntos = archivos == null ? List.of() : archivos.stream().filter(a -> !a.isEmpty()).toList();
        if (adjuntos.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "COMPROBANTE_REQUERIDO", "Adjunte el comprobante del pago");
        }
        if (destinos == null || destinos.isEmpty() || destinos.size() > MAX_DESTINOS) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "DESTINOS_INVALIDOS", "Elija entre 1 y " + MAX_DESTINOS + " pagos");
        }
        Set<Long> usuarios = new LinkedHashSet<>();
        usuarios.add(usuarioId);
        destinos.forEach(d -> usuarios.add(d.usuarioId()));
        Map<Long, Info> infos = info(usuarios);
        Info solicitante = infos.get(usuarioId);
        if (usuarios.size() > 1) {
            exigirSocio(solicitante);
        } else {
            exigirCuenta(solicitante);
        }
        List<Info> beneficiarios = destinos.stream().map(d -> beneficiario(infos, d.usuarioId())).distinct().toList();
        Datos datos = datos(beneficiarios);

        List<Fila> filas = new ArrayList<>();
        Set<String> repetidos = new HashSet<>();
        for (Destino d : destinos) {
            filas.add(validar(d, infos.get(d.usuarioId()), datos, repetidos));
        }
        BigDecimal total = filas.stream().map(Fila::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
        DepositoService.Vista aviso = depositos.solicitar(usuarioId, total, medio, referencia, clave, true);
        if (aviso.destinos().isEmpty()) {
            guardar(aviso.id(), filas);
            comprobantes.subir(usuarioId, ComprobanteService.DEPOSITO, aviso.id(), adjuntos);
        }
        return new Creado(aviso.id(), aviso.codigo(), aviso.importe());
    }

    private record Fila(Long personaId, String tipo, Long referenciaId, List<String> periodos, BigDecimal importe,
            String descripcion) {
    }

    /** Todos los destinos en un solo INSERT. */
    private void guardar(Long solicitudId, List<Fila> filas) {
        StringBuilder sql = new StringBuilder(
                "INSERT INTO destinos_pago (solicitud_id, orden, persona_id, tipo, referencia_id, periodos, importe, descripcion) VALUES ");
        Map<String, Object> params = new HashMap<>();
        params.put("solicitud", solicitudId);
        for (int i = 0; i < filas.size(); i++) {
            Fila f = filas.get(i);
            sql.append(i == 0 ? "" : ", ").append("(:solicitud, :o%1$d, :p%1$d, :t%1$d, :r%1$d, :m%1$d, :i%1$d, :d%1$d)".formatted(i));
            params.put("o" + i, i + 1);
            params.put("p" + i, f.personaId());
            params.put("t" + i, f.tipo());
            params.put("r" + i, f.referenciaId());
            params.put("m" + i, f.periodos().isEmpty() ? null : String.join(",", f.periodos()));
            params.put("i" + i, f.importe());
            params.put("d" + i, recortar(f.descripcion()));
        }
        jdbc.sql(sql.toString()).params(params).update();
    }

    private Fila validar(Destino d, Info p, Datos datos, Set<String> repetidos) {
        String de = " de " + p.nombre();
        return switch (d.tipo()) {
            case CUENTA -> new Fila(p.personaId(), CUENTA, null, List.of(), importe(d), "Depósito a la cuenta principal" + de);
            case CUOTA_CREDITO -> {
                CreditoService.Deuda c = datos.creditos(p).stream().filter(x -> x.id().equals(d.referenciaId())).findFirst()
                        .orElseThrow(() -> NegocioException.conflicto("DESTINO_INVALIDO", "Ese crédito" + de + " no admite pagos"));
                BigDecimal importe = importe(d);
                if (importe.compareTo(c.totalPendiente()) > 0) {
                    throw NegocioException.conflicto("PAGO_EXCEDIDO", "El pago al crédito " + c.codigo() + de
                            + " supera lo que falta (" + c.totalPendiente().toPlainString() + " USD)");
                }
                yield new Fila(p.personaId(), CUOTA_CREDITO, c.id(), List.of(), importe, "Pago al crédito " + c.codigo() + de);
            }
            case AHORRO -> {
                AhorroOpcion a = datos.ahorros(p).stream().filter(x -> x.id().equals(d.referenciaId())).findFirst()
                        .orElseThrow(() -> NegocioException.conflicto("DESTINO_INVALIDO", "Ese ahorro" + de + " no admite depósitos"));
                yield new Fila(p.personaId(), AHORRO, a.id(), List.of(), importe(d), "Depósito al ahorro «" + a.nombre() + "»" + de);
            }
            case APORTE_PERSONAL -> {
                exigirSocioBeneficiario(p);
                yield new Fila(p.personaId(), APORTE_PERSONAL, null, List.of(), importe(d), "Aporte personal" + de);
            }
            case APORTE_OBLIGATORIO, CAJA_CHICA -> {
                exigirSocioBeneficiario(p);
                boolean mensual = APORTE_OBLIGATORIO.equals(d.tipo());
                List<String> meses = meses(d, datos.meses(p), mensual, repetidos);
                BigDecimal porMes = mensual ? datos.aporteMensual() : datos.aporteCaja();
                yield new Fila(p.personaId(), d.tipo(), null, meses, porMes.multiply(BigDecimal.valueOf(meses.size())),
                        (mensual ? "Aporte mensual" : "Caja chica") + de + ": " + String.join(", ", meses));
            }
            default -> throw new NegocioException(HttpStatus.BAD_REQUEST, "DESTINO_INVALIDO", "Tipo de pago desconocido");
        };
    }

    /** Solo meses de este año que aún no estén pagados, y sin repetir dentro del mismo pago. */
    private static List<String> meses(Destino d, List<MesAporte> calendario, boolean mensual, Set<String> repetidos) {
        List<String> pedidos = d.periodos() == null ? List.of() : d.periodos().stream().distinct().sorted().toList();
        if (pedidos.isEmpty() || pedidos.size() > 12) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODOS_INVALIDOS", "Elija entre 1 y 12 meses");
        }
        for (String texto : pedidos) {
            try {
                YearMonth.parse(texto);
            } catch (DateTimeParseException e) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "PERIODOS_INVALIDOS", "Mes inválido: " + texto);
            }
            MesAporte mes = calendario.stream().filter(m -> m.periodo().equals(texto)).findFirst()
                    .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, "PERIODOS_INVALIDOS",
                            "Solo se pueden pagar los meses de este año"));
            if (mensual ? mes.obligatorioPagado() : mes.cajaChicaPagado()) {
                throw NegocioException.conflicto("APORTE_YA_REALIZADO", "El mes " + texto + " ya está pagado");
            }
            if (!repetidos.add(d.tipo() + ":" + d.usuarioId() + ":" + texto)) {
                throw NegocioException.conflicto("DESTINO_REPETIDO", "El mes " + texto + " está dos veces en el pago");
            }
        }
        return pedidos;
    }

    // --- Lecturas en bloque

    /** Créditos, ahorros, meses de aporte y parámetros de todas las personas del pago. */
    private record Datos(Map<Long, List<CreditoService.Deuda>> creditosPorPersona, Map<Long, List<AhorroOpcion>> ahorrosPorPersona,
            Map<Long, List<MesAporte>> mesesPorSocio, BigDecimal aporteMensual, BigDecimal aporteCaja) {

        List<CreditoService.Deuda> creditos(Info p) {
            return creditosPorPersona.getOrDefault(p.personaId(), List.of());
        }

        List<AhorroOpcion> ahorros(Info p) {
            return ahorrosPorPersona.getOrDefault(p.personaId(), List.of());
        }

        List<MesAporte> meses(Info p) {
            return p.socio() ? mesesPorSocio.getOrDefault(p.socioId(), List.of()) : List.of();
        }
    }

    private Datos datos(Collection<Info> personas) {
        List<Long> personaIds = personas.stream().map(Info::personaId).toList();
        Map<Long, List<CreditoService.Deuda>> deudas = creditos.deudasActivas(personaIds).stream()
                .collect(Collectors.groupingBy(CreditoService.Deuda::personaId));
        Map<Long, List<AhorroOpcion>> ahorros = new HashMap<>();
        jdbc.sql("""
                SELECT persona_id, id, nombre FROM cuentas_ahorro
                WHERE persona_id IN (:personas) AND tipo = 'NORMAL' AND estado = 'ACTIVA' ORDER BY id DESC
                """).param("personas", personaIds)
                .query((rs, n) -> ahorros.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(new AhorroOpcion(rs.getLong(2), Codigos.ahorro("NORMAL", rs.getLong(2)), rs.getString(3))))
                .list();
        List<Long> socios = personas.stream().filter(Info::socio).map(Info::socioId).toList();
        if (socios.isEmpty()) {
            return new Datos(deudas, ahorros, Map.of(), null, null);
        }
        Map<String, BigDecimal> valores = parametros.valoresVigentes(List.of(PARAM_MENSUAL, PARAM_CAJA));
        return new Datos(deudas, ahorros, calendarios(socios), dinero(valores.get(PARAM_MENSUAL)), dinero(valores.get(PARAM_CAJA)));
    }

    /** Los doce meses del año de cada socio, con lo ya pagado, en una consulta. */
    private Map<Long, List<MesAporte>> calendarios(List<Long> socios) {
        Map<Long, Set<String>> mensual = new HashMap<>();
        Map<Long, Set<String>> caja = new HashMap<>();
        int anio = YearMonth.now(reloj).getYear();
        jdbc.sql("""
                SELECT socio_id, periodo, true FROM aportes_socios
                WHERE tipo = 'OBLIGATORIO' AND socio_id IN (:socios) AND periodo LIKE :anio
                UNION ALL
                SELECT socio_id, periodo, false FROM aportes_caja_chica WHERE socio_id IN (:socios) AND periodo LIKE :anio
                """).param("socios", socios).param("anio", anio + "-%")
                .query((rs, n) -> (rs.getBoolean(3) ? mensual : caja).computeIfAbsent(rs.getLong(1), k -> new HashSet<>())
                        .add(rs.getString(2)))
                .list();
        Map<Long, List<MesAporte>> resultado = new HashMap<>();
        for (Long socio : socios) {
            Set<String> m = mensual.getOrDefault(socio, Set.of());
            Set<String> c = caja.getOrDefault(socio, Set.of());
            resultado.put(socio, IntStream.rangeClosed(1, 12).mapToObj(i -> YearMonth.of(anio, i).toString())
                    .map(p -> new MesAporte(p, m.contains(p), c.contains(p))).toList());
        }
        return resultado;
    }

    private Map<Long, Info> info(Collection<Long> usuarioIds) {
        Map<Long, Info> resultado = new HashMap<>();
        jdbc.sql("""
                SELECT p.usuario_id, p.id, p.nombres || ' ' || p.apellidos, s.id,
                       EXISTS (SELECT 1 FROM cuentas_principales c WHERE c.persona_id = p.id AND c.estado = 'ACTIVA')
                FROM personas p LEFT JOIN socios s ON s.persona_id = p.id AND s.estado = 'ACTIVO'
                WHERE p.usuario_id IN (:usuarios)
                """).param("usuarios", usuarioIds)
                .query((rs, n) -> resultado.put(rs.getLong(1), new Info(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        (Long) rs.getObject(4, Long.class), rs.getBoolean(5))))
                .list();
        return resultado;
    }

    // --- Reglas

    private static void exigirCuenta(Info solicitante) {
        if (solicitante == null || !solicitante.cuentaActiva()) {
            throw NegocioException.conflicto("CUENTA_INACTIVA", "Necesita una cuenta principal activa");
        }
    }

    /** Decisión del propietario: solo un socio paga lo de otras personas. */
    private static void exigirSocio(Info solicitante) {
        exigirCuenta(solicitante);
        if (!solicitante.socio()) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "SOLO_SOCIOS", "Solo los socios pueden pagar lo de otras personas");
        }
    }

    private static void exigirSocioBeneficiario(Info p) {
        if (!p.socio()) {
            throw NegocioException.conflicto("NO_ES_SOCIO", p.nombre() + " no es socio: no tiene aportes");
        }
    }

    /** Quien recibe el pago necesita una cuenta principal activa: ahí entra el dinero antes de pagar el destino. */
    private static Info beneficiario(Map<Long, Info> infos, Long usuarioId) {
        Info p = infos.get(usuarioId);
        if (p == null || !p.cuentaActiva()) {
            throw NegocioException.conflicto("CUENTA_INACTIVA", "La persona elegida no tiene una cuenta activa");
        }
        return p;
    }

    private static BigDecimal importe(Destino d) {
        if (d.importe() == null || d.importe().signum() <= 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "IMPORTE_REQUERIDO", "Indique el importe de cada pago");
        }
        return d.importe().setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String recortar(String texto) {
        return texto.length() > 200 ? texto.substring(0, 199) + "…" : texto;
    }
}
