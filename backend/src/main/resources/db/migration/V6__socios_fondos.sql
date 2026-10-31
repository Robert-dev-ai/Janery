ALTER TABLE operaciones_financieras DROP CONSTRAINT operaciones_financieras_tipo_check;
ALTER TABLE operaciones_financieras ADD CONSTRAINT operaciones_financieras_tipo_check CHECK (tipo IN (
    'APERTURA', 'DEPOSITO', 'RETIRO', 'DEVOLUCION_CIERRE', 'DESEMBOLSO', 'PAGO_CREDITO',
    'APERTURA_AHORRO', 'DEPOSITO_AHORRO', 'RETIRO_AHORRO', 'INTERES_AHORRO',
    'APORTE_CAPITAL', 'APORTE_CAJA_CHICA', 'GASTO_CAJA_CHICA', 'RESERVA', 'DISTRIBUCION'));

ALTER TABLE movimientos_caja ADD COLUMN detalle VARCHAR(200);

CREATE TABLE aportes_socios (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    socio_id     BIGINT         NOT NULL REFERENCES socios (id),
    tipo         VARCHAR(20)    NOT NULL CHECK (tipo IN ('OBLIGATORIO', 'PERSONAL')),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    periodo      VARCHAR(7)     NOT NULL,
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    creado_en    TIMESTAMPTZ    NOT NULL
);
CREATE UNIQUE INDEX ux_aporte_obligatorio_periodo ON aportes_socios (socio_id, periodo) WHERE tipo = 'OBLIGATORIO';

-- Separados del capital: son dinero de caja chica, no participación.
CREATE TABLE aportes_caja_chica (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    socio_id     BIGINT         NOT NULL REFERENCES socios (id),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    periodo      VARCHAR(7)     NOT NULL,
    operacion_id BIGINT         NOT NULL REFERENCES operaciones_financieras (id),
    creado_en    TIMESTAMPTZ    NOT NULL,
    UNIQUE (socio_id, periodo)
);

CREATE TABLE resultados (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo      VARCHAR(10)    NOT NULL CHECK (tipo IN ('GANANCIA', 'PERDIDA')),
    importe   NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    concepto  VARCHAR(200)   NOT NULL,
    fecha     DATE           NOT NULL,
    actor_id  BIGINT         NOT NULL REFERENCES usuarios (id),
    creado_en TIMESTAMPTZ    NOT NULL
);
CREATE TRIGGER resultados_inmutable BEFORE UPDATE OR DELETE ON resultados
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

CREATE TABLE cierres_anuales (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anio               INTEGER        NOT NULL UNIQUE,
    resultado_neto     NUMERIC(14, 2) NOT NULL,
    porcentaje_reserva NUMERIC(14, 6) NOT NULL,
    reserva            NUMERIC(14, 2) NOT NULL,
    distribuido        NUMERIC(14, 2) NOT NULL,
    actor_id           BIGINT         NOT NULL REFERENCES usuarios (id),
    creado_en          TIMESTAMPTZ    NOT NULL
);

-- El valor de cada socio se obtiene sumando estas filas; los periodos se calculan de ellas.
CREATE TABLE variaciones_participacion (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    socio_id     BIGINT         NOT NULL REFERENCES socios (id),
    tipo         VARCHAR(30)    NOT NULL CHECK (tipo IN ('APORTE_OBLIGATORIO', 'APORTE_PERSONAL', 'GANANCIA', 'PERDIDA', 'RESERVA', 'DISTRIBUCION')),
    importe      NUMERIC(14, 2) NOT NULL CHECK (importe <> 0),
    fecha        DATE           NOT NULL,
    aporte_id    BIGINT REFERENCES aportes_socios (id),
    resultado_id BIGINT REFERENCES resultados (id),
    cierre_id    BIGINT REFERENCES cierres_anuales (id),
    creado_en    TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_variaciones_socio ON variaciones_participacion (socio_id, fecha, id);
CREATE TRIGGER variaciones_participacion_inmutable BEFORE UPDATE OR DELETE ON variaciones_participacion
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

INSERT INTO permisos (codigo, descripcion) VALUES
    ('GESTIONAR_CAJA_CHICA', 'Consultar caja chica y registrar sus gastos'),
    ('GESTIONAR_RESULTADOS', 'Registrar ganancias y pérdidas, y cerrar el año');
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE p.codigo IN ('GESTIONAR_CAJA_CHICA', 'GESTIONAR_RESULTADOS') AND c.codigo = 'PRESIDENCIA';
-- Documentado en reglas-negocio.md: el cargo de caja chica administra este fondo.
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p WHERE p.codigo = 'GESTIONAR_CAJA_CHICA' AND c.codigo = 'CAJA_CHICA';
