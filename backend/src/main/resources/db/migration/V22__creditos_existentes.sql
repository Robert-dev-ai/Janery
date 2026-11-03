-- Créditos a letras que ya existían antes de usar Janery: se registran con sus propias condiciones y sus letras ya pagadas.
ALTER TABLE creditos ADD COLUMN existente BOOLEAN NOT NULL DEFAULT false;

INSERT INTO permisos (codigo, descripcion) VALUES
    ('REGISTRAR_CREDITOS_EXISTENTES', 'Registrar créditos que ya existían antes de usar Janery');
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE p.codigo = 'REGISTRAR_CREDITOS_EXISTENTES' AND c.codigo = 'PRESIDENCIA';
