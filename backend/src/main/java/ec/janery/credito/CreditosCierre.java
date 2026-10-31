package ec.janery.credito;

import org.springframework.stereotype.Component;

import ec.janery.common.NegocioException;
import ec.janery.cuenta.RestriccionCierre;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CreditosCierre implements RestriccionCierre {

    private final CreditoService.CreditoRepository creditos;

    @Override
    public void verificar(Long personaId) {
        if (creditos.tieneObligaciones(personaId)) {
            throw NegocioException.conflicto("CREDITOS_PENDIENTES", "Tiene créditos pendientes");
        }
    }
}
