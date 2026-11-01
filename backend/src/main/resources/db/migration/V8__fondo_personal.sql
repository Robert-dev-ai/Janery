-- Decisión del propietario: la ganancia queda en el fondo personal, la reserva se separa con cada ganancia
-- y el fondo se desglosa por origen, incluidos los aportes iniciales.
ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO',
    'APORTE_CAPITAL', 'APORTE_INICIAL', 'APORTE_CAJA_CHICA', 'GASTO_CAJA_CHICA', 'RESERVA', 'DISTRIBUCION'));

ALTER TABLE aportes_socios DROP CONSTRAINT aportes_socios_tipo_check;
ALTER TABLE aportes_socios ADD CONSTRAINT aportes_socios_tipo_check CHECK (tipo IN ('INICIAL', 'OBLIGATORIO', 'PERSONAL'));

ALTER TABLE variaciones_participacion DROP CONSTRAINT variaciones_participacion_tipo_check;
ALTER TABLE variaciones_participacion ADD CONSTRAINT variaciones_participacion_tipo_check CHECK (tipo IN (
    'APORTE_INICIAL', 'APORTE_OBLIGATORIO', 'APORTE_PERSONAL', 'GANANCIA', 'PERDIDA', 'RESERVA', 'DISTRIBUCION'));

-- Las filas anteriores repartieron el importe completo, sin reserva. El trigger de solo inserción no se dispara
-- con ADD COLUMN: el valor por defecto rellena las existentes.
ALTER TABLE resultados ADD COLUMN reserva NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (reserva >= 0);
ALTER TABLE resultados ADD COLUMN porcentaje_reserva NUMERIC(14, 6) NOT NULL DEFAULT 0;

UPDATE parametros_sistema
SET descripcion = 'Porcentaje de cada ganancia que se separa para el fondo de reserva al registrarla'
WHERE codigo = 'FONDO_RESERVA';
