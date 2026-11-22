-- Decisión del propietario (22/11/2026): el fondo de reserva no es dinero guardado aparte. Es la parte del patrimonio que
-- pertenece al grupo y su dinero se presta como el resto, desde la caja principal. Sigue recibiendo el 10 % de cada
-- ganancia, pero ya no pasa a otra caja: queda registrado aquí y en el balance (3.5).
CREATE TABLE movimientos_reserva (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operacion_id BIGINT REFERENCES operaciones_financieras (id),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe <> 0),
    concepto     VARCHAR(40)    NOT NULL,
    detalle      VARCHAR(200),
    creado_en    TIMESTAMPTZ    NOT NULL
);
CREATE TRIGGER movimientos_reserva_inmutable BEFORE UPDATE OR DELETE ON movimientos_reserva
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

-- Saldos con que empieza el balance para lo que no tiene otro registro en Janery (fondo de desgravamen, resultado
-- acumulado de antes): se cargan una vez, al reiniciar los datos, y no se modifican.
CREATE TABLE saldos_iniciales (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    linea        VARCHAR(5)     NOT NULL CHECK (linea IN ('1.2', '1.3', '1.5', '1.6', '1.7', '1.8', '2.4', '2.5', '2.6', '3.3', '3.4', '3.7')),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe <> 0),
    descripcion  VARCHAR(200)   NOT NULL,
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    creado_en    TIMESTAMPTZ    NOT NULL
);
CREATE TRIGGER saldos_iniciales_inmutable BEFORE UPDATE OR DELETE ON saldos_iniciales
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

-- Lo que ya se separó sigue siendo reserva.
INSERT INTO movimientos_reserva (operacion_id, importe, concepto, detalle, creado_en)
SELECT m.operacion_id, CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, m.concepto, m.detalle, m.creado_en
FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA' ORDER BY m.id;

-- Y su dinero vuelve a la caja principal, de donde se presta.
WITH saldo AS (
    SELECT COALESCE(SUM(CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END), 0) AS importe
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA'
), operacion AS (
    INSERT INTO operaciones_financieras (tipo, importe, metodo_pago, actor_id, creado_en)
    SELECT 'RESERVA', s.importe, 'INTERNO',
           COALESCE((SELECT uc.usuario_id FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
                     WHERE c.codigo = 'PRESIDENCIA' ORDER BY uc.usuario_id LIMIT 1), (SELECT MIN(id) FROM usuarios)),
           now()
    FROM saldo s WHERE s.importe > 0
    RETURNING id, importe
)
INSERT INTO movimientos_caja (caja_id, operacion_id, sentido, importe, concepto, detalle, creado_en)
SELECT c.id, o.id, x.sentido, o.importe, 'RESERVA_A_CAJA_PRINCIPAL', 'El fondo de reserva se presta desde la caja principal', now()
FROM operacion o
CROSS JOIN (VALUES ('FONDO_RESERVA', 'SALIDA'), ('PRINCIPAL', 'ENTRADA')) x (caja, sentido)
JOIN cajas c ON c.codigo = x.caja;

-- El libro de V23, con la reserva tomada de movimientos_reserva en lugar de la caja del fondo de reserva.
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
