INSERT INTO permisos (codigo, descripcion) VALUES ('CONSULTAR_INFORMES', 'Consultar y exportar informes de toda la organización');
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p WHERE c.codigo = 'PRESIDENCIA' AND p.codigo = 'CONSULTAR_INFORMES';
