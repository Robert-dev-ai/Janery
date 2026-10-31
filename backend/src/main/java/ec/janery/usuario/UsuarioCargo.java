package ec.janery.usuario;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cada fila es un periodo en el cargo: revocar cierra {@code hasta} y conserva el historial. */
@Entity
@Table(name = "usuario_cargos")
@Getter
@NoArgsConstructor
public class UsuarioCargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private Long cargoId;
    private Instant desde;
    private Instant hasta;
    private Long asignadoPor;

    public UsuarioCargo(Long usuarioId, Long cargoId, Instant desde, Long asignadoPor) {
        this.usuarioId = usuarioId;
        this.cargoId = cargoId;
        this.desde = desde;
        this.asignadoPor = asignadoPor;
    }

    public void revocar(Instant ahora) {
        hasta = ahora;
    }
}
