package ec.janery.parametros;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ec.janery.auditoria.AuditoriaService;
import ec.janery.common.NegocioException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParametroService {

    interface ParametroRepository extends JpaRepository<ParametroSistema, Long> {
        Optional<ParametroSistema> findByCodigo(String codigo);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select p from ParametroSistema p where p.id = :id")
        Optional<ParametroSistema> bloquear(@Param("id") Long id);
    }

    interface ValorRepository extends JpaRepository<ParametroValor, Long> {
        @Query("""
                select p.codigo, v.valor from ParametroValor v, ParametroSistema p where v.parametroId = p.id
                and p.codigo in :codigos and v.vigenteDesde <= :ahora and (v.vigenteHasta is null or v.vigenteHasta > :ahora)
                """)
        List<Object[]> vigentes(@Param("codigos") java.util.Collection<String> codigos, @Param("ahora") Instant ahora);

        Optional<ParametroValor> findByParametroIdAndVigenteHastaIsNull(Long parametroId);

        /** El valor vigente y el programado de todos los parámetros: los que aún no se han cerrado antes de ahora. */
        @Query("select v from ParametroValor v where v.vigenteHasta is null or v.vigenteHasta > :ahora")
        List<ParametroValor> noVencidos(@Param("ahora") Instant ahora);
    }

    interface HistorialRepository extends JpaRepository<HistorialParametro, Long> {
        List<HistorialParametro> findByParametroIdOrderByIdDesc(Long parametroId);
    }

    public record ParametroVista(ParametroSistema parametro, ParametroValor vigente, ParametroValor programado) {
    }

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ParametroRepository parametros;
    private final ValorRepository valores;
    private final HistorialRepository historial;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    /** Varios valores vigentes en una sola consulta. Falla si falta alguno: nunca se usa uno supuesto. */
    @Transactional(readOnly = true)
    public java.util.Map<String, BigDecimal> valoresVigentes(java.util.Collection<String> codigos) {
        java.util.Map<String, BigDecimal> resultado = new java.util.HashMap<>();
        valores.vigentes(codigos, Instant.now(reloj)).forEach(f -> resultado.put((String) f[0], (BigDecimal) f[1]));
        for (String codigo : codigos) {
            if (!resultado.containsKey(codigo)) {
                throw NegocioException.conflicto("PARAMETRO_SIN_VALOR", "El parámetro " + codigo + " no tiene un valor vigente");
            }
        }
        return resultado;
    }

    /** Valor oficial vigente ahora. Falla si el parámetro no tiene valor: nunca se usa uno supuesto. */
    @Transactional(readOnly = true)
    public BigDecimal valorVigente(String codigo) {
        // Una sola consulta: este método se llama en casi todas las operaciones.
        return valoresVigentes(List.of(codigo)).get(codigo);
    }

    @Transactional(readOnly = true)
    public List<ParametroVista> listar() {
        Instant ahora = Instant.now(reloj);
        java.util.Map<Long, List<ParametroValor>> porParametro = valores.noVencidos(ahora).stream()
                .collect(java.util.stream.Collectors.groupingBy(ParametroValor::getParametroId));
        return parametros.findAll().stream()
                .filter(ParametroSistema::isActivo)
                .sorted(java.util.Comparator.comparing(ParametroSistema::getCodigo))
                .map(p -> {
                    List<ParametroValor> propios = porParametro.getOrDefault(p.getId(), List.of());
                    ParametroValor vigente = propios.stream().filter(v -> !v.getVigenteDesde().isAfter(ahora))
                            .findFirst().orElse(null);
                    ParametroValor abierto = propios.stream().filter(v -> v.getVigenteHasta() == null)
                            .findFirst().orElse(null);
                    boolean programado = abierto != null && abierto.getVigenteDesde().isAfter(ahora);
                    return new ParametroVista(p, vigente, programado ? abierto : null);
                })
                .toList();
    }

    /** Cierra el valor abierto y crea uno nuevo. Las operaciones ya hechas conservan el valor que usaron. */
    @Transactional
    public void cambiarValor(Long actorId, Long parametroId, BigDecimal valor, Instant vigenteDesde, String motivo) {
        ParametroSistema parametro = parametros.bloquear(parametroId)
                .orElseThrow(() -> NegocioException.noEncontrado("Parámetro no encontrado"));
        validarRango(parametro, valor);
        Instant ahora = Instant.now(reloj);
        Instant desde = vigenteDesde == null ? ahora : vigenteDesde;
        if (desde.isBefore(ahora.minusSeconds(60))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "VIGENCIA_RETROACTIVA",
                    "La vigencia no puede empezar en el pasado");
        }
        ParametroValor abierto = valores.findByParametroIdAndVigenteHastaIsNull(parametroId).orElse(null);
        BigDecimal anterior = null;
        if (abierto != null) {
            if (!desde.isAfter(abierto.getVigenteDesde())) {
                throw NegocioException.conflicto("VIGENCIA_SOLAPADA",
                        "La vigencia debe empezar después de la del último valor registrado");
            }
            anterior = abierto.getValor();
            abierto.cerrar(desde);
            valores.saveAndFlush(abierto);
        }
        valores.save(new ParametroValor(parametroId, valor, desde, actorId, motivo, ahora));
        historial.save(new HistorialParametro(null, parametroId, anterior, valor, desde, actorId, motivo, ahora));
        auditoria.registrar(actorId, "PARAMETRO_CAMBIADO", "PARAMETROS", parametro.getCodigo(), parametroId,
                anterior == null ? null : anterior.toPlainString(), valor.toPlainString(), motivo);
    }

    @Transactional(readOnly = true)
    public List<HistorialParametro> historial(Long parametroId) {
        if (!parametros.existsById(parametroId)) {
            throw NegocioException.noEncontrado("Parámetro no encontrado");
        }
        return historial.findByParametroIdOrderByIdDesc(parametroId);
    }

    private void validarRango(ParametroSistema parametro, BigDecimal valor) {
        if (parametro.getUnidad().startsWith("PCT") && valor.compareTo(CIEN) > 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "VALOR_FUERA_DE_RANGO",
                    "Un porcentaje no puede superar 100");
        }
        if ("MES".equals(parametro.getUnidad())
                && (valor.stripTrailingZeros().scale() > 0 || valor.intValue() < 1 || valor.intValue() > 12)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "VALOR_FUERA_DE_RANGO",
                    "El mes debe ser un número entero entre 1 y 12");
        }
    }
}
