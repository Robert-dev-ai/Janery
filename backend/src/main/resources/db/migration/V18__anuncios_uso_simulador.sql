-- Decisión del propietario: la presidencia publica un aviso que corre en la cabecera de todos los paneles.
CREATE TABLE anuncios (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    texto       VARCHAR(300),
    publicado_por BIGINT      NOT NULL REFERENCES usuarios (id),
    creado_en   TIMESTAMPTZ  NOT NULL
);

-- Qué pantallas se usan más, para decidir qué mejorar primero. Solo la ruta, sin datos de la pantalla.
CREATE TABLE visitas (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT       NOT NULL REFERENCES usuarios (id),
    ruta       VARCHAR(120) NOT NULL,
    creado_en  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_visitas_fecha ON visitas (creado_en);

INSERT INTO permisos (codigo, descripcion) VALUES
    ('PUBLICAR_ANUNCIOS', 'Publicar el aviso que corre en la cabecera'),
    ('VER_USO_APP', 'Ver qué pantallas se usan más'),
    ('USAR_SIMULADOR', 'Usar el simulador: probar sin tocar los datos reales');
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE (c.codigo = 'PRESIDENCIA' AND p.codigo IN ('PUBLICAR_ANUNCIOS', 'VER_USO_APP', 'USAR_SIMULADOR'))
   OR (c.codigo = 'ADMINISTRACION' AND p.codigo IN ('VER_USO_APP', 'USAR_SIMULADOR'))
ON CONFLICT DO NOTHING;

-- Decisión del propietario: tesorería tramita los créditos de principio a fin.
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE c.codigo = 'TESORERIA' AND p.codigo IN ('APROBAR_CREDITOS', 'GESTIONAR_CUENTAS', 'CONSULTAR_CAJA')
ON CONFLICT DO NOTHING;
UPDATE permisos SET descripcion = 'Tramitar créditos y votar su aprobación o rechazo' WHERE codigo = 'APROBAR_CREDITOS';
