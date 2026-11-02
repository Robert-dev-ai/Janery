-- Índices para que las consultas sigan rápidas cuando haya años de datos reales. No cambian datos ni reglas.

-- persona_de_operacion() busca la operación en todas estas tablas, una vez por fila del libro diario y de tesorería.
CREATE INDEX ix_movimientos_cuenta_operacion ON movimientos_cuenta (operacion_id);
CREATE INDEX ix_solicitudes_retiro_operacion ON solicitudes_retiro (operacion_id);
CREATE INDEX ix_solicitudes_deposito_operacion ON solicitudes_deposito (operacion_id);
CREATE INDEX ix_depositos_apertura_operacion ON depositos_apertura (operacion_id);
CREATE INDEX ix_desembolsos_credito_operacion ON desembolsos_credito (operacion_id);
CREATE INDEX ix_pagos_credito_operacion ON pagos_credito (operacion_id);
CREATE INDEX ix_encajes_credito_operacion ON encajes_credito (operacion_id);
CREATE INDEX ix_movimientos_ahorro_operacion ON movimientos_ahorro (operacion_id);
CREATE INDEX ix_aportes_socios_operacion ON aportes_socios (operacion_id);
CREATE INDEX ix_aportes_caja_chica_operacion ON aportes_caja_chica (operacion_id);
CREATE INDEX ix_movimientos_caja_operacion ON movimientos_caja (operacion_id);

-- Listados filtrados por estado, del más reciente al más antiguo, y la campana de pendientes.
CREATE INDEX ix_creditos_estado ON creditos (estado, id DESC);
CREATE INDEX ix_cuentas_principales_estado ON cuentas_principales (estado, id DESC);

-- Detalle de cada crédito.
CREATE INDEX ix_desembolsos_credito ON desembolsos_credito (credito_id);
CREATE INDEX ix_pagos_credito ON pagos_credito (credito_id);
CREATE INDEX ix_movimientos_ahorro_letras ON movimientos_ahorro_letras (credito_id);

-- Resumen de los socios y valor vigente de cada parámetro.
CREATE INDEX ix_aportes_socios_socio ON aportes_socios (socio_id, tipo);
CREATE INDEX ix_parametro_valores ON parametro_valores (parametro_id, vigente_desde);

-- Cerrar las otras sesiones de una persona.
CREATE INDEX ix_tokens_refresco_usuario ON tokens_refresco (usuario_id) WHERE revocado_en IS NULL;
