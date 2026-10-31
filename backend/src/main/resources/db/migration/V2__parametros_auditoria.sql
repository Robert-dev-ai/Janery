CREATE FUNCTION rechazar_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo inserción', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE parametros_sistema (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo      VARCHAR(60)  NOT NULL UNIQUE,
    nombre      VARCHAR(120) NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    unidad      VARCHAR(20)  NOT NULL CHECK (unidad IN ('USD', 'PCT', 'PCT_DIARIO', 'PCT_MENSUAL', 'PCT_ANUAL', 'DIAS', 'MESES', 'MES', 'USOS_MES'))
);

CREATE TABLE parametro_valores (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parametro_id  BIGINT         NOT NULL REFERENCES parametros_sistema (id),
    valor         NUMERIC(14, 6) NOT NULL CHECK (valor >= 0),
    vigente_desde TIMESTAMPTZ    NOT NULL,
    vigente_hasta TIMESTAMPTZ,
    creado_por    BIGINT REFERENCES usuarios (id),
    motivo        VARCHAR(300)   NOT NULL,
    creado_en     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde)
);
CREATE UNIQUE INDEX ux_parametro_valor_abierto ON parametro_valores (parametro_id) WHERE vigente_hasta IS NULL;

CREATE TABLE historial_parametros (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parametro_id   BIGINT         NOT NULL REFERENCES parametros_sistema (id),
    valor_anterior NUMERIC(14, 6),
    valor_nuevo    NUMERIC(14, 6) NOT NULL,
    vigente_desde  TIMESTAMPTZ    NOT NULL,
    actor_id       BIGINT REFERENCES usuarios (id),
    motivo         VARCHAR(300)   NOT NULL,
    creado_en      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE TRIGGER historial_parametros_inmutable BEFORE UPDATE OR DELETE ON historial_parametros
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

CREATE TABLE auditoria_eventos (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id   BIGINT REFERENCES usuarios (id),
    accion     VARCHAR(60)  NOT NULL,
    modulo     VARCHAR(40)  NOT NULL,
    entidad    VARCHAR(60),
    entidad_id VARCHAR(60),
    antes      TEXT,
    despues    TEXT,
    motivo     VARCHAR(300),
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_creado_en ON auditoria_eventos (creado_en DESC);
CREATE TRIGGER auditoria_eventos_inmutable BEFORE UPDATE OR DELETE ON auditoria_eventos
    FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion();

-- Solo valores indicados en docs/reglas-negocio.md. Tasas de letras, encajes y ahorros siguen pendientes.
WITH nuevos (codigo, nombre, descripcion, unidad, valor) AS (VALUES
    ('APERTURA_TOTAL', 'Pago de apertura', 'Importe total para abrir la cuenta principal', 'USD', 20),
    ('APERTURA_INGRESO', 'Ingreso de apertura', 'Parte del pago reconocida como ingreso en caja principal', 'USD', 15),
    ('APERTURA_REEMBOLSABLE', 'Depósito reembolsable', 'Parte del pago que se devuelve al cerrar la cuenta', 'USD', 5),
    ('EMERGENTE_CLIENTE_LIMITE', 'Emergente cliente: límite', 'Límite general del emergente para clientes', 'USD', 300),
    ('EMERGENTE_CLIENTE_FRECUENCIA', 'Emergente cliente: frecuencia', 'Veces que puede obtenerse al mes', 'USOS_MES', 1),
    ('EMERGENTE_CLIENTE_INTERES', 'Emergente cliente: interés', 'Interés mensual', 'PCT_MENSUAL', 3.5),
    ('EMERGENTE_CLIENTE_DESGRAVAMEN', 'Emergente cliente: desgravamen', 'Fondo de desgravamen mensual', 'PCT_MENSUAL', 0.25),
    ('EMERGENTE_SOCIO_CUPO', 'Emergente socio: cupo', 'Cupo general del emergente para socios', 'USD', 300),
    ('EMERGENTE_SOCIO_INTERES', 'Emergente socio: interés', 'Interés diario sobre el capital utilizado', 'PCT_DIARIO', 0.12),
    ('EMERGENTE_SOCIO_DESGRAVAMEN', 'Emergente socio: desgravamen', 'Fondo de desgravamen diario', 'PCT_DIARIO', 0.08),
    ('ESTACIONAL_LIMITE', 'Emergente estacional: límite', 'Máximo por promoción, dentro del cupo general', 'USD', 50),
    ('ESTACIONAL_TASA', 'Emergente estacional: tasa', 'Tasa diaria de las promociones', 'PCT_DIARIO', 0.05),
    ('ESTACIONAL_MES_ESTUDIANTIL', 'Mes del emergente estudiantil', 'Mes del año en que está disponible', 'MES', 8),
    ('ESTACIONAL_MES_NAVIDENO', 'Mes del emergente navideño', 'Mes del año en que está disponible', 'MES', 12),
    ('APORTE_OBLIGATORIO', 'Aporte obligatorio', 'Aporte mensual de cada socio al capital', 'USD', 2.5),
    ('APORTE_CAJA_CHICA', 'Aporte a caja chica', 'Aporte mensual de cada socio a caja chica', 'USD', 2.5),
    ('FONDO_RESERVA', 'Fondo de reserva', 'Porcentaje destinado a reserva antes de distribuir ganancias', 'PCT', 10)
), parametros AS (
    INSERT INTO parametros_sistema (codigo, nombre, descripcion, unidad)
    SELECT codigo, nombre, descripcion, unidad FROM nuevos
    RETURNING id, codigo
), valores AS (
    INSERT INTO parametro_valores (parametro_id, valor, vigente_desde, motivo)
    SELECT p.id, n.valor, now(), 'Valor inicial indicado por el propietario'
    FROM parametros p JOIN nuevos n ON n.codigo = p.codigo
    RETURNING parametro_id, valor, vigente_desde, motivo
)
INSERT INTO historial_parametros (parametro_id, valor_nuevo, vigente_desde, motivo)
SELECT parametro_id, valor, vigente_desde, motivo FROM valores;
