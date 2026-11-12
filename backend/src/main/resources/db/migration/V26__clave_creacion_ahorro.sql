-- Clave de idempotencia al abrir un ahorro normal: un doble clic o un reintento no abre dos ahorros.
-- Los ahorros ya abiertos quedan sin clave. Los plazos fijos se reconocen por su operación de apertura.
ALTER TABLE cuentas_ahorro ADD COLUMN clave_creacion VARCHAR(100) UNIQUE;
