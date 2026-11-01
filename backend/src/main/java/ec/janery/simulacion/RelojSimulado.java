package ec.janery.simulacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El tiempo real más un desplazamiento que solo crece: volver atrás dejaría movimientos con fecha futura.
 * El desplazamiento vive en la base simulada, para que reiniciar el backend no retroceda la fecha.
 */
class RelojSimulado extends Clock {

    private final Clock real;
    private final Desplazamiento desplazamiento;

    RelojSimulado(Clock real, JdbcTemplate jdbc) {
        this(real, new Desplazamiento(jdbc));
    }

    private RelojSimulado(Clock real, Desplazamiento desplazamiento) {
        this.real = real;
        this.desplazamiento = desplazamiento;
    }

    @Override
    public ZoneId getZone() {
        return real.getZone();
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return new RelojSimulado(real.withZone(zona), desplazamiento);
    }

    @Override
    public Instant instant() {
        return real.instant().plusSeconds(desplazamiento.segundos);
    }

    /** Un instante que no sea posterior al actual no tiene efecto. */
    void avanzarHasta(Instant destino) {
        long segundos = Duration.between(real.instant(), destino).getSeconds();
        if (segundos > desplazamiento.segundos) {
            desplazamiento.guardar(segundos);
        }
    }

    long diasAdelantados() {
        return ChronoUnit.DAYS.between(LocalDate.now(real), LocalDate.now(this));
    }

    private static final class Desplazamiento {

        private final JdbcTemplate jdbc;
        private volatile long segundos;

        Desplazamiento(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            // Fuera de las migraciones a propósito: esta tabla no debe existir en producción.
            jdbc.execute("""
                    create table if not exists simulacion_reloj (
                        id int primary key check (id = 1),
                        desplazamiento_segundos bigint not null
                    )""");
            segundos = jdbc.queryForList("select desplazamiento_segundos from simulacion_reloj", Long.class)
                    .stream().findFirst().orElse(0L);
        }

        void guardar(long nuevos) {
            jdbc.update("""
                    insert into simulacion_reloj (id, desplazamiento_segundos) values (1, ?)
                    on conflict (id) do update set desplazamiento_segundos = excluded.desplazamiento_segundos""", nuevos);
            segundos = nuevos;
        }
    }
}
