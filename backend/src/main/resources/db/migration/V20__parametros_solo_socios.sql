-- Decisión del propietario: los clientes solo piden el crédito a letras normal. Las tasas y encajes de las demás
-- modalidades para clientes ya no se usan: quedan por su historial, pero no se ofrecen para editar.
UPDATE parametros_sistema SET activo = FALSE
WHERE codigo IN ('LETRAS_INTERES_INVERSION_CLIENTE', 'LETRAS_INTERES_ESTUDIO_CLIENTE', 'LETRAS_INTERES_PERSONALIZADO_CLIENTE',
                 'LETRAS_ENCAJE_INVERSION_CLIENTE', 'LETRAS_ENCAJE_ESTUDIO_CLIENTE', 'LETRAS_ENCAJE_PERSONALIZADO_CLIENTE');
