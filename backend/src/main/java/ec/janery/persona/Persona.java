package ec.janery.persona;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "personas")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String nombres;
    private String apellidos;
    private String identificacion;
    private String telefono;
    private String direccion;
    private Instant creadoEn;
    private String lugarTrabajo;
    private String ocupacion;
    private String antiguedadTrabajo;
    private BigDecimal ingresosMensuales;
    private LocalDate fechaNacimiento;
    private String estadoCivil;
    private String referenciaNombre;
    private String referenciaTelefono;
    private String banco;
    private String tipoCuentaBanco;
    private String numeroCuentaBanco;

    public void actualizarDatosCredito(PersonaController.DatosCreditoRequest r) {
        lugarTrabajo = r.lugarTrabajo().trim();
        ocupacion = r.ocupacion().trim();
        antiguedadTrabajo = r.antiguedadTrabajo().trim();
        ingresosMensuales = r.ingresosMensuales();
        fechaNacimiento = r.fechaNacimiento();
        estadoCivil = r.estadoCivil();
        referenciaNombre = r.referenciaNombre().trim();
        referenciaTelefono = r.referenciaTelefono().trim();
        banco = r.banco().trim();
        tipoCuentaBanco = r.tipoCuentaBanco();
        numeroCuentaBanco = r.numeroCuentaBanco().trim();
    }

    /** Lo que se exige para pedir un crédito a letras. Quien es socio no se bloquea. */
    public boolean datosCreditoCompletos() {
        return direccion != null && !direccion.isBlank() && lugarTrabajo != null && ingresosMensuales != null
                && fechaNacimiento != null && banco != null;
    }

    public void actualizarContacto(String telefono, String direccion) {
        this.telefono = telefono;
        this.direccion = direccion;
    }

    private static final java.util.Locale ESPANOL = java.util.Locale.of("es", "EC");

    /**
     * Mayúscula inicial en cada palabra y el resto en minúsculas, como sea que se haya escrito: "mARÍA  josé" queda
     * "María José". También tras un guion ("Ruiz-pérez" queda "Ruiz-Pérez").
     */
    public static String formatearNombre(String texto) {
        StringBuilder resultado = new StringBuilder();
        boolean inicio = true;
        for (char c : texto.trim().replaceAll("\\s+", " ").toLowerCase(ESPANOL).toCharArray()) {
            resultado.append(inicio ? Character.toUpperCase(c) : c);
            inicio = c == ' ' || c == '-';
        }
        return resultado.toString();
    }
}
