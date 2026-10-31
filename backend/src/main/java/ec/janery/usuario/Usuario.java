package ec.janery.usuario;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuarios")
@Getter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String correo;
    private String hashContrasena;
    private String estado;
    private Instant creadoEn;

    public Usuario(String correo, String hashContrasena, Instant creadoEn) {
        this.correo = correo;
        this.hashContrasena = hashContrasena;
        this.estado = "ACTIVO";
        this.creadoEn = creadoEn;
    }

    public boolean activo() {
        return "ACTIVO".equals(estado);
    }
}
