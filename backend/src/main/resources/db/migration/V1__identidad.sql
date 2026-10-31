CREATE TABLE usuarios (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correo          VARCHAR(254) NOT NULL UNIQUE,
    hash_contrasena VARCHAR(100) NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'BLOQUEADO')),
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE personas (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL UNIQUE REFERENCES usuarios (id),
    nombres        VARCHAR(100) NOT NULL,
    apellidos      VARCHAR(100) NOT NULL,
    identificacion VARCHAR(20)  NOT NULL UNIQUE,
    telefono       VARCHAR(20)  NOT NULL,
    direccion      VARCHAR(200) NOT NULL,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cargos (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(40)  NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL
);

CREATE TABLE permisos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo      VARCHAR(60)  NOT NULL UNIQUE,
    descripcion VARCHAR(200) NOT NULL
);

CREATE TABLE cargo_permisos (
    cargo_id   BIGINT NOT NULL REFERENCES cargos (id),
    permiso_id BIGINT NOT NULL REFERENCES permisos (id),
    PRIMARY KEY (cargo_id, permiso_id)
);

CREATE TABLE usuario_cargos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT      NOT NULL REFERENCES usuarios (id),
    cargo_id     BIGINT      NOT NULL REFERENCES cargos (id),
    desde        TIMESTAMPTZ NOT NULL DEFAULT now(),
    hasta        TIMESTAMPTZ,
    asignado_por BIGINT REFERENCES usuarios (id)
);
CREATE UNIQUE INDEX ux_usuario_cargo_vigente ON usuario_cargos (usuario_id, cargo_id) WHERE hasta IS NULL;

CREATE TABLE tokens_refresco (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id),
    hash_token  VARCHAR(64) NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ NOT NULL,
    revocado_en TIMESTAMPTZ,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO cargos (codigo, nombre) VALUES
    ('ADMINISTRACION', 'Administración'),
    ('PRESIDENCIA', 'Presidencia'),
    ('VICEPRESIDENCIA', 'Vicepresidencia'),
    ('SECRETARIA', 'Secretaría'),
    ('TESORERIA', 'Tesorería'),
    ('CAJA_CHICA', 'Caja chica'),
    ('COMITE_CREDITO', 'Comité de crédito'),
    ('ASUNTOS_SOCIALES', 'Asuntos sociales');

INSERT INTO permisos (codigo, descripcion) VALUES
    ('ADMIN_PARAMETROS', 'Administrar parámetros del sistema'),
    ('ADMIN_USUARIOS', 'Consultar usuarios y asignar o revocar cargos'),
    ('CONSULTAR_AUDITORIA', 'Consultar la auditoría'),
    ('GESTIONAR_CUENTAS', 'Confirmar aperturas y cierres; registrar depósitos y retiros'),
    ('CONSULTAR_CAJA', 'Consultar la caja principal');

-- Decisión del propietario: Presidencia tiene todos los permisos; los demás cargos se definirán por versiones.
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c CROSS JOIN permisos p WHERE c.codigo = 'PRESIDENCIA';
