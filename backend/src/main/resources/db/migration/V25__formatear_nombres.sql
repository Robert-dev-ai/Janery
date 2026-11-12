-- Lleva los nombres y apellidos ya registrados al formato con que se guardan desde ahora: mayúscula inicial en cada
-- palabra. Misma regla que Persona.formatearNombre: espacios sobrantes fuera, minúsculas, y mayúscula al inicio y
-- después de cada espacio o guion ("maría  josé" -> "María José", "ruiz-pérez" -> "Ruiz-Pérez").
-- Cada cambio queda en la auditoría con el nombre anterior.

-- Temporal: desaparece al terminar la conexión de la migración.
CREATE FUNCTION pg_temp.formatear_nombre(texto TEXT) RETURNS TEXT LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    limpio    TEXT := lower(regexp_replace(regexp_replace(texto, '^\s+|\s+$', '', 'g'), '\s+', ' ', 'g'));
    resultado TEXT := '';
    inicio    BOOLEAN := TRUE;
    letra     TEXT;
BEGIN
    FOREACH letra IN ARRAY coalesce(regexp_split_to_array(limpio, ''), '{}') LOOP
        resultado := resultado || CASE WHEN inicio THEN upper(letra) ELSE letra END;
        inicio := letra IN (' ', '-');
    END LOOP;
    RETURN resultado;
END
$$;

CREATE TEMPORARY TABLE cambios_nombre ON COMMIT DROP AS
SELECT id,
       nombres || ' ' || apellidos                                                       AS antes,
       pg_temp.formatear_nombre(nombres)                                                 AS nombres,
       pg_temp.formatear_nombre(apellidos)                                               AS apellidos,
       pg_temp.formatear_nombre(nombres) || ' ' || pg_temp.formatear_nombre(apellidos)   AS despues
FROM personas
WHERE nombres <> pg_temp.formatear_nombre(nombres) OR apellidos <> pg_temp.formatear_nombre(apellidos);

UPDATE personas p SET nombres = c.nombres, apellidos = c.apellidos
FROM cambios_nombre c WHERE p.id = c.id;

INSERT INTO auditoria_eventos (actor_id, accion, modulo, entidad, entidad_id, antes, despues, motivo)
SELECT NULL, 'NOMBRE_FORMATEADO', 'PERSONA', 'persona', id::TEXT, antes, despues, 'Mayúscula inicial en cada palabra'
FROM cambios_nombre ORDER BY id;
