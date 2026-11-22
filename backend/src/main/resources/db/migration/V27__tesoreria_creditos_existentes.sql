-- Decisión del propietario (13/11/2026): además de Presidencia, Tesorería registra los créditos que ya existían antes de Janery.
-- Tesorería ya tramita créditos nuevos en ventanilla con APROBAR_CREDITOS (V17).
INSERT INTO cargo_permisos (cargo_id, permiso_id)
SELECT c.id, p.id FROM cargos c, permisos p
WHERE c.codigo = 'TESORERIA' AND p.codigo = 'REGISTRAR_CREDITOS_EXISTENTES'
ON CONFLICT DO NOTHING;
