-- Decisión del propietario (02/11/2026): al inicio de cada crédito a letras se cobra un porcentaje del importe para
-- suministros (papelería y gastos de operación). Se paga como el encaje, pero no se devuelve: es ganancia (línea 4.8).
ALTER TABLE creditos ADD COLUMN porcentaje_suministros NUMERIC(9, 6);

CREATE TABLE suministros_credito (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id   BIGINT         NOT NULL UNIQUE REFERENCES creditos (id),
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    creado_en    TIMESTAMPTZ    NOT NULL
);

WITH nuevos (codigo, nombre, descripcion, unidad, valor) AS (VALUES
    ('LETRAS_SUMINISTROS_NORMAL', 'Suministros normal', 'Porcentaje del importe, cobrado al inicio y no devuelto', 'PCT', 1.5),
    ('LETRAS_SUMINISTROS_INVERSION', 'Suministros inversión', 'Porcentaje del importe, cobrado al inicio y no devuelto', 'PCT', 0.75),
    ('LETRAS_SUMINISTROS_ESTUDIO', 'Suministros estudio', 'Porcentaje del importe, cobrado al inicio y no devuelto', 'PCT', 0.75),
    ('LETRAS_SUMINISTROS_PERSONALIZADO', 'Suministros personalizado', 'Porcentaje del importe, cobrado al inicio y no devuelto', 'PCT', 1.5)
), parametros AS (
    INSERT INTO parametros_sistema (codigo, nombre, descripcion, unidad)
    SELECT codigo, nombre, descripcion, unidad FROM nuevos
    RETURNING id, codigo
), valores AS (
    INSERT INTO parametro_valores (parametro_id, valor, vigente_desde, motivo)
    SELECT p.id, n.valor, now(), 'Definido por el propietario el 02/11/2026'
    FROM parametros p JOIN nuevos n ON n.codigo = p.codigo
    RETURNING parametro_id, valor, vigente_desde, motivo
)
INSERT INTO historial_parametros (parametro_id, valor_nuevo, vigente_desde, motivo)
SELECT parametro_id, valor, vigente_desde, motivo FROM valores;

-- El libro de V17, más los suministros cobrados.
CREATE OR REPLACE VIEW libro_contable AS
    SELECT m.creado_en AS fecha, m.operacion_id, '1.1'::varchar AS linea,
           CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END AS importe, m.concepto::varchar AS concepto
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo IN ('PRINCIPAL', 'FONDO_RESERVA')
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '3.5', CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, 'RESERVA'
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA'
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '3.7', CASE WHEN m.sentido = 'ENTRADA' THEN -m.importe ELSE m.importe END, 'RESERVA'
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA'
    UNION ALL
    SELECT o.creado_en, d.operacion_id, '4.7', d.importe_ingreso, 'INGRESO_APERTURA'
    FROM depositos_apertura d JOIN operaciones_financieras o ON o.id = d.operacion_id WHERE d.importe_ingreso > 0
    UNION ALL
    SELECT o.creado_en, d.operacion_id, '2.3', d.importe_reembolsable, 'DEPOSITO_REEMBOLSABLE'
    FROM depositos_apertura d JOIN operaciones_financieras o ON o.id = d.operacion_id WHERE d.importe_reembolsable > 0
    UNION ALL
    SELECT d.devuelto_en, d.operacion_id, '2.3', -d.importe_reembolsable, 'DEVOLUCION_DEPOSITO'
    FROM depositos_apertura d WHERE d.devuelto_en IS NOT NULL AND d.importe_reembolsable > 0
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '2.1', CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, m.concepto
    FROM movimientos_cuenta m
    UNION ALL
    SELECT s.creado_en, s.operacion_id, '2.1', s.importe + s.costo, 'RETIRO_APARTADO' FROM solicitudes_retiro s
    UNION ALL
    SELECT s.resuelta_en, s.operacion_id, '2.1', -(s.importe + s.costo), 'RETIRO_RESUELTO' FROM solicitudes_retiro s WHERE s.resuelta_en IS NOT NULL
    UNION ALL
    SELECT m.creado_en, m.operacion_id, CASE WHEN a.tipo = 'NORMAL' THEN '2.1' ELSE '2.2' END,
           CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, m.concepto
    FROM movimientos_ahorro m JOIN cuentas_ahorro a ON a.id = m.cuenta_ahorro_id
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '5.1', m.importe, 'INTERES_AHORRO'
    FROM movimientos_ahorro m WHERE m.concepto = 'INTERES' AND m.sentido = 'ENTRADA'
    UNION ALL
    SELECT d.creado_en, d.operacion_id, '1.4', d.importe, 'DESEMBOLSO' FROM desembolsos_credito d
    UNION ALL
    SELECT p.creado_en, p.operacion_id,
           CASE dp.concepto WHEN 'CAPITAL' THEN '1.4' WHEN 'INTERES' THEN '4.1' WHEN 'FONDO_DESGRAVAMEN' THEN '2.5' WHEN 'MORA' THEN '4.2' ELSE '2.3' END,
           CASE WHEN dp.concepto = 'CAPITAL' THEN -dp.importe ELSE dp.importe END, 'PAGO_' || dp.concepto
    FROM detalles_pago dp JOIN pagos_credito p ON p.id = dp.pago_id
    UNION ALL
    SELECT e.pagado_en, e.operacion_id, '2.3', e.importe, 'ENCAJE' FROM encajes_credito e WHERE e.importe > 0
    UNION ALL
    SELECT e.devuelto_en, e.operacion_id, '2.3', -e.importe, 'DEVOLUCION_ENCAJE' FROM encajes_credito e
    WHERE e.devuelto_en IS NOT NULL AND e.importe > 0
    UNION ALL
    SELECT m.creado_en, NULL, '2.3', -m.importe, 'LIBERACION_AHORRO_LETRAS' FROM movimientos_ahorro_letras m WHERE m.tipo = 'LIBERACION'
    UNION ALL
    SELECT v.creado_en, a.operacion_id,
           CASE WHEN v.tipo = 'APORTE_INICIAL' THEN '3.1' WHEN v.tipo LIKE 'APORTE_%' THEN '3.2' ELSE '3.7' END, v.importe, v.tipo
    FROM variaciones_participacion v LEFT JOIN aportes_socios a ON a.id = v.aporte_id
    WHERE v.tipo IN ('APORTE_INICIAL', 'APORTE_OBLIGATORIO', 'APORTE_PERSONAL', 'APORTE_COMPRA', 'APORTE_VENTA',
                     'RETIRO_GANANCIA', 'DESCUENTO_OBLIGACION')
    UNION ALL
    SELECT a.creado_en, a.operacion_id, a.linea, a.importe, 'MANUAL: ' || a.descripcion FROM asientos_manuales a
    UNION ALL
    SELECT a.creado_en, a.operacion_id, '1.99', -a.importe, 'MANUAL: ' || a.descripcion FROM asientos_manuales a WHERE a.linea = '5.9'
    UNION ALL
    SELECT s.creado_en, s.operacion_id, '4.8', s.importe, 'SUMINISTROS' FROM suministros_credito s;
