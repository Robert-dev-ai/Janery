-- Decisión del propietario (22/11/2026): además de los créditos a letras, se registran los emergentes, los ahorros y los
-- aportes que ya existían antes de usar Janery. Entran solo los saldos de hoy, sin mover dinero: SALDO_ANTERIOR.
ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO',
    'APORTE_CAPITAL', 'APORTE_INICIAL', 'APORTE_CAJA_CHICA', 'GASTO_CAJA_CHICA', 'RESERVA', 'DISTRIBUCION',
    'RETIRO_GANANCIA', 'COMPRAVENTA_CAPITAL', 'RETIRO_RESERVADO', 'RETIRO_LIBERADO', 'ASIENTO_MANUAL', 'SALDO_ANTERIOR'));

UPDATE permisos SET descripcion = 'Registrar créditos, ahorros y aportes que ya existían antes de usar Janery'
WHERE codigo = 'REGISTRAR_CREDITOS_EXISTENTES';
