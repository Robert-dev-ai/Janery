-- Decisión del propietario: el registro solo pide lo básico; la dirección y los datos para evaluar un crédito van en el perfil.
ALTER TABLE personas ALTER COLUMN direccion DROP NOT NULL;
ALTER TABLE personas ADD COLUMN lugar_trabajo VARCHAR(150);
ALTER TABLE personas ADD COLUMN ocupacion VARCHAR(100);
ALTER TABLE personas ADD COLUMN antiguedad_trabajo VARCHAR(50);
ALTER TABLE personas ADD COLUMN ingresos_mensuales NUMERIC(14, 2) CHECK (ingresos_mensuales >= 0);
ALTER TABLE personas ADD COLUMN fecha_nacimiento DATE;
ALTER TABLE personas ADD COLUMN estado_civil VARCHAR(20) CHECK (estado_civil IN ('SOLTERO', 'CASADO', 'UNION_LIBRE', 'DIVORCIADO', 'VIUDO'));
ALTER TABLE personas ADD COLUMN referencia_nombre VARCHAR(150);
ALTER TABLE personas ADD COLUMN referencia_telefono VARCHAR(20);
ALTER TABLE personas ADD COLUMN banco VARCHAR(100);
ALTER TABLE personas ADD COLUMN tipo_cuenta_banco VARCHAR(20) CHECK (tipo_cuenta_banco IN ('AHORROS', 'CORRIENTE'));
ALTER TABLE personas ADD COLUMN numero_cuenta_banco VARCHAR(40);

-- Un crédito precancelado termina con letras sin pagar: el interés y los cargos de esas letras no se cobran.
ALTER TABLE creditos ADD COLUMN precancelado_en TIMESTAMPTZ;
