package ec.janery.consultas;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.common.OrdenPersonas;
import ec.janery.credito.CreditoService;
import ec.janery.credito.CreditoService.CreditoVista;
import ec.janery.credito.CreditoService.CuotaVista;
import lombok.RequiredArgsConstructor;

/**
 * Las dos hojas que los socios llevaban en Excel: la cartera de crédito (cada persona con todos sus créditos y lo que
 * debe) y los ahorros y aportes de cada persona. Solo lectura y sin datos de contacto; quién puede verlas lo decide el
 * controlador.
 */
@Service
@RequiredArgsConstructor
public class CarteraSociosService {

    /**
     * Un crédito activo. En los créditos a letras, la letra es la próxima por pagar, con su desglose; en los emergentes
     * (cupo) no hay letra y lo que corre es el interés y el desgravamen. cargosPorPagar es ese interés y desgravamen en
     * los emergentes, y la mora en los créditos a letras.
     */
    public record CreditoCartera(Long id, String codigo, String familia, String modalidad, boolean existente,
            BigDecimal saldoInicial, BigDecimal saldoActual, Integer plazoMeses, BigDecimal letra, BigDecimal letraCapital,
            BigDecimal letraInteres, BigDecimal letraDesgravamen, BigDecimal letraAhorro, BigDecimal cargosPorPagar,
            int letrasPagadas, int letras, int letrasAtrasadas, BigDecimal ahorroLetras, BigDecimal encaje,
            BigDecimal totalDeuda) {
    }

    public record PersonaCartera(Long id, String nombre, boolean socio, List<CreditoCartera> creditos,
            BigDecimal saldoActual, BigDecimal cargosPorPagar, BigDecimal totalDeuda, int letrasAtrasadas) {
    }

    /**
     * Lo que cada persona tiene en la caja y, si es socio, sus aportes. fondoPersonal es la suma de los aportes y las
     * ganancias netas, como en «Mis inversiones»; caja chica va aparte.
     */
    public record PersonaAhorros(Long id, String nombre, boolean socio, BigDecimal cuenta, BigDecimal apertura,
            BigDecimal ahorrosNormales, BigDecimal plazosFijos, BigDecimal ahorroLetras, BigDecimal encajes,
            BigDecimal totalAhorros, BigDecimal aporteInicial, BigDecimal aportesMensuales, long mesesAportados,
            BigDecimal aportesPersonales, BigDecimal compraventas, BigDecimal ganancias, BigDecimal fondoPersonal,
            BigDecimal cajaChica) {
    }

    private final JdbcClient jdbc;
    private final CreditoService creditos;

    @Transactional(readOnly = true)
    public List<PersonaCartera> cartera() {
        Map<Long, List<CreditoVista>> porPersona = creditos.activosPorPersona();
        if (porPersona.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT p.id, p.apellidos || ' ' || p.nombres AS nombre,
                       EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO') AS socio
                FROM personas p WHERE p.id IN (:ids)
                """ + " ORDER BY " + OrdenPersonas.ORDEN).param("ids", porPersona.keySet()).query((rs, n) -> {
            List<CreditoCartera> lista = porPersona.get(rs.getLong("id")).stream().map(CarteraSociosService::fila).toList();
            return new PersonaCartera(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("socio"), lista,
                    suma(lista.stream().map(CreditoCartera::saldoActual).toList()),
                    suma(lista.stream().map(CreditoCartera::cargosPorPagar).toList()),
                    suma(lista.stream().map(CreditoCartera::totalDeuda).toList()),
                    lista.stream().mapToInt(CreditoCartera::letrasAtrasadas).sum());
        }).list();
    }

    @Transactional(readOnly = true)
    public List<PersonaAhorros> ahorros() {
        return jdbc.sql("""
                SELECT * FROM (
                    SELECT p.id, p.nombres, p.apellidos, p.apellidos || ' ' || p.nombres AS nombre,
                        EXISTS (SELECT 1 FROM socios s WHERE s.persona_id = p.id AND s.estado = 'ACTIVO') AS socio,
                        EXISTS (SELECT 1 FROM cuentas_principales c WHERE c.persona_id = p.id
                                AND c.estado IN ('ACTIVA', 'PENDIENTE_CIERRE')) AS con_cuenta,
                        (SELECT COALESCE(SUM(c.saldo_disponible), 0) FROM cuentas_principales c WHERE c.persona_id = p.id) AS cuenta,
                        (SELECT COALESCE(SUM(d.importe_reembolsable), 0) FROM depositos_apertura d
                         JOIN cuentas_principales c ON c.id = d.cuenta_id WHERE c.persona_id = p.id AND d.devuelto_en IS NULL) AS apertura,
                        (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a
                         WHERE a.persona_id = p.id AND a.tipo = 'NORMAL' AND a.estado = 'ACTIVA') AS normales,
                        (SELECT COALESCE(SUM(a.saldo), 0) FROM cuentas_ahorro a
                         WHERE a.persona_id = p.id AND a.tipo = 'PLAZO_FIJO' AND a.estado = 'ACTIVA') AS plazos,
                        (SELECT COALESCE(SUM(l.saldo), 0) FROM ahorro_letras_credito l
                         JOIN creditos c ON c.id = l.credito_id WHERE c.persona_id = p.id) AS letras,
                        (SELECT COALESCE(SUM(e.importe), 0) FROM encajes_credito e
                         JOIN creditos c ON c.id = e.credito_id WHERE c.persona_id = p.id AND e.devuelto_en IS NULL) AS encajes,
                        v.inicial, v.mensuales, v.personales, v.compraventas, v.ganancias, v.fondo,
                        (SELECT COUNT(*) FROM aportes_socios a JOIN socios s ON s.id = a.socio_id
                         WHERE s.persona_id = p.id AND a.tipo = 'OBLIGATORIO') AS meses,
                        (SELECT COALESCE(SUM(a.importe), 0) FROM aportes_caja_chica a JOIN socios s ON s.id = a.socio_id
                         WHERE s.persona_id = p.id) AS caja_chica
                    FROM personas p
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(v.importe) FILTER (WHERE v.tipo = 'APORTE_INICIAL'), 0) AS inicial,
                               COALESCE(SUM(v.importe) FILTER (WHERE v.tipo = 'APORTE_OBLIGATORIO'), 0) AS mensuales,
                               COALESCE(SUM(v.importe) FILTER (WHERE v.tipo = 'APORTE_PERSONAL'), 0) AS personales,
                               COALESCE(SUM(v.importe) FILTER (WHERE v.tipo IN ('APORTE_COMPRA', 'APORTE_VENTA')), 0) AS compraventas,
                               COALESCE(SUM(v.importe) FILTER (WHERE v.tipo NOT LIKE 'APORTE%'), 0) AS ganancias,
                               COALESCE(SUM(v.importe), 0) AS fondo
                        FROM variaciones_participacion v JOIN socios s ON s.id = v.socio_id WHERE s.persona_id = p.id
                    ) v ON true
                ) p
                WHERE socio OR con_cuenta OR cuenta + apertura + normales + plazos + letras + encajes + fondo + caja_chica <> 0
                """ + " ORDER BY " + OrdenPersonas.ORDEN).query((rs, n) -> {
            BigDecimal normales = rs.getBigDecimal("normales");
            BigDecimal plazos = rs.getBigDecimal("plazos");
            BigDecimal letras = rs.getBigDecimal("letras");
            BigDecimal encajes = rs.getBigDecimal("encajes");
            return new PersonaAhorros(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("socio"), rs.getBigDecimal("cuenta"),
                    rs.getBigDecimal("apertura"), normales, plazos, letras, encajes, normales.add(plazos).add(letras).add(encajes),
                    rs.getBigDecimal("inicial"), rs.getBigDecimal("mensuales"), rs.getLong("meses"), rs.getBigDecimal("personales"),
                    rs.getBigDecimal("compraventas"), rs.getBigDecimal("ganancias"), rs.getBigDecimal("fondo"),
                    rs.getBigDecimal("caja_chica"));
        }).list();
    }

    private static CreditoCartera fila(CreditoVista c) {
        boolean linea = c.cuotas().isEmpty();
        CuotaVista proxima = c.cuotas().stream().filter(q -> !c.cuotasPagadas().contains(q.numero())).findFirst().orElse(null);
        BigDecimal cargos = linea ? c.interes().add(c.desgravamen()) : c.moraPendiente();
        return new CreditoCartera(c.id(), c.codigo(), c.familia(), c.modalidad(), c.existente(), c.importe(), c.capitalPendiente(),
                c.plazoMeses(), proxima == null ? null : proxima.total(), proxima == null ? null : proxima.capital(),
                proxima == null ? null : proxima.interes(), proxima == null ? null : proxima.fondoDesgravamen(),
                proxima == null ? null : proxima.ahorroLetras(), cargos, c.cuotasPagadas().size(), c.cuotas().size(),
                c.letrasAtrasadas(), c.ahorroLetras(), c.encajeDevuelto() ? null : c.encaje(), c.totalPendiente());
    }

    private static BigDecimal suma(List<BigDecimal> importes) {
        return importes.stream().filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
