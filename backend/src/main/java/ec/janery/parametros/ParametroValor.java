package ec.janery.parametros;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parametro_valores")
@Getter
@NoArgsConstructor
public class ParametroValor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long parametroId;
    private BigDecimal valor;
    private Instant vigenteDesde;
    private Instant vigenteHasta;
    private Long creadoPor;
    private String motivo;
    private Instant creadoEn;

    ParametroValor(Long parametroId, BigDecimal valor, Instant vigenteDesde, Long creadoPor, String motivo,
            Instant creadoEn) {
        this.parametroId = parametroId;
        this.valor = valor;
        this.vigenteDesde = vigenteDesde;
        this.creadoPor = creadoPor;
        this.motivo = motivo;
        this.creadoEn = creadoEn;
    }

    void cerrar(Instant hasta) {
        vigenteHasta = hasta;
    }
}
