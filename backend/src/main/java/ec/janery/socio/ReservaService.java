package ec.janery.socio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Decisión del propietario (22/11/2026): el fondo de reserva es la parte del patrimonio que pertenece al grupo, no dinero
 * guardado aparte. Su dinero queda en la caja principal y se presta; aquí solo se lleva cuánto le corresponde (línea 3.5).
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

    private final JdbcClient jdbc;
    private final Clock reloj;

    /** Importe positivo: la reserva crece; negativo: se reduce. */
    @Transactional
    public void registrar(Long operacionId, BigDecimal importe, String concepto, String detalle) {
        jdbc.sql("""
                INSERT INTO movimientos_reserva (operacion_id, importe, concepto, detalle, creado_en)
                VALUES (:operacion, :importe, :concepto, :detalle, :ahora)
                """).param("operacion", operacionId).param("importe", importe).param("concepto", concepto)
                .param("detalle", detalle).param("ahora", java.sql.Timestamp.from(Instant.now(reloj))).update();
    }

    @Transactional(readOnly = true)
    public BigDecimal saldo() {
        return jdbc.sql("SELECT COALESCE(SUM(importe), 0) FROM movimientos_reserva").query(BigDecimal.class).single().setScale(2);
    }
}
