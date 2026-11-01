ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO',
    'APORTE_CAPITAL', 'APORTE_INICIAL', 'APORTE_CAJA_CHICA', 'GASTO_CAJA_CHICA', 'RESERVA', 'DISTRIBUCION',
    'RETIRO_GANANCIA', 'COMPRAVENTA_CAPITAL'));

ALTER TABLE variaciones_participacion DROP CONSTRAINT variaciones_participacion_tipo_check;
ALTER TABLE variaciones_participacion ADD CONSTRAINT variaciones_participacion_tipo_check CHECK (tipo IN (
    'APORTE_INICIAL', 'APORTE_OBLIGATORIO', 'APORTE_PERSONAL', 'APORTE_COMPRA', 'APORTE_VENTA',
    'GANANCIA', 'PERDIDA', 'RESERVA', 'DISTRIBUCION', 'RETIRO_GANANCIA', 'DESCUENTO_OBLIGACION'));

-- El precio lo acuerdan los socios: puede ser distinto del capital que cambia de manos.
CREATE TABLE compraventas_capital (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vendedor_id  BIGINT         NOT NULL REFERENCES socios (id),
    comprador_id BIGINT         NOT NULL REFERENCES socios (id),
    capital      NUMERIC(14, 2) NOT NULL CHECK (capital > 0),
    precio       NUMERIC(14, 2) NOT NULL CHECK (precio > 0),
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    actor_id     BIGINT         NOT NULL REFERENCES usuarios (id),
    creado_en    TIMESTAMPTZ    NOT NULL,
    CHECK (vendedor_id <> comprador_id)
);
CREATE TRIGGER compraventas_capital_inmutable BEFORE UPDATE OR DELETE ON compraventas_capital
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();
