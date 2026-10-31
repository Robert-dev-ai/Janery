-- Los productos mueven dinero internamente, sin paso por ventanilla, y los procesos automáticos no tienen actor.
ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO'));
ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_metodo_pago_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_metodo_pago_check
    CHECK (metodo_pago IN ('EFECTIVO', 'TRANSFERENCIA', 'INTERNO'));
ALTER TABLE operaciones_financieras ALTER COLUMN actor_id DROP NOT NULL;

CREATE TABLE socios (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id BIGINT      NOT NULL UNIQUE REFERENCES personas (id),
    desde      TIMESTAMPTZ NOT NULL,
    estado     VARCHAR(20) NOT NULL CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE creditos (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id            BIGINT         NOT NULL REFERENCES personas (id),
    familia               VARCHAR(30)    NOT NULL CHECK (familia IN ('LETRAS', 'EMERGENTE_CLIENTE', 'EMERGENTE_SOCIO', 'EMERGENTE_ESTACIONAL')),
    modalidad             VARCHAR(20)    CHECK (modalidad IN ('NORMAL', 'INVERSION', 'ESTUDIO', 'PERSONALIZADO', 'ESTUDIANTIL', 'NAVIDENO')),
    perfil                VARCHAR(10)    NOT NULL CHECK (perfil IN ('SOCIO', 'CLIENTE')),
    estado                VARCHAR(20)    NOT NULL CHECK (estado IN ('SOLICITADO', 'ACTIVO', 'FINALIZADO', 'RECHAZADO')),
    importe               NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (importe >= 0),
    plazo_meses           INTEGER,
    tasa_interes          NUMERIC(9, 6)  NOT NULL,
    tasa_desgravamen      NUMERIC(9, 6)  NOT NULL,
    unidad_tasa           VARCHAR(20)    NOT NULL CHECK (unidad_tasa IN ('PCT_DIARIO', 'PCT_MENSUAL')),
    porcentaje_encaje     NUMERIC(9, 6),
    capital_pendiente     NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (capital_pendiente >= 0),
    interes_acumulado     NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (interes_acumulado >= 0),
    desgravamen_acumulado NUMERIC(14, 6) NOT NULL DEFAULT 0 CHECK (desgravamen_acumulado >= 0),
    ultimo_devengo        DATE,
    anio_promocion        INTEGER,
    fecha_inicio          DATE,
    motivo_rechazo        VARCHAR(300),
    finalizado_en         TIMESTAMPTZ,
    creado_en             TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_creditos_persona ON creditos (persona_id);
CREATE UNIQUE INDEX ux_cupo_socio ON creditos (persona_id) WHERE familia = 'EMERGENTE_SOCIO';
CREATE UNIQUE INDEX ux_promocion_socio ON creditos (persona_id, modalidad, anio_promocion) WHERE familia = 'EMERGENTE_ESTACIONAL';

CREATE TABLE cuotas_credito (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id        BIGINT         NOT NULL REFERENCES creditos (id),
    numero            INTEGER        NOT NULL,
    vencimiento       DATE           NOT NULL,
    capital           NUMERIC(14, 2) NOT NULL,
    ahorro_letras     NUMERIC(14, 2) NOT NULL,
    fondo_desgravamen NUMERIC(14, 2) NOT NULL,
    interes           NUMERIC(14, 2) NOT NULL,
    total             NUMERIC(14, 2) NOT NULL,
    pagada_en         TIMESTAMPTZ,
    UNIQUE (credito_id, numero),
    CHECK (capital + ahorro_letras + fondo_desgravamen + interes = total)
);

CREATE TABLE desembolsos_credito (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id   BIGINT         NOT NULL REFERENCES creditos (id),
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    creado_en    TIMESTAMPTZ    NOT NULL
);

CREATE TABLE pagos_credito (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id   BIGINT         NOT NULL REFERENCES creditos (id),
    cuota_id     BIGINT REFERENCES cuotas_credito (id),
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    creado_en    TIMESTAMPTZ    NOT NULL
);

CREATE TABLE detalles_pago (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pago_id  BIGINT         NOT NULL REFERENCES pagos_credito (id),
    concepto VARCHAR(30)    NOT NULL CHECK (concepto IN ('CAPITAL', 'INTERES', 'FONDO_DESGRAVAMEN', 'AHORRO_LETRAS')),
    importe  NUMERIC(14, 2) NOT NULL CHECK (importe > 0)
);

CREATE TABLE encajes_credito (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id   BIGINT         NOT NULL UNIQUE REFERENCES creditos (id),
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe >= 0),
    pagado_en    TIMESTAMPTZ    NOT NULL,
    devuelto_en  TIMESTAMPTZ
);

CREATE TABLE ahorro_letras_credito (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id  BIGINT         NOT NULL UNIQUE REFERENCES creditos (id),
    saldo       NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (saldo >= 0),
    liberado_en TIMESTAMPTZ
);

CREATE TABLE movimientos_ahorro_letras (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credito_id BIGINT         NOT NULL REFERENCES creditos (id),
    cuota_id   BIGINT REFERENCES cuotas_credito (id),
    tipo       VARCHAR(20)    NOT NULL CHECK (tipo IN ('ACUMULACION', 'LIBERACION')),
    importe    NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    creado_en  TIMESTAMPTZ    NOT NULL
);
CREATE TRIGGER movimientos_ahorro_letras_inmutable BEFORE UPDATE OR DELETE ON movimientos_ahorro_letras
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

INSERT INTO permisos (codigo, descripcion) VALUES ('APROBAR_CREDITOS', 'Aprobar y rechazar créditos a letras');
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p WHERE c.codigo = 'PRESIDENCIA' AND p.codigo = 'APROBAR_CREDITOS';

-- Valores provisionales, sin definir por el propietario: se ajustan desde el CMS.
WITH nuevos (codigo, nombre, descripcion, unidad, valor) AS (VALUES
    ('LETRAS_IMPORTE_MIN', 'Letras: importe mínimo', 'Importe mínimo de un crédito a letras', 'USD', 100),
    ('LETRAS_IMPORTE_MAX', 'Letras: importe máximo', 'Importe máximo de un crédito a letras', 'USD', 5000),
    ('LETRAS_PLAZO_MIN', 'Letras: plazo mínimo', 'Plazo mínimo, en letras mensuales', 'MESES', 3),
    ('LETRAS_PLAZO_MAX', 'Letras: plazo máximo', 'Plazo máximo, en letras mensuales', 'MESES', 36),
    ('LETRAS_DESGRAVAMEN', 'Letras: desgravamen', 'Fondo de desgravamen mensual sobre el capital pendiente', 'PCT_MENSUAL', 0.1),
    ('LETRAS_INTERES_NORMAL_SOCIO', 'Interés normal, socio', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 1),
    ('LETRAS_INTERES_NORMAL_CLIENTE', 'Interés normal, cliente', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 1.5),
    ('LETRAS_INTERES_INVERSION_SOCIO', 'Interés inversión, socio', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 1.2),
    ('LETRAS_INTERES_INVERSION_CLIENTE', 'Interés inversión, cliente', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 1.8),
    ('LETRAS_INTERES_ESTUDIO_SOCIO', 'Interés estudio, socio', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 0.8),
    ('LETRAS_INTERES_ESTUDIO_CLIENTE', 'Interés estudio, cliente', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 1.2),
    ('LETRAS_INTERES_PERSONALIZADO_SOCIO', 'Interés personalizado, socio', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 1.5),
    ('LETRAS_INTERES_PERSONALIZADO_CLIENTE', 'Interés personalizado, cliente', 'Interés mensual sobre el capital pendiente', 'PCT_MENSUAL', 2),
    ('LETRAS_ENCAJE_NORMAL_SOCIO', 'Encaje normal, socio', 'Porcentaje del importe, pagado al inicio', 'PCT', 5),
    ('LETRAS_ENCAJE_NORMAL_CLIENTE', 'Encaje normal, cliente', 'Porcentaje del importe, pagado al inicio', 'PCT', 10),
    ('LETRAS_ENCAJE_INVERSION_SOCIO', 'Encaje inversión, socio', 'Porcentaje del importe, pagado al inicio', 'PCT', 5),
    ('LETRAS_ENCAJE_INVERSION_CLIENTE', 'Encaje inversión, cliente', 'Porcentaje del importe, pagado al inicio', 'PCT', 10),
    ('LETRAS_ENCAJE_ESTUDIO_SOCIO', 'Encaje estudio, socio', 'Porcentaje del importe, pagado al inicio', 'PCT', 3),
    ('LETRAS_ENCAJE_ESTUDIO_CLIENTE', 'Encaje estudio, cliente', 'Porcentaje del importe, pagado al inicio', 'PCT', 5),
    ('LETRAS_ENCAJE_PERSONALIZADO_SOCIO', 'Encaje personalizado, socio', 'Porcentaje del importe, pagado al inicio', 'PCT', 10),
    ('LETRAS_ENCAJE_PERSONALIZADO_CLIENTE', 'Encaje personalizado, cliente', 'Porcentaje del importe, pagado al inicio', 'PCT', 15)
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
