CREATE TABLE cajas (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE CHECK (codigo IN ('PRINCIPAL', 'CHICA', 'FONDO_RESERVA'))
);
INSERT INTO cajas (codigo) VALUES ('PRINCIPAL'), ('CHICA'), ('FONDO_RESERVA');

CREATE SEQUENCE secuencia_numero_cuenta;

CREATE TABLE cuentas_principales (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id       BIGINT         NOT NULL REFERENCES personas (id),
    numero           VARCHAR(20)    NOT NULL UNIQUE,
    estado           VARCHAR(20)    NOT NULL CHECK (estado IN ('PENDIENTE_PAGO', 'ACTIVA', 'PENDIENTE_CIERRE', 'CERRADA', 'RECHAZADA')),
    saldo_disponible NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (saldo_disponible >= 0),
    metodo_pago      VARCHAR(20)    NOT NULL CHECK (metodo_pago IN ('EFECTIVO', 'TRANSFERENCIA')),
    referencia_pago  VARCHAR(100),
    abierta_en       TIMESTAMPTZ,
    cerrada_en       TIMESTAMPTZ,
    creado_en        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_cuenta_vigente_por_persona ON cuentas_principales (persona_id)
    WHERE estado NOT IN ('CERRADA', 'RECHAZADA');

CREATE TABLE operaciones_financieras (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo               VARCHAR(30)    NOT NULL CHECK (tipo IN ('APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE')),
    importe            NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    metodo_pago        VARCHAR(20)    NOT NULL CHECK (metodo_pago IN ('EFECTIVO', 'TRANSFERENCIA')),
    referencia         VARCHAR(100),
    clave_idempotencia VARCHAR(100) UNIQUE,
    actor_id           BIGINT         NOT NULL REFERENCES usuarios (id),
    creado_en          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE depositos_apertura (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_id            BIGINT         NOT NULL UNIQUE REFERENCES cuentas_principales (id),
    operacion_id         BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    importe_total        NUMERIC(14, 2) NOT NULL,
    importe_ingreso      NUMERIC(14, 2) NOT NULL,
    importe_reembolsable NUMERIC(14, 2) NOT NULL,
    devuelto_en          TIMESTAMPTZ,
    CHECK (importe_ingreso + importe_reembolsable = importe_total)
);

CREATE TABLE movimientos_cuenta (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_id        BIGINT         NOT NULL REFERENCES cuentas_principales (id),
    operacion_id     BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    sentido          VARCHAR(10)    NOT NULL CHECK (sentido IN ('ENTRADA', 'SALIDA')),
    importe          NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    concepto         VARCHAR(40)    NOT NULL,
    saldo_resultante NUMERIC(14, 2) NOT NULL CHECK (saldo_resultante >= 0),
    creado_en        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_movimientos_cuenta ON movimientos_cuenta (cuenta_id, id DESC);
CREATE TRIGGER movimientos_cuenta_inmutable BEFORE UPDATE OR DELETE ON movimientos_cuenta
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

CREATE TABLE movimientos_caja (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caja_id      BIGINT         NOT NULL REFERENCES cajas (id),
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    sentido      VARCHAR(10)    NOT NULL CHECK (sentido IN ('ENTRADA', 'SALIDA')),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    concepto     VARCHAR(40)    NOT NULL,
    creado_en    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_movimientos_caja ON movimientos_caja (caja_id, id DESC);
CREATE TRIGGER movimientos_caja_inmutable BEFORE UPDATE OR DELETE ON movimientos_caja
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();
