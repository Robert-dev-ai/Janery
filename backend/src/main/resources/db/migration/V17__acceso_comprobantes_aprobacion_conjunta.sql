-- Decisión del propietario: tres contraseñas incorrectas seguidas bloquean el acceso; el presidente lo restablece.
-- La contraseña temporal que entrega el presidente obliga a elegir una nueva al entrar.
ALTER TABLE usuarios
    ADD COLUMN intentos_fallidos       INT         NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    ADD COLUMN debe_cambiar_contrasena BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN foto                    BYTEA,
    ADD COLUMN foto_tipo               VARCHAR(30) CHECK (foto_tipo IN ('image/jpeg', 'image/png', 'image/webp'));

-- Sin servidor de correo, quien olvida su contraseña lo pide aquí y el presidente se la restablece.
CREATE TABLE solicitudes_restablecimiento (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT      NOT NULL REFERENCES usuarios (id),
    estado       VARCHAR(20) NOT NULL CHECK (estado IN ('PENDIENTE', 'ATENDIDA')),
    atendida_por BIGINT REFERENCES usuarios (id),
    atendida_en  TIMESTAMPTZ,
    creado_en    TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_solicitudes_restablecimiento ON solicitudes_restablecimiento (estado, id);

-- Todo dinero que llega de fuera (apertura, depósitos) se respalda con el comprobante de pago.
CREATE TABLE comprobantes (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo          VARCHAR(20)  NOT NULL CHECK (tipo IN ('APERTURA', 'DEPOSITO')),
    referencia_id BIGINT       NOT NULL,
    nombre        VARCHAR(255) NOT NULL,
    tipo_contenido VARCHAR(30) NOT NULL CHECK (tipo_contenido IN ('application/pdf', 'image/jpeg', 'image/png', 'image/webp')),
    tamano        BIGINT       NOT NULL CHECK (tamano > 0),
    contenido     BYTEA        NOT NULL,
    subido_por    BIGINT       NOT NULL REFERENCES usuarios (id),
    creado_en     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_comprobantes ON comprobantes (tipo, referencia_id, id);

-- Una transferencia a otro banco o cooperativa cuesta lo que cobra el banco; lo paga quien retira.
ALTER TABLE solicitudes_retiro ADD COLUMN costo NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (costo >= 0);

-- Decisión del propietario: un crédito se decide con presidencia o vicepresidencia, tesorería y comité de crédito.
CREATE TABLE aprobaciones_credito (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo          VARCHAR(10)  NOT NULL CHECK (tipo IN ('CREDITO', 'CUPO')),
    referencia_id BIGINT       NOT NULL,
    area          VARCHAR(20)  NOT NULL CHECK (area IN ('DIRECCION', 'TESORERIA', 'COMITE_CREDITO')),
    usuario_id    BIGINT       NOT NULL REFERENCES usuarios (id),
    decision      VARCHAR(10)  NOT NULL CHECK (decision IN ('APROBAR', 'RECHAZAR')),
    motivo        VARCHAR(300),
    creado_en     TIMESTAMPTZ  NOT NULL,
    UNIQUE (tipo, referencia_id, area),
    UNIQUE (tipo, referencia_id, usuario_id)
);
CREATE INDEX ix_aprobaciones_credito ON aprobaciones_credito (tipo, referencia_id);

INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE c.codigo IN ('VICEPRESIDENCIA', 'TESORERIA', 'COMITE_CREDITO') AND p.codigo = 'APROBAR_CREDITOS'
ON CONFLICT DO NOTHING;
UPDATE permisos SET descripcion = 'Votar la aprobación o el rechazo de créditos' WHERE codigo = 'APROBAR_CREDITOS';

WITH nuevos (codigo, nombre, descripcion, unidad, valor, motivo) AS (VALUES
    ('COSTO_TRANSFERENCIA_OTROS', 'Costo de transferencia a otros bancos', 'Se suma al retiro que va a un banco o cooperativa distinto de Pichincha y Guayaquil', 'USD', 0.41, 'Definido por el propietario'),
    ('APORTE_INICIAL_MINIMO', 'Aporte inicial para ser socio', 'Capital con el que un cliente se convierte en socio', 'USD', 1000, 'Definido por el propietario')
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

-- Igual que V16, salvo el retiro: lo apartado y lo entregado incluyen el costo de la transferencia.
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
    SELECT a.creado_en, a.operacion_id, '1.99', -a.importe, 'MANUAL: ' || a.descripcion FROM asientos_manuales a WHERE a.linea = '5.9';
