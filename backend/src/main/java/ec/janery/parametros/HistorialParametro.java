package ec.janery.parametros;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "historial_parametros")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialParametro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long parametroId;
    private BigDecimal valorAnterior;
    private BigDecimal valorNuevo;
    private Instant vigenteDesde;
    private Long actorId;
    private String motivo;
    private Instant creadoEn;
}
