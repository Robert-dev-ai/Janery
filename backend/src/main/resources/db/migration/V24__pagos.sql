-- Decisión del propietario: desde «Pagar» la persona paga de una vez cuotas, ahorros y aportes, suyos o de otras
-- personas, con comprobante obligatorio. Es un aviso de depósito con destinos: al confirmarlo el personal, el dinero
-- entra a la cuenta de cada beneficiario y de ahí se paga cada destino.
ALTER TABLE solicitudes_deposito ADD COLUMN es_pago BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE destinos_pago (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitud_id  BIGINT         NOT NULL REFERENCES solicitudes_deposito (id),
    orden         INTEGER        NOT NULL,
    persona_id    BIGINT         NOT NULL REFERENCES personas (id),
    tipo          VARCHAR(20)    NOT NULL CHECK (tipo IN ('CUENTA', 'CUOTA_CREDITO', 'AHORRO', 'APORTE_OBLIGATORIO',
                                                          'APORTE_PERSONAL', 'CAJA_CHICA')),
    referencia_id BIGINT,
    periodos      VARCHAR(100),
    importe       NUMERIC(14, 2) NOT NULL CHECK (importe > 0),
    descripcion   VARCHAR(200)   NOT NULL,
    UNIQUE (solicitud_id, orden)
);
CREATE INDEX ix_destinos_pago_solicitud ON destinos_pago (solicitud_id, orden);
