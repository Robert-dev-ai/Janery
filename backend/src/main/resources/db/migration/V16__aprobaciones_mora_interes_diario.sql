-- Decisión del propietario: el depósito de apertura sigue siendo del cliente, pero está en la caja y se puede prestar.
INSERT INTO movimientos_caja (caja_id, operacion_id, sentido, importe, concepto, creado_en)
SELECT c.id, d.operacion_id, 'ENTRADA', d.importe_reembolsable, 'DEPOSITO_REEMBOLSABLE', o.creado_en
FROM depositos_apertura d JOIN operaciones_financieras o ON o.id = d.operacion_id
CROSS JOIN cajas c WHERE c.codigo = 'PRINCIPAL' AND d.importe_reembolsable > 0;
INSERT INTO movimientos_caja (caja_id, operacion_id, sentido, importe, concepto, creado_en)
SELECT c.id, d.operacion_id, 'SALIDA', d.importe_reembolsable, 'DEVOLUCION_DEPOSITO', d.devuelto_en
FROM depositos_apertura d CROSS JOIN cajas c
WHERE c.codigo = 'PRINCIPAL' AND d.importe_reembolsable > 0 AND d.devuelto_en IS NOT NULL;

-- El encaje puede pagarse en efectivo, de la cuenta, de un ahorro o descontarse del propio crédito.
ALTER TABLE creditos
    ADD COLUMN origen_encaje VARCHAR(10) CHECK (origen_encaje IN ('EFECTIVO', 'CUENTA', 'AHORROS', 'CREDITO')),
    ADD COLUMN ahorro_encaje_id BIGINT REFERENCES cuentas_ahorro (id),
    ADD COLUMN tasa_mora NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (tasa_mora >= 0),
    ADD COLUMN clave_solicitud VARCHAR(100) UNIQUE;

-- La mora se calcula con los días de atraso; aquí solo queda lo cobrado y hasta qué día corrió.
ALTER TABLE cuotas_credito
    ADD COLUMN mora_pagada NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (mora_pagada >= 0),
    ADD COLUMN mora_hasta DATE;
ALTER TABLE detalles_pago DROP CONSTRAINT detalles_pago_concepto_check;
ALTER TABLE detalles_pago ADD CONSTRAINT detalles_pago_concepto_check
    CHECK (concepto IN ('CAPITAL', 'INTERES', 'FONDO_DESGRAVAMEN', 'AHORRO_LETRAS', 'MORA'));

-- Un cupo de socio es una sola línea de crédito: cada retiro que pide espera aprobación aquí.
CREATE TABLE solicitudes_cupo (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id    BIGINT         NOT NULL REFERENCES personas (id),
    importe       NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    promocion     VARCHAR(20)    CHECK (promocion IN ('ESTUDIANTIL', 'NAVIDENO')),
    estado        VARCHAR(20)    NOT NULL CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    motivo        VARCHAR(300),
    clave         VARCHAR(100)   NOT NULL UNIQUE,
    resuelta_por  BIGINT REFERENCES usuarios (id),
    resuelta_en   TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_solicitudes_cupo_estado ON solicitudes_cupo (estado, id);
CREATE INDEX ix_solicitudes_cupo_persona ON solicitudes_cupo (persona_id, id DESC);

CREATE TABLE documentos_credito (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id BIGINT       NOT NULL REFERENCES creditos (id),
    nombre     VARCHAR(255) NOT NULL,
    tamano     BIGINT       NOT NULL CHECK (tamano > 0),
    contenido  BYTEA        NOT NULL,
    subido_por BIGINT       NOT NULL REFERENCES usuarios (id),
    creado_en  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_documentos_credito ON documentos_credito (credito_id, id);

-- El interés de los ahorros normales corre cada día y va aparte del saldo: lo acumulado se libera cada día 1.
ALTER TABLE cuentas_ahorro
    ADD COLUMN interes_acumulado NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (interes_acumulado >= 0),
    ADD COLUMN interes_disponible NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (interes_disponible >= 0),
    ADD COLUMN ultimo_devengo DATE;
UPDATE cuentas_ahorro SET ultimo_devengo = CURRENT_DATE WHERE tipo = 'NORMAL' AND estado = 'ACTIVA';

-- Parámetros que ya no se usan: quedan por su historial, pero no se ofrecen para editar.
ALTER TABLE parametros_sistema ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE parametros_sistema SET activo = FALSE WHERE codigo IN ('PLAZO_FIJO_TASA', 'PLAZO_FIJO_PLAZO_MIN', 'PLAZO_FIJO_PLAZO_MAX');
UPDATE parametros_sistema SET descripcion = 'Interés anual; se calcula cada día (anual / 365) sobre el saldo y se libera cada día 1'
WHERE codigo = 'AHORRO_NORMAL_TASA';

UPDATE parametro_valores SET vigente_hasta = now()
WHERE vigente_hasta IS NULL AND parametro_id = (SELECT id FROM parametros_sistema WHERE codigo = 'AHORRO_NORMAL_TASA');
WITH valor AS (
    INSERT INTO parametro_valores (parametro_id, valor, vigente_desde, motivo)
    SELECT id, 5, now(), 'Definido por el propietario: 5 % anual' FROM parametros_sistema WHERE codigo = 'AHORRO_NORMAL_TASA'
    RETURNING parametro_id, valor, vigente_desde, motivo
)
INSERT INTO historial_parametros (parametro_id, valor_nuevo, vigente_desde, motivo)
SELECT parametro_id, valor, vigente_desde, motivo FROM valor;

WITH nuevos (codigo, nombre, descripcion, unidad, valor, motivo) AS (VALUES
    ('MORA_DIARIA', 'Mora diaria', 'Cargo por día de atraso de cada letra. Solo para clientes; se fija en el crédito al aprobarlo', 'USD', 0.01, 'Definido por el propietario'),
    ('PLAZO_FIJO_TASA_3_MESES', 'Plazo fijo a 3 meses: tasa', 'Interés anual simple, pagado al terminar el contrato', 'PCT_ANUAL', 5.5, 'Valor provisional, a ajustar por el presidente'),
    ('PLAZO_FIJO_TASA_6_MESES', 'Plazo fijo a 6 meses: tasa', 'Interés anual simple, pagado al terminar el contrato', 'PCT_ANUAL', 6, 'Valor provisional, a ajustar por el presidente'),
    ('PLAZO_FIJO_TASA_9_MESES', 'Plazo fijo a 9 meses: tasa', 'Interés anual simple, pagado al terminar el contrato', 'PCT_ANUAL', 6.5, 'Valor provisional, a ajustar por el presidente'),
    ('PLAZO_FIJO_TASA_12_MESES', 'Plazo fijo a 12 meses: tasa', 'Interés anual simple, pagado al terminar el contrato', 'PCT_ANUAL', 7, 'Valor provisional, a ajustar por el presidente'),
    ('PLAZO_FIJO_TASA_24_MESES', 'Plazo fijo a 2 años: tasa', 'Interés anual simple, pagado al terminar el contrato', 'PCT_ANUAL', 8, 'Valor provisional, a ajustar por el presidente')
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

-- Los créditos de clientes ya vigentes también corren mora desde ahora.
UPDATE creditos SET tasa_mora = 0.01 WHERE perfil = 'CLIENTE' AND familia IN ('LETRAS', 'EMERGENTE_CLIENTE') AND estado = 'ACTIVO';

-- Igual que V12, salvo dos cambios: el depósito de apertura ya entra a 1.1 por la caja, y la mora cobrada es 4.2.
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
    SELECT s.creado_en, s.operacion_id, '2.1', s.importe, 'RETIRO_APARTADO' FROM solicitudes_retiro s
    UNION ALL
    SELECT s.resuelta_en, s.operacion_id, '2.1', -s.importe, 'RETIRO_RESUELTO' FROM solicitudes_retiro s WHERE s.resuelta_en IS NOT NULL
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
    SELECT a.creado_en, a.operacion_id, '1.99', -a.importe, 'MANUAL: ' || a.descripcion FROM asientos_manuales a WHERE a.linea = '5.9';
