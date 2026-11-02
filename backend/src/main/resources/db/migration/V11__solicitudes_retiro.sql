ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO',
    'APORTE_CAPITAL', 'APORTE_INICIAL', 'APORTE_CAJA_CHICA', 'GASTO_CAJA_CHICA', 'RESERVA', 'DISTRIBUCION',
    'RETIRO_GANANCIA', 'COMPRAVENTA_CAPITAL', 'RETIRO_RESERVADO', 'RETIRO_LIBERADO', 'ASIENTO_MANUAL'));

-- Decisión del propietario: la persona pide sacar su dinero; el saldo queda apartado hasta que el personal lo entrega.
CREATE TABLE solicitudes_retiro (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_id          BIGINT         NOT NULL REFERENCES cuentas_principales (id),
    persona_id         BIGINT         NOT NULL REFERENCES personas (id),
    importe            NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    medio              VARCHAR(20)    NOT NULL CHECK (medio IN ('EFECTIVO', 'TRANSFERENCIA')),
    banco              VARCHAR(100),
    tipo_cuenta        VARCHAR(20)    CHECK (tipo_cuenta IN ('AHORROS', 'CORRIENTE')),
    numero_cuenta      VARCHAR(40),
    titular_cuenta     VARCHAR(150),
    estado             VARCHAR(20)    NOT NULL CHECK (estado IN ('PENDIENTE', 'ENTREGADA', 'RECHAZADA', 'CANCELADA')),
    motivo             VARCHAR(300),
    referencia         VARCHAR(100),
    clave_idempotencia VARCHAR(100)   NOT NULL UNIQUE,
    operacion_id       BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    resuelta_por       BIGINT REFERENCES usuarios (id),
    creado_en          TIMESTAMPTZ    NOT NULL,
    resuelta_en        TIMESTAMPTZ,
    CHECK (medio = 'EFECTIVO' OR (banco IS NOT NULL AND tipo_cuenta IS NOT NULL AND numero_cuenta IS NOT NULL AND titular_cuenta IS NOT NULL))
);
CREATE INDEX ix_solicitudes_retiro_estado ON solicitudes_retiro (estado, id DESC);
CREATE INDEX ix_solicitudes_retiro_persona ON solicitudes_retiro (persona_id, id DESC);
