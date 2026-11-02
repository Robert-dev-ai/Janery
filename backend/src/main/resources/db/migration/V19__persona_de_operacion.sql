-- De quién es cada operación, para mostrarlo en los libros y los informes generales. Null si no es de una persona
-- (registro manual, reserva, reparto entre socios): quien consulta pone "Socios" o "Janery" según el concepto.
CREATE FUNCTION persona_de_operacion(op BIGINT) RETURNS BIGINT LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        (SELECT c.persona_id FROM movimientos_cuenta m JOIN cuentas_principales c ON c.id = m.cuenta_id WHERE m.operacion_id = op LIMIT 1),
        (SELECT persona_id FROM solicitudes_retiro WHERE operacion_id = op LIMIT 1),
        (SELECT persona_id FROM solicitudes_deposito WHERE operacion_id = op LIMIT 1),
        (SELECT c.persona_id FROM depositos_apertura d JOIN cuentas_principales c ON c.id = d.cuenta_id WHERE d.operacion_id = op LIMIT 1),
        (SELECT cr.persona_id FROM desembolsos_credito d JOIN creditos cr ON cr.id = d.credito_id WHERE d.operacion_id = op LIMIT 1),
        (SELECT cr.persona_id FROM pagos_credito p JOIN creditos cr ON cr.id = p.credito_id WHERE p.operacion_id = op LIMIT 1),
        (SELECT cr.persona_id FROM encajes_credito e JOIN creditos cr ON cr.id = e.credito_id WHERE e.operacion_id = op LIMIT 1),
        (SELECT a.persona_id FROM movimientos_ahorro m JOIN cuentas_ahorro a ON a.id = m.cuenta_ahorro_id WHERE m.operacion_id = op LIMIT 1),
        (SELECT s.persona_id FROM aportes_socios a JOIN socios s ON s.id = a.socio_id WHERE a.operacion_id = op LIMIT 1),
        (SELECT s.persona_id FROM aportes_caja_chica a JOIN socios s ON s.id = a.socio_id WHERE a.operacion_id = op LIMIT 1)
    )
$$;
