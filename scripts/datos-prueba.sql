-- Datos falsos para probar Janery: 5 usuarios con perfil y cuenta principal. NUNCA ejecutar en producción.
-- Contraseña de los 5 usuarios: Prueba-Janery-1
--
-- Requiere las migraciones de Flyway ya aplicadas. Es todo o nada, y se niega a repetirse.
-- Los movimientos y la auditoría son inmutables: estos datos no se pueden borrar con DELETE.
--
-- Uso: psql "<cadena de conexión>" -v ON_ERROR_STOP=1 -f scripts/datos-prueba.sql

BEGIN;

SET LOCAL search_path TO janery;

DO $$
DECLARE
    -- BCrypt de la contraseña de prueba, generado con el mismo codificador que usa la aplicación.
    hash_prueba CONSTANT VARCHAR := '$2a$10$351DL5BwiemTyQ5rbrm7iOgNY2GB5GhmnzlkoecCDLHM26uKIC31.';
    ahora CONSTANT TIMESTAMPTZ := now();
    actor BIGINT;
    fila RECORD;
    v_usuario BIGINT;
    v_persona BIGINT;
    v_cuenta BIGINT;
    v_operacion BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM usuarios WHERE correo LIKE '%@janery.test') THEN
        RAISE EXCEPTION 'Los datos de prueba ya existen en esta base';
    END IF;

    -- Quien confirma las operaciones: el presidente, si ya existe.
    SELECT uc.usuario_id INTO actor
    FROM usuario_cargos uc JOIN cargos c ON c.id = uc.cargo_id
    WHERE c.codigo = 'PRESIDENCIA' AND uc.hasta IS NULL
    ORDER BY uc.id LIMIT 1;

    FOR fila IN
        SELECT * FROM (VALUES
            ('ana.prueba@janery.test',    'Ana',    'Prueba Mora',    '0000000001', '0990000001', 'Calle Ficticia 101, Quito',     'EFECTIVO',      'ACTIVA',         250.00),
            ('bruno.prueba@janery.test',  'Bruno',  'Prueba Vera',    '0000000002', '0990000002', 'Calle Ficticia 102, Guayaquil', 'TRANSFERENCIA', 'ACTIVA',         120.50),
            ('carla.prueba@janery.test',  'Carla',  'Prueba Ríos',    '0000000003', '0990000003', 'Calle Ficticia 103, Cuenca',    'TRANSFERENCIA', 'ACTIVA',         500.00),
            ('diego.prueba@janery.test',  'Diego',  'Prueba Luna',    '0000000004', '0990000004', 'Calle Ficticia 104, Loja',      'EFECTIVO',      'ACTIVA',           0.00),
            ('elena.prueba@janery.test',  'Elena',  'Prueba Campos',  '0000000005', '0990000005', 'Calle Ficticia 105, Ambato',    'EFECTIVO',      'PENDIENTE_PAGO',   0.00)
        ) AS t (correo, nombres, apellidos, identificacion, telefono, direccion, metodo, estado, deposito)
    LOOP
        INSERT INTO usuarios (correo, hash_contrasena)
        VALUES (fila.correo, hash_prueba)
        RETURNING id INTO v_usuario;

        INSERT INTO personas (usuario_id, nombres, apellidos, identificacion, telefono, direccion)
        VALUES (v_usuario, fila.nombres, fila.apellidos, fila.identificacion, fila.telefono, fila.direccion)
        RETURNING id INTO v_persona;

        INSERT INTO cuentas_principales (persona_id, numero, estado, saldo_disponible, metodo_pago, abierta_en)
        VALUES (v_persona, 'JAN-' || lpad(nextval('secuencia_numero_cuenta')::TEXT, 6, '0'), fila.estado,
                fila.deposito, fila.metodo, CASE WHEN fila.estado = 'ACTIVA' THEN ahora END)
        RETURNING id INTO v_cuenta;

        INSERT INTO auditoria_eventos (actor_id, accion, modulo, entidad, entidad_id, motivo)
        VALUES (actor, 'DATOS_PRUEBA', 'USUARIO', 'usuario', v_usuario::TEXT, 'Creado por scripts/datos-prueba.sql');

        -- Elena queda pendiente de pago, para probar la confirmación de apertura desde la aplicación.
        CONTINUE WHEN fila.estado <> 'ACTIVA';

        -- Apertura: USD 15 de ingreso en caja y USD 5 de depósito reembolsable, fuera del saldo.
        INSERT INTO operaciones_financieras (tipo, importe, metodo_pago, actor_id)
        VALUES ('APERTURA', 20.00, fila.metodo, actor)
        RETURNING id INTO v_operacion;

        INSERT INTO depositos_apertura (cuenta_id, operacion_id, importe_total, importe_ingreso, importe_reembolsable)
        VALUES (v_cuenta, v_operacion, 20.00, 15.00, 5.00);

        INSERT INTO movimientos_caja (caja_id, operacion_id, sentido, importe, concepto)
        SELECT id, v_operacion, 'ENTRADA', 15.00, 'INGRESO_APERTURA' FROM cajas WHERE codigo = 'PRINCIPAL';

        CONTINUE WHEN fila.deposito = 0;

        -- Depósito: entra a la caja y al libro de la cuenta, que debe cuadrar con el saldo.
        INSERT INTO operaciones_financieras (tipo, importe, metodo_pago, actor_id)
        VALUES ('DEPOSITO', fila.deposito, fila.metodo, actor)
        RETURNING id INTO v_operacion;

        INSERT INTO movimientos_cuenta (cuenta_id, operacion_id, sentido, importe, concepto, saldo_resultante)
        VALUES (v_cuenta, v_operacion, 'ENTRADA', fila.deposito, 'DEPOSITO', fila.deposito);

        INSERT INTO movimientos_caja (caja_id, operacion_id, sentido, importe, concepto)
        SELECT id, v_operacion, 'ENTRADA', fila.deposito, 'DEPOSITO_CUENTA' FROM cajas WHERE codigo = 'PRINCIPAL';
    END LOOP;
END $$;

COMMIT;
