CREATE TABLE cuentas_ahorro (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id  BIGINT         NOT NULL REFERENCES personas (id),
    tipo        VARCHAR(20)    NOT NULL CHECK (tipo IN ('NORMAL', 'PLAZO_FIJO')),
    nombre      VARCHAR(100)   NOT NULL,
    estado      VARCHAR(20)    NOT NULL CHECK (estado IN ('ACTIVA', 'CERRADA')),
    saldo       NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (saldo >= 0),
    tasa_anual  NUMERIC(9, 6),
    plazo_meses INTEGER,
    vence_en    DATE,
    abierta_en  TIMESTAMPTZ    NOT NULL,
    cerrada_en  TIMESTAMPTZ,
    CHECK ((tipo = 'PLAZO_FIJO') = (vence_en IS NOT NULL))
);
CREATE INDEX ix_cuentas_ahorro_persona ON cuentas_ahorro (persona_id);

CREATE TABLE movimientos_ahorro (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_ahorro_id BIGINT         NOT NULL REFERENCES cuentas_ahorro (id),
    operacion_id     BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    sentido          VARCHAR(10)    NOT NULL CHECK (sentido IN ('ENTRADA', 'SALIDA')),
    importe          NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    concepto         VARCHAR(40)    NOT NULL,
    saldo_resultante NUMERIC(14, 2) NOT NULL CHECK (saldo_resultante >= 0),
    periodo_interes  VARCHAR(7),
    tasa_aplicada    NUMERIC(9, 6),
    creado_en        TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_movimientos_ahorro ON movimientos_ahorro (cuenta_ahorro_id, id DESC);
-- Evita abonar dos veces el interés de un mismo mes.
CREATE UNIQUE INDEX ux_interes_ahorro_periodo ON movimientos_ahorro (cuenta_ahorro_id, periodo_interes)
    WHERE periodo_interes IS NOT NULL;
CREATE TRIGGER movimientos_ahorro_inmutable BEFORE UPDATE OR DELETE ON movimientos_ahorro
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

-- Valores provisionales, sin definir por el propietario: se ajustan desde el CMS.
WITH nuevos (codigo, nombre, descripcion, unidad, valor) AS (VALUES
    ('AHORRO_NORMAL_TASA', 'Ahorro normal: tasa', 'Interés anual, abonado cada mes sobre el saldo', 'PCT_ANUAL', 2),
    ('PLAZO_FIJO_TASA', 'Plazo fijo: tasa', 'Interés anual simple, abonado al vencimiento', 'PCT_ANUAL', 5),
    ('PLAZO_FIJO_PLAZO_MIN', 'Plazo fijo: plazo mínimo', 'Plazo mínimo del depósito', 'MESES', 3),
    ('PLAZO_FIJO_PLAZO_MAX', 'Plazo fijo: plazo máximo', 'Plazo máximo del depósito', 'MESES', 24)
), parametros AS (
    INSERT INTO parametros_sistema (codigo, nombre, descripcion, unidad)
    SELECT codigo, nombre, descripcion, unidad FROM nuevos
    RETURNING id, codigo
), valores AS (
    INSERT INTO parametro_valores (parametro_id, valor, vigente_desde, motivo)
    SELECT p.id, n.valor, now(), 'Valor provisional, a ajustar por el propietario'
    FROM parametros p JOIN nuevos n ON n.codigo = p.codigo
    RETURNING parametro_id, valor, vigente_desde, motivo
)
INSERT INTO historial_parametros (parametro_id, valor_nuevo, vigente_desde, motivo)
SELECT parametro_id, valor, vigente_desde, motivo FROM valores;
