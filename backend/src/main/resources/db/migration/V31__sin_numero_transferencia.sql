-- Decisión del propietario (24/11/2026): ya no se pide el número de la transferencia; basta el comprobante.
-- Los depósitos y pagos por transferencia se aceptan sin referencia. Los ya guardados conservan la suya.
ALTER TABLE solicitudes_deposito DROP CONSTRAINT IF EXISTS solicitudes_deposito_check;
