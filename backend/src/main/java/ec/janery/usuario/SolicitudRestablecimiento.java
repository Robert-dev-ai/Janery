package ec.janery.usuario;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pedido de una persona que olvidó su contraseña; lo atiende la presidencia. */
@Entity
@Table(name = "solicitudes_restablecimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRestablecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String estado;
    private Long atendidaPor;
    private Instant atendidaEn;
    private Instant creadoEn;
}
