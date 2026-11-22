-- Decisión del propietario (22/11/2026): el capital también pasa de un socio a otro sin pago dentro de Janery (un regalo,
-- una herencia o un pago hecho por fuera). Se guarda como una compraventa de precio cero, sin operación de dinero y con
-- su motivo, para que todos los cálculos del capital la traten igual que a una compraventa.
ALTER TABLE compraventas_capital DROP CONSTRAINT compraventas_capital_precio_check;
ALTER TABLE compraventas_capital ADD CONSTRAINT compraventas_capital_precio_check CHECK (precio >= 0);
ALTER TABLE compraventas_capital ALTER COLUMN operacion_id DROP NOT NULL;
ALTER TABLE compraventas_capital ADD COLUMN motivo VARCHAR(200);
-- Sin operación de dinero, la clave de idempotencia evita registrar dos veces la misma transferencia.
ALTER TABLE compraventas_capital ADD COLUMN clave VARCHAR(100) UNIQUE;
ALTER TABLE compraventas_capital ADD CONSTRAINT compraventas_capital_transferencia_check
    CHECK (operacion_id IS NOT NULL OR (precio = 0 AND motivo IS NOT NULL));
