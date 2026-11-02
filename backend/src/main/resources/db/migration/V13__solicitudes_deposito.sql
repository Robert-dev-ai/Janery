-- Decisión del propietario: la persona también avisa que deposita. El saldo y la caja solo cambian cuando el personal
-- confirma que recibió el dinero; por eso el depósito confirmado no necesita líneas nuevas en libro_contable.
CREATE TABLE solicitudes_deposito (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_id          BIGINT         NOT NULL REFERENCES cuentas_principales (id),
    persona_id         BIGINT         NOT NULL REFERENCES personas (id),
    importe            NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    medio              VARCHAR(20)    NOT NULL CHECK (medio IN ('EFECTIVO', 'TRANSFERENCIA')),
    referencia         VARCHAR(100),
    estado             VARCHAR(20)    NOT NULL CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'CANCELADA')),
    motivo             VARCHAR(300),
    clave_idempotencia VARCHAR(100)   NOT NULL UNIQUE,
    operacion_id       BIGINT REFERENCES operaciones_financieras (id),
    resuelta_por       BIGINT REFERENCES usuarios (id),
    creado_en          TIMESTAMPTZ    NOT NULL,
    resuelta_en        TIMESTAMPTZ,
    CHECK (medio = 'EFECTIVO' OR referencia IS NOT NULL)
);
CREATE INDEX ix_solicitudes_deposito_estado ON solicitudes_deposito (estado, id DESC);
CREATE INDEX ix_solicitudes_deposito_persona ON solicitudes_deposito (persona_id, id DESC);
