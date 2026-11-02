-- Decisión del propietario: cada ingreso se reparte al momento. El reparto automático no tiene actor si lo origina un proceso.
ALTER TABLE resultados ALTER COLUMN actor_id DROP NOT NULL;
-- Lo que un asiento de ingreso separó a la reserva: su reverso debe devolver exactamente eso.
ALTER TABLE asientos_manuales ADD COLUMN reserva NUMERIC(14, 2) NOT NULL DEFAULT 0;
