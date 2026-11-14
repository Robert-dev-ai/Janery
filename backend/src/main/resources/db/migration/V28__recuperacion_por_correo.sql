-- Decisión del propietario (13/11/2026): quien olvida su contraseña la recupera con un enlace que le llega a su correo.
-- Se guarda solo el hash del enlace: quien lea la base no puede usarlo. Sirve una vez y vence pronto.
CREATE TABLE recuperaciones_contrasena (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id),
    hash_token VARCHAR(64) NOT NULL UNIQUE,
    expira_en  TIMESTAMPTZ NOT NULL,
    usada_en   TIMESTAMPTZ,
    creado_en  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_recuperaciones_usuario ON recuperaciones_contrasena (usuario_id, creado_en);
