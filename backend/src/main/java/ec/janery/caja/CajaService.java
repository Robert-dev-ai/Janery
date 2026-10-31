package ec.janery.caja;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Fondos independientes: cada movimiento pertenece a una sola caja. */
@Service
@RequiredArgsConstructor
public class CajaService {

    public static final String PRINCIPAL = "PRINCIPAL";
    public static final String CHICA = "CHICA";
    public static final String FONDO_RESERVA = "FONDO_RESERVA";

    interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {
        @Query(value = "SELECT id FROM cajas WHERE codigo = :codigo", nativeQuery = true)
        Long idDe(@Param("codigo") String codigo);

        @Query(value = "SELECT id FROM cajas WHERE codigo = :codigo FOR UPDATE", nativeQuery = true)
        Long bloquear(@Param("codigo") String codigo);

        @Query("""
                select coalesce(sum(case when m.sentido = 'ENTRADA' then m.importe else -m.importe end), 0)
                from MovimientoCaja m where m.cajaId = :cajaId
                """)
        BigDecimal saldo(@Param("cajaId") Long cajaId);

        Page<MovimientoCaja> findByCajaId(Long cajaId, Pageable pageable);
    }

    private final MovimientoCajaRepository repository;
    private final Clock reloj;

    @Transactional
    public void registrar(String caja, Long operacionId, String sentido, BigDecimal importe, String concepto,
            String detalle) {
        repository.save(new MovimientoCaja(null, repository.idDe(caja), operacionId, sentido, importe, concepto, detalle,
                Instant.now(reloj)));
    }

    @Transactional
    public void registrarEnPrincipal(Long operacionId, String sentido, BigDecimal importe, String concepto) {
        registrar(PRINCIPAL, operacionId, sentido, importe, concepto, null);
    }

    /** Saldo bajo bloqueo de la caja, para validar una salida sin carreras. Requiere transacción. */
    @Transactional
    public BigDecimal saldoBloqueado(String caja) {
        return repository.saldo(repository.bloquear(caja));
    }

    @Transactional(readOnly = true)
    public BigDecimal saldo(String caja) {
        return repository.saldo(repository.idDe(caja));
    }

    @Transactional(readOnly = true)
    public BigDecimal saldoPrincipal() {
        return saldo(PRINCIPAL);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoCaja> movimientos(String caja, int pagina, int tamano) {
        return repository.findByCajaId(repository.idDe(caja),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
    }

    @Transactional(readOnly = true)
    public Page<MovimientoCaja> movimientosPrincipal(int pagina, int tamano) {
        return movimientos(PRINCIPAL, pagina, tamano);
    }
}
