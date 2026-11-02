package ec.janery.credito;

import org.springframework.stereotype.Component;

import ec.janery.common.NegocioException;
import ec.janery.cuenta.RestriccionCierre;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CreditosCierre implements RestriccionCierre {

    private final CreditoService.CreditoRepository creditos;
    private final CreditoService.SolicitudCupoRepository solicitudes;

    @Override
    public void verificar(Long personaId) {
        if (creditos.tieneObligaciones(personaId) || solicitudes.existsByPersonaIdAndEstado(personaId, "PENDIENTE")) {
            throw NegocioException.conflicto("CREDITOS_PENDIENTES", "Tiene créditos pendientes");
        }
    }
}
