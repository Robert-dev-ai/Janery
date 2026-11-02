-- Lo que el sistema no registra por sí solo: multas, gastos, bienes, bancos. Solo se inserta; se corrige con un reverso.
CREATE TABLE asientos_manuales (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    linea        VARCHAR(5)     NOT NULL,
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe <> 0),
    descripcion  VARCHAR(200)   NOT NULL,
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    reverso_de   BIGINT UNIQUE REFERENCES asientos_manuales (id),
    actor_id     BIGINT         NOT NULL REFERENCES usuarios (id),
    creado_en    TIMESTAMPTZ    NOT NULL
);
CREATE TRIGGER asientos_manuales_inmutable BEFORE UPDATE OR DELETE ON asientos_manuales
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

-- Traduce cada movimiento del sistema a las líneas del balance del propietario. Importe positivo: la línea aumenta.
-- Debe y haber salen del primer dígito: 1 y 5 aumentan por el debe; 2, 3 y 4, por el haber.
-- La caja chica lleva sus cuentas aparte y no entra. 3.6 no aparece: se calcula con los ingresos y gastos del mes.
CREATE VIEW libro_contable AS
    -- 1.1 Caja: principal y fondo de reserva.
    SELECT m.creado_en AS fecha, m.operacion_id, '1.1'::varchar AS linea,
           CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END AS importe, m.concepto::varchar AS concepto
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo IN ('PRINCIPAL', 'FONDO_RESERVA')
    -- 3.5 Reservas: lo que se separa sale del resultado acumulado.
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '3.5', CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, 'RESERVA'
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA'
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '3.7', CASE WHEN m.sentido = 'ENTRADA' THEN -m.importe ELSE m.importe END, 'RESERVA'
    FROM movimientos_caja m JOIN cajas c ON c.id = m.caja_id WHERE c.codigo = 'FONDO_RESERVA'
    -- Apertura: el ingreso es 4.7. El depósito reembolsable no pasa por la caja principal, pero es efectivo que se debe.
    UNION ALL
    SELECT o.creado_en, d.operacion_id, '4.7', d.importe_ingreso, 'INGRESO_APERTURA'
    FROM depositos_apertura d JOIN operaciones_financieras o ON o.id = d.operacion_id WHERE d.importe_ingreso > 0
    UNION ALL
    SELECT o.creado_en, d.operacion_id, x.linea, d.importe_reembolsable, 'DEPOSITO_REEMBOLSABLE'
    FROM depositos_apertura d JOIN operaciones_financieras o ON o.id = d.operacion_id
    CROSS JOIN (VALUES ('1.1'), ('2.3')) x (linea) WHERE d.importe_reembolsable > 0
    UNION ALL
    SELECT d.devuelto_en, d.operacion_id, x.linea, -d.importe_reembolsable, 'DEVOLUCION_DEPOSITO'
    FROM depositos_apertura d CROSS JOIN (VALUES ('1.1'), ('2.3')) x (linea)
    WHERE d.devuelto_en IS NOT NULL AND d.importe_reembolsable > 0
    -- 2.1 Cuentas principales, más lo apartado por retiros aún sin entregar.
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '2.1', CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, m.concepto
    FROM movimientos_cuenta m
    UNION ALL
    SELECT s.creado_en, s.operacion_id, '2.1', s.importe, 'RETIRO_APARTADO' FROM solicitudes_retiro s
    UNION ALL
    SELECT s.resuelta_en, s.operacion_id, '2.1', -s.importe, 'RETIRO_RESUELTO' FROM solicitudes_retiro s WHERE s.resuelta_en IS NOT NULL
    -- 2.1 ahorro normal y 2.2 plazo fijo; su interés es gasto 5.1.
    UNION ALL
    SELECT m.creado_en, m.operacion_id, CASE WHEN a.tipo = 'NORMAL' THEN '2.1' ELSE '2.2' END,
           CASE WHEN m.sentido = 'ENTRADA' THEN m.importe ELSE -m.importe END, m.concepto
    FROM movimientos_ahorro m JOIN cuentas_ahorro a ON a.id = m.cuenta_ahorro_id
    UNION ALL
    SELECT m.creado_en, m.operacion_id, '5.1', m.importe, 'INTERES_AHORRO'
    FROM movimientos_ahorro m WHERE m.concepto = 'INTERES' AND m.sentido = 'ENTRADA'
    -- 1.4 Cartera, 4.1 intereses y 2.5 desgravamen, según lo cobrado.
    UNION ALL
    SELECT d.creado_en, d.operacion_id, '1.4', d.importe, 'DESEMBOLSO' FROM desembolsos_credito d
    UNION ALL
    SELECT p.creado_en, p.operacion_id,
           CASE dp.concepto WHEN 'CAPITAL' THEN '1.4' WHEN 'INTERES' THEN '4.1' WHEN 'FONDO_DESGRAVAMEN' THEN '2.5' ELSE '2.3' END,
           CASE WHEN dp.concepto = 'CAPITAL' THEN -dp.importe ELSE dp.importe END, 'PAGO_' || dp.concepto
    FROM detalles_pago dp JOIN pagos_credito p ON p.id = dp.pago_id
    -- 2.3 Encajes y ahorro letras.
    UNION ALL
    SELECT e.pagado_en, e.operacion_id, '2.3', e.importe, 'ENCAJE' FROM encajes_credito e WHERE e.importe > 0
    UNION ALL
    SELECT e.devuelto_en, e.operacion_id, '2.3', -e.importe, 'DEVOLUCION_ENCAJE' FROM encajes_credito e
    WHERE e.devuelto_en IS NOT NULL AND e.importe > 0
    UNION ALL
    SELECT m.creado_en, NULL, '2.3', -m.importe, 'LIBERACION_AHORRO_LETRAS' FROM movimientos_ahorro_letras m WHERE m.tipo = 'LIBERACION'
    -- 3.1 y 3.2 Aportes. Repartir ganancias es interno al patrimonio; solo retirarlas o descontarlas lo reduce.
    UNION ALL
    SELECT v.creado_en, a.operacion_id,
           CASE WHEN v.tipo = 'APORTE_INICIAL' THEN '3.1' WHEN v.tipo LIKE 'APORTE_%' THEN '3.2' ELSE '3.7' END, v.importe, v.tipo
    FROM variaciones_participacion v LEFT JOIN aportes_socios a ON a.id = v.aporte_id
    WHERE v.tipo IN ('APORTE_INICIAL', 'APORTE_OBLIGATORIO', 'APORTE_PERSONAL', 'APORTE_COMPRA', 'APORTE_VENTA',
                     'RETIRO_GANANCIA', 'DESCUENTO_OBLIGACION')
    -- Registro manual. La provisión (5.9) no mueve caja: reduce la cartera en 1.99.
    UNION ALL
    SELECT a.creado_en, a.operacion_id, a.linea, a.importe, 'MANUAL: ' || a.descripcion FROM asientos_manuales a
    UNION ALL
    SELECT a.creado_en, a.operacion_id, '1.99', -a.importe, 'MANUAL: ' || a.descripcion FROM asientos_manuales a WHERE a.linea = '5.9';

INSERT INTO permisos (codigo, descripcion) VALUES
    ('CONSULTAR_CONTABILIDAD', 'Consultar balance, libros e indicadores'),
    ('REGISTRAR_ASIENTOS', 'Registrar y revertir ingresos, gastos y bienes en el registro manual');
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE p.codigo IN ('CONSULTAR_CONTABILIDAD', 'REGISTRAR_ASIENTOS') AND c.codigo = 'PRESIDENCIA';
