-- Pagos parciales: lo cobrado de cada componente de la cuota.
ALTER TABLE cuotas_credito ADD COLUMN capital_pagado NUMERIC(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE cuotas_credito ADD COLUMN interes_pagado NUMERIC(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE cuotas_credito ADD COLUMN desgravamen_pagado NUMERIC(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE cuotas_credito ADD COLUMN ahorro_pagado NUMERIC(14, 2) NOT NULL DEFAULT 0;
UPDATE cuotas_credito
SET capital_pagado = capital, interes_pagado = interes, desgravamen_pagado = fondo_desgravamen, ahorro_pagado = ahorro_letras
WHERE pagada_en IS NOT NULL;

-- Decisión del presidente: qué socio puede retirar sus ganancias cada año.
CREATE TABLE autorizaciones_retiro (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    socio_id       BIGINT      NOT NULL REFERENCES socios (id),
    anio           INTEGER     NOT NULL,
    autorizado_por BIGINT      NOT NULL REFERENCES usuarios (id),
    creado_en      TIMESTAMPTZ NOT NULL,
    UNIQUE (socio_id, anio)
);

-- El interés y el desgravamen de las letras ahora son fijos sobre el capital sacado.
UPDATE parametros_sistema SET descripcion = 'Interés mensual fijo sobre el capital sacado' WHERE codigo LIKE 'LETRAS_INTERES_%';
UPDATE parametros_sistema SET descripcion = 'Fondo de desgravamen mensual fijo sobre el capital sacado' WHERE codigo = 'LETRAS_DESGRAVAMEN';
