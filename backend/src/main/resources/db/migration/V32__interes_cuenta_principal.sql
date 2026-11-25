-- Decisión del propietario (24/11/2026): la cuenta principal gana interés igual que un ahorro normal, para que nadie
-- tenga que pasar dinero a un ahorro solo para ganarlo. Tiene su propia tasa, editable aparte de la del ahorro normal.
WITH nuevos (codigo, nombre, descripcion, unidad, valor, motivo) AS (VALUES
    ('PRINCIPAL_TASA', 'Cuenta principal: tasa',
     'Interés anual de la cuenta principal; se calcula cada día (anual / 365) sobre el saldo y se libera cada día 1',
     'PCT_ANUAL', 5, 'Definido por el propietario: igual que el ahorro normal')
), parametros AS (
    INSERT INTO parametros_sistema (codigo, nombre, descripcion, unidad)
    SELECT codigo, nombre, descripcion, unidad FROM nuevos
    RETURNING id, codigo
), valores AS (
    INSERT INTO parametro_valores (parametro_id, valor, vigente_desde, motivo)
    SELECT p.id, n.valor, now(), n.motivo
    FROM parametros p JOIN nuevos n ON n.codigo = p.codigo
    RETURNING parametro_id, valor, vigente_desde, motivo
)
INSERT INTO historial_parametros (parametro_id, valor_nuevo, vigente_desde, motivo)
SELECT parametro_id, valor, vigente_desde, motivo FROM valores;

-- Como en los ahorros normales (V16). Sin interés retroactivo: las cuentas activas empiezan a ganar desde hoy.
ALTER TABLE cuentas_principales
    ADD COLUMN interes_acumulado NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (interes_acumulado >= 0),
    ADD COLUMN interes_disponible NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (interes_disponible >= 0),
    ADD COLUMN ultimo_devengo DATE;
UPDATE cuentas_principales SET ultimo_devengo = CURRENT_DATE WHERE estado = 'ACTIVA';

-- Pagar el interés de la cuenta principal es una operación interna propia.
ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO', 'INTERES_PRINCIPAL',
    'APORTE_CAPITAL', 'APORTE_INICIAL', 'APORTE_CAJA_CHICA', 'GASTO_CAJA_CHICA', 'RESERVA', 'DISTRIBUCION',
    'RETIRO_GANANCIA', 'COMPRAVENTA_CAPITAL', 'RETIRO_RESERVADO', 'RETIRO_LIBERADO', 'ASIENTO_MANUAL', 'SALDO_ANTERIOR'));

-- El libro de V30, con el interés pagado a la cuenta principal como gasto (5.1), igual que el de los ahorros.
CREATE OR REPLACE VIEW libro_contable AS
    SELECT m.creado_en AS fecha, m.operacion_id, '1.1'::varchar AS linea,
           CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END AS importe, m.concepto::varchar AS concepto
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo IN ('PRINCIPAL', 'FONDO_RESERVA')
    UNION ALL
    SELECT r.creado_en, r.operacion_id, '3.5', r.importe, 'RESERVA' FROM movimientos_reserva r
    UNION ALL
    SELECT r.creado_en, r.operacion_id, '3.7', -r.importe, 'RESERVA' FROM movimientos_reserva r
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
    SELECT m.creado_en, m.operacion_id, '5.1', m.importe, 'INTERES_PRINCIPAL'
    FROM movimientos_cuenta m WHERE m.concepto = 'INTERES_PRINCIPAL' AND m.sentido = 'ENTRADA'
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
    SELECT s.creado_en, s.operacion_id, '4.8', s.importe, 'SUMINISTROS' FROM suministros_credito s
    UNION ALL
    SELECT s.creado_en, s.operacion_id, s.linea, s.importe, 'SALDO_INICIAL: ' || s.descripcion FROM saldos_iniciales s;
