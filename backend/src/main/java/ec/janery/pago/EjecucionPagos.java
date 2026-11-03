package ec.janery.pago;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.ahorro.AhorroService;
import ec.janery.common.NegocioException;
import ec.janery.credito.CreditoService;
import ec.janery.cuenta.CuentaService;
import ec.janery.cuenta.EjecutorPagos;
import ec.janery.socio.ParticipacionService;
import lombok.RequiredArgsConstructor;

/**
 * Al confirmarse un pago, el dinero entra a la cuenta principal de cada beneficiario (y a la caja principal), y de ahí
 * se paga cada destino como si la persona lo hubiera pagado. Todo o nada: si un destino falla, no se confirma nada.
 */
@Component
@RequiredArgsConstructor
class EjecucionPagos implements EjecutorPagos {

    private record Fila(int orden, Long personaId, Long usuarioId, String tipo, Long referenciaId, String periodos,
            BigDecimal importe, String descripcion) {
    }

    private final JdbcClient jdbc;
    private final CuentaService cuentas;
    private final CreditoService creditos;
    private final AhorroService ahorros;
    private final ParticipacionService participacion;

    @Override
    @Transactional
    public Long ejecutar(Long actorId, Long solicitudId, String medio, String referencia) {
        List<Fila> filas = jdbc.sql("""
                SELECT d.orden, d.persona_id, p.usuario_id, d.tipo, d.referencia_id, d.periodos, d.importe, d.descripcion
                FROM destinos_pago d JOIN personas p ON p.id = d.persona_id
                WHERE d.solicitud_id = :id ORDER BY d.orden
                """).param("id", solicitudId)
                .query((rs, n) -> new Fila(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                        (Long) rs.getObject(5, Long.class), rs.getString(6), rs.getBigDecimal(7), rs.getString(8)))
                .list();
        if (filas.isEmpty()) {
            throw new IllegalStateException("Pago sin destinos: " + solicitudId);
        }
        Map<Long, BigDecimal> porPersona = new LinkedHashMap<>();
        filas.forEach(f -> porPersona.merge(f.personaId(), f.importe(), BigDecimal::add));
        Long primera = null;
        for (Map.Entry<Long, BigDecimal> e : porPersona.entrySet()) {
            Long cuenta = cuentas.cuentaActivaBloqueada(e.getKey()).getId();
            Long operacion = cuentas.depositar(actorId, cuenta, e.getValue(), medio, referencia,
                    "pago-" + solicitudId + "-persona-" + e.getKey());
            primera = primera == null ? operacion : primera;
        }
        for (Fila f : filas) {
            try {
                pagar(f, "pago-" + solicitudId + "-" + f.orden());
            } catch (NegocioException ex) {
                // El personal debe saber qué destino falló para decidir si rechaza el pago.
                throw new NegocioException(ex.getStatus(), ex.getCodigo(), f.descripcion() + ": " + ex.getMessage());
            }
        }
        return primera;
    }

    private void pagar(Fila f, String clave) {
        switch (f.tipo()) {
            case PagoService.CUENTA -> {
                // El dinero ya quedó en su cuenta principal.
            }
            case PagoService.CUOTA_CREDITO -> creditos.pagar(f.usuarioId(), f.referenciaId(), f.importe(), false, false, clave);
            case PagoService.AHORRO -> ahorros.depositar(f.usuarioId(), f.referenciaId(), f.importe(), clave);
            case PagoService.APORTE_PERSONAL -> participacion.aportar(f.usuarioId(), "PERSONAL", f.importe(), null, clave);
            case PagoService.APORTE_OBLIGATORIO -> participacion.pagarPeriodos(f.usuarioId(), "OBLIGATORIO", meses(f), clave);
            case PagoService.CAJA_CHICA -> participacion.pagarPeriodos(f.usuarioId(), "CAJA_CHICA", meses(f), clave);
            default -> throw new IllegalStateException("Destino desconocido: " + f.tipo());
        }
    }

    private static List<String> meses(Fila f) {
        return Arrays.asList(f.periodos().split(","));
    }
}
