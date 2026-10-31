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
@Table(name = "tokens_refresco")
@Getter
@NoArgsConstructor
public class TokenRefresco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String hashToken;
    private Instant expiraEn;
    private Instant revocadoEn;
    private Instant creadoEn;

    public TokenRefresco(Long usuarioId, String hashToken, Instant expiraEn, Instant creadoEn) {
        this.usuarioId = usuarioId;
        this.hashToken = hashToken;
        this.expiraEn = expiraEn;
        this.creadoEn = creadoEn;
    }

    public boolean vigente(Instant ahora) {
        return revocadoEn == null && expiraEn.isAfter(ahora);
    }

    public void revocar(Instant ahora) {
        revocadoEn = ahora;
    }
}
