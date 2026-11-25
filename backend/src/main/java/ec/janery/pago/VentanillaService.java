package ec.janery.pago;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ec.janery.ahorro.AhorroService;
import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.Codigos;
import ec.janery.common.NegocioException;
import ec.janery.common.OrdenPersonas;
import ec.janery.cuenta.DepositoService;
import ec.janery.cuenta.RetiroService;
import lombok.RequiredArgsConstructor;

/**
 * Ventanilla de tesorería. Decisión del propietario (24/11/2026): cada persona puede hacer sus movimientos, pero lo más
 * común es que se lo pida al tesorero, que cobra y entrega por cualquiera, y por varias personas en un mismo cobro.
 *
 * <p>Todo se aplica al momento: el tesorero ya recibió o entregó el dinero. Un cobro puede pagarse con dinero (efectivo o
 * transferencia, que entra a la caja) o con el saldo de la cuenta principal o de un ahorro normal de quien paga, sin que
 * entre dinero. Un retiro puede salir de la cuenta principal o directo de un ahorro normal.
 */
@Service
@RequiredArgsConstructor
public class VentanillaService {

    public static final String EFECTIVO = "EFECTIVO";
    public static final String TRANSFERENCIA = "TRANSFERENCIA";
    public static final String CUENTA = "CUENTA";
    public static final String AHORRO = "AHORRO";

    /** El personal sí ve la cédula y el número de cuenta: los necesita para reconocer a quien atiende. */
    public record Persona(Long usuarioId, String nombre, String identificacion, String numeroCuenta, boolean socio) {
    }

    public record AhorroSaldo(Long id, String codigo, String nombre, BigDecimal saldo) {
    }

    /** Lo que se le puede cobrar a la persona y de dónde puede salir el dinero. */
    public record Ficha(Persona persona, BigDecimal saldoCuenta, List<AhorroSaldo> ahorros, PagoService.Opciones opciones) {
    }

    /** codigo: el del pago (DP) cuando entró dinero a la caja; null si se pagó con saldo. */
    public record Cobro(Long id, String codigo, BigDecimal importe, List<String> detalle) {
    }

    private final JdbcClient jdbc;
    private final PagoService pagos;
    private final EjecucionPagos ejecucion;
    private final AhorroService ahorros;
    private final RetiroService retiros;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<Persona> buscar(String texto) {
        String limpio = texto == null ? "" : texto.trim().toLowerCase();
        if (limpio.length() < 3) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT p.usuario_id, p.apellidos || ' ' || p.nombres, p.identificacion, c.numero,
                       EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO')
                FROM personas p JOIN cuentas_principales c ON c.persona_id = p.id AND c.estado = 'ACTIVA'
                """ + " WHERE " + OrdenPersonas.NOMBRE_BUSCABLE + " LIKE :filtro OR p.identificacion LIKE :inicio"
                + " OR lower(c.numero) = :exacto ORDER BY " + OrdenPersonas.ORDEN + " LIMIT 15")
                .param("filtro", "%" + limpio + "%").param("inicio", limpio + "%").param("exacto", limpio)
                .query((rs, n) -> new Persona(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .list();
    }

    @Transactional(readOnly = true)
    public Ficha ficha(Long usuarioId) {
        PagoService.Opciones opciones = pagos.opcionesVentanilla(usuarioId);
        record Cuenta(String identificacion, String numero, BigDecimal saldo, Long personaId) {
        }
        Cuenta c = jdbc.sql("""
                SELECT p.identificacion, c.numero, c.saldo_disponible, p.id
                FROM personas p JOIN cuentas_principales c ON c.persona_id = p.id AND c.estado = 'ACTIVA'
                WHERE p.usuario_id = :usuario
                """).param("usuario", usuarioId)
                .query((rs, n) -> new Cuenta(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getLong(4))).single();
        List<AhorroSaldo> normales = jdbc.sql("""
                SELECT id, nombre, saldo FROM cuentas_ahorro
                WHERE persona_id = :persona AND tipo = 'NORMAL' AND estado = 'ACTIVA' ORDER BY id DESC
                """).param("persona", c.personaId())
                .query((rs, n) -> new AhorroSaldo(rs.getLong(1), Codigos.ahorro("NORMAL", rs.getLong(1)), rs.getString(2),
                        rs.getBigDecimal(3)))
                .list();
        return new Ficha(new Persona(usuarioId, opciones.nombre(), c.identificacion(), c.numero(), opciones.socio()),
                c.saldo(), normales, opciones);
    }

    /**
     * Cobra todo de una vez. Con dinero, a nombre de quien lo entregó, y puede incluir lo de otras personas. Con el saldo
     * de la cuenta o de un ahorro, solo lo de esa misma persona: no se pasa dinero de una cuenta a otra.
     */
    @Transactional
    public Cobro cobrar(Long actorId, Long pagadorId, List<PagoService.Destino> destinos, String origen, Long ahorroId,
            String referencia, String clave, List<MultipartFile> archivos) {
        if (EFECTIVO.equals(origen) || TRANSFERENCIA.equals(origen)) {
            if (TRANSFERENCIA.equals(origen) && (referencia == null || referencia.isBlank())) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "REFERENCIA_REQUERIDA", "Indique el número de la transferencia");
            }
            DepositoService.Vista v = pagos.cobrarEnVentanilla(actorId, pagadorId, destinos, origen,
                    referencia == null || referencia.isBlank() ? null : referencia.trim(), clave, archivos);
            return new Cobro(v.id(), v.codigo(), v.importe(),
                    v.destinos().stream().map(d -> d.descripcion() + ": " + d.importe().toPlainString() + " USD").toList());
        }
        List<PagoService.Fila> filas = pagos.validarVentanilla(pagadorId, destinos);
        if (filas.stream().anyMatch(f -> !pagadorId.equals(f.usuarioId()))) {
            throw NegocioException.conflicto("SOLO_PROPIO",
                    "Con el saldo de una cuenta o de un ahorro solo se paga lo de su dueño. Lo de otras personas se cobra en efectivo o por transferencia.");
        }
        BigDecimal total = PagoService.total(filas);
        if (CUENTA.equals(origen)) {
            if (filas.stream().anyMatch(f -> PagoService.CUENTA.equals(f.tipo()))) {
                throw NegocioException.conflicto("DESTINO_INVALIDO", "No se deposita a la cuenta principal con su propio saldo");
            }
        } else if (AHORRO.equals(origen)) {
            exigirAhorroNormal(pagadorId, ahorroId);
            if (filas.stream().anyMatch(f -> PagoService.AHORRO.equals(f.tipo()) && ahorroId.equals(f.referenciaId()))) {
                throw NegocioException.conflicto("DESTINO_INVALIDO", "No se deposita a un ahorro con su propio saldo");
            }
            // El ahorro pasa por la cuenta principal, como cuando la persona retira de él: así queda en sus movimientos.
            ahorros.retirar(pagadorId, ahorroId, total, clave + ":ahorro");
        } else {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "ORIGEN_INVALIDO", "Origen de pago desconocido");
        }
        for (int i = 0; i < filas.size(); i++) {
            PagoService.Fila f = filas.get(i);
            try {
                ejecucion.pagarDestino(f.usuarioId(), f.tipo(), f.referenciaId(), f.periodos(), f.importe(), clave + ":" + (i + 1));
            } catch (NegocioException ex) {
                throw new NegocioException(ex.getStatus(), ex.getCodigo(), f.descripcion() + ": " + ex.getMessage());
            }
        }
        auditoria.registrar(actorId, "COBRO_VENTANILLA", "CUENTA", "usuario", pagadorId, null, total.toPlainString(), origen);
        return new Cobro(null, null, total, detalle(filas));
    }

    /** Entrega dinero en efectivo o por transferencia. Sale de la cuenta principal o directo de un ahorro normal. */
    @Transactional
    public RetiroService.Vista entregar(Long actorId, Long usuarioId, String origen, Long ahorroId, BigDecimal importe,
            String medio, RetiroService.DatosBanco banco, String referencia, String clave) {
        if (AHORRO.equals(origen)) {
            exigirAhorroNormal(usuarioId, ahorroId);
            // El costo de la transferencia también sale del ahorro, para no exigir saldo en la cuenta principal.
            BigDecimal costo = retiros.costo(medio, banco == null ? null : banco.banco());
            ahorros.retirar(usuarioId, ahorroId, importe.add(costo), clave + ":ahorro");
        } else if (!CUENTA.equals(origen)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "ORIGEN_INVALIDO", "Origen del retiro desconocido");
        }
        return retiros.entregarEnVentanilla(actorId, usuarioId, importe, medio, banco, referencia, clave);
    }

    /** Un plazo fijo no se toca antes de vencer y, al vencer, se liquida entero desde Ahorros. */
    private void exigirAhorroNormal(Long usuarioId, Long ahorroId) {
        if (ahorroId == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "AHORRO_REQUERIDO", "Elija de qué ahorro sale el dinero");
        }
        String tipo = jdbc.sql("""
                SELECT a.tipo FROM cuentas_ahorro a JOIN personas p ON p.id = a.persona_id
                WHERE a.id = :ahorro AND p.usuario_id = :usuario AND a.estado = 'ACTIVA'
                """).param("ahorro", ahorroId).param("usuario", usuarioId).query(String.class).optional()
                .orElseThrow(() -> NegocioException.noEncontrado("Ahorro no encontrado"));
        if (!"NORMAL".equals(tipo)) {
            throw NegocioException.conflicto("AHORRO_NO_DISPONIBLE", "Solo se usa un ahorro normal; el plazo fijo se liquida al vencer");
        }
    }

    private static List<String> detalle(List<PagoService.Fila> filas) {
        return filas.stream().map(f -> f.descripcion() + ": " + f.importe().toPlainString() + " USD").toList();
    }
}
