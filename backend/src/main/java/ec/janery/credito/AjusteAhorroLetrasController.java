package ec.janery.credito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.Codigos;
import ec.janery.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * TEMPORAL (22/11/2026), a pedido del propietario: corrige el ahorro letras de los créditos anteriores a Janery. Al
 * registrarlos se sumó el ahorro de todas las letras pagadas, pero muchas se cobraron sin él. Se borra junto con
 * ajuste-ahorro-letras.ts cuando se terminen de corregir.
 *
 * Solo cambia lo que entró al registrar el crédito (el pago inicial de AHORRO_LETRAS, sin letra), como si se hubiera
 * registrado bien desde el principio; lo ahorrado después con pagos dentro de Janery no se toca.
 */
@RestController
@RequestMapping("/api/v1/admin/creditos/existentes")
@PreAuthorize("hasAuthority('SCOPE_REGISTRAR_CREDITOS_EXISTENTES')")
@RequiredArgsConstructor
public class AjusteAhorroLetrasController {

    public record AhorroCredito(Long id, String codigo, BigDecimal importe, String estado, BigDecimal ahorroActual,
            BigDecimal ahorroInicial, boolean liberado) {
    }

    public record AjusteRequest(@NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal ahorro,
            @Size(max = 300) String motivo) {
    }

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    @GetMapping("/personas/{usuarioId}/ahorro-letras")
    @Transactional(readOnly = true)
    public List<AhorroCredito> listar(@PathVariable Long usuarioId) {
        return jdbc.query("""
                SELECT c.id, c.familia, c.importe, c.estado, a.saldo, a.liberado_en IS NOT NULL AS liberado,
                       COALESCE((SELECT d.importe FROM pagos_credito p JOIN detalles_pago d ON d.pago_id = p.id
                                 WHERE p.credito_id = c.id AND p.cuota_id IS NULL AND p.creado_en = c.creado_en
                                   AND d.concepto = 'AHORRO_LETRAS'), 0) AS inicial
                FROM creditos c JOIN personas pe ON pe.id = c.persona_id JOIN ahorro_letras_credito a ON a.credito_id = c.id
                WHERE pe.usuario_id = ? AND c.existente AND c.familia = 'LETRAS'
                ORDER BY c.id""",
                (rs, i) -> new AhorroCredito(rs.getLong("id"), Codigos.credito(rs.getString("familia"), rs.getLong("id")),
                        rs.getBigDecimal("importe"), rs.getString("estado"), rs.getBigDecimal("saldo"),
                        rs.getBigDecimal("inicial"), rs.getBoolean("liberado")),
                usuarioId);
    }

    @PutMapping("/creditos/{creditoId}/ahorro-letras")
    @Transactional
    public AhorroCredito corregir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long creditoId, @Valid @RequestBody AjusteRequest r) {
        return ajustar(actor(jwt), creditoId, r.ahorro(), r.motivo());
    }

    /** También lo usa el reinicio de datos, para dejar el ahorro letras real de cada crédito. */
    @Transactional
    public AhorroCredito ajustar(Long actorId, Long creditoId, BigDecimal ahorro, String motivo) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT a.saldo, a.liberado_en IS NOT NULL AS liberado, pe.usuario_id
                FROM ahorro_letras_credito a JOIN creditos c ON c.id = a.credito_id JOIN personas pe ON pe.id = c.persona_id
                WHERE a.credito_id = ? AND c.existente AND c.familia = 'LETRAS'
                FOR UPDATE OF a""", creditoId);
        if (filas.isEmpty()) {
            throw NegocioException.conflicto("NO_ES_CREDITO_ANTERIOR", "Solo se corrigen créditos a letras registrados como anteriores a Janery");
        }
        if ((Boolean) filas.get(0).get("liberado")) {
            throw NegocioException.conflicto("AHORRO_LIBERADO", "Ese ahorro letras ya pasó a la cuenta principal: no se puede corregir aquí");
        }
        BigDecimal actual = (BigDecimal) filas.get(0).get("saldo");
        BigDecimal nuevo = ahorro.setScale(2);
        BigDecimal diferencia = nuevo.subtract(actual);

        List<Map<String, Object>> pagos = jdbc.queryForList("""
                SELECT p.id AS pago, p.operacion_id, d.id AS detalle, d.importe
                FROM pagos_credito p JOIN detalles_pago d ON d.pago_id = p.id JOIN creditos c ON c.id = p.credito_id
                WHERE p.credito_id = ? AND p.cuota_id IS NULL AND p.creado_en = c.creado_en AND d.concepto = 'AHORRO_LETRAS'""",
                creditoId);
        BigDecimal inicial = pagos.isEmpty() ? BigDecimal.ZERO.setScale(2) : (BigDecimal) pagos.get(0).get("importe");
        BigDecimal nuevoInicial = inicial.add(diferencia);
        if (nuevoInicial.signum() < 0) {
            throw NegocioException.conflicto("AHORRO_MENOR_QUE_LO_PAGADO_EN_JANERY", "Desde que se registró, pagó "
                    + actual.subtract(inicial).toPlainString() + " USD de ahorro letras en Janery: el ahorro no puede ser menor");
        }

        if (diferencia.signum() != 0) {
            if (pagos.isEmpty()) {
                // No tenía ahorro inicial: se crea como lo habría creado el registro.
                Long operacion = jdbc.queryForObject("""
                        INSERT INTO operaciones_financieras (tipo, importe, metodo_pago, actor_id, creado_en)
                        SELECT 'PAGO_CREDITO', ?, 'INTERNO', ?, creado_en FROM creditos WHERE id = ? RETURNING id""",
                        Long.class, nuevoInicial, actorId, creditoId);
                Long pago = jdbc.queryForObject("""
                        INSERT INTO pagos_credito (credito_id, cuota_id, operacion_id, importe, creado_en)
                        SELECT id, NULL, ?, ?, creado_en FROM creditos WHERE id = ? RETURNING id""",
                        Long.class, operacion, nuevoInicial, creditoId);
                jdbc.update("INSERT INTO detalles_pago (pago_id, concepto, importe) VALUES (?, 'AHORRO_LETRAS', ?)", pago, nuevoInicial);
            } else {
                Map<String, Object> p = pagos.get(0);
                if (nuevoInicial.signum() == 0) {
                    jdbc.update("DELETE FROM detalles_pago WHERE id = ?", p.get("detalle"));
                    jdbc.update("DELETE FROM pagos_credito WHERE id = ?", p.get("pago"));
                    jdbc.update("DELETE FROM operaciones_financieras WHERE id = ?", p.get("operacion_id"));
                } else {
                    jdbc.update("UPDATE detalles_pago SET importe = ? WHERE id = ?", nuevoInicial, p.get("detalle"));
                    jdbc.update("UPDATE pagos_credito SET importe = ? WHERE id = ?", nuevoInicial, p.get("pago"));
                    jdbc.update("UPDATE operaciones_financieras SET importe = ? WHERE id = ?", nuevoInicial, p.get("operacion_id"));
                }
            }
            jdbc.update("UPDATE ahorro_letras_credito SET saldo = ? WHERE credito_id = ?", nuevo, creditoId);
            auditoria.registrar(actorId, "AHORRO_LETRAS_CORREGIDO", "CREDITO", "credito", creditoId, actual.toPlainString(),
                    nuevo.toPlainString(), motivo == null || motivo.isBlank() ? null : motivo.trim());
        }
        Long usuarioId = ((Number) filas.get(0).get("usuario_id")).longValue();
        return listar(usuarioId).stream().filter(a -> a.id().equals(creditoId)).findFirst().orElseThrow();
    }

    private static Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
