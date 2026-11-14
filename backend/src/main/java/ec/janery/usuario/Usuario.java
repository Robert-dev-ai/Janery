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
    private int intentosFallidos;
    private boolean debeCambiarContrasena;

    public Usuario(String correo, String hashContrasena, Instant creadoEn) {
        this.correo = correo;
        this.hashContrasena = hashContrasena;
        this.estado = "ACTIVO";
        this.creadoEn = creadoEn;
    }

    public static final int INTENTOS_MAXIMOS = 3;

    public void cambiarContrasena(String hashContrasena) {
        this.hashContrasena = hashContrasena;
        this.debeCambiarContrasena = false;
    }

    /** Contraseña que entrega el presidente: desbloquea el acceso y obliga a cambiarla al entrar. */
    public void restablecer(String hashTemporal) {
        this.hashContrasena = hashTemporal;
        this.debeCambiarContrasena = true;
        this.intentosFallidos = 0;
        this.estado = "ACTIVO";
    }

    /** Contraseña elegida por la persona con el enlace de su correo: la eligió ella, así que no hay que cambiarla otra vez. */
    public void recuperar(String hashContrasena) {
        this.hashContrasena = hashContrasena;
        this.debeCambiarContrasena = false;
        this.intentosFallidos = 0;
        this.estado = "ACTIVO";
    }

    public void cambiarCorreo(String correo) {
        this.correo = correo;
    }

    public boolean bloqueado() {
        return "BLOQUEADO".equals(estado);
    }

    public boolean activo() {
        return "ACTIVO".equals(estado);
    }
}
