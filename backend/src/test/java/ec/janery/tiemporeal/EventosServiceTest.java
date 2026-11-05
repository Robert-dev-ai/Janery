package ec.janery.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventosServiceTest {

    @Test
    void cadaPersonaTieneUnMaximoDeConexionesYLaMasAntiguaSeCierra() {
        EventosService eventos = new EventosService();
        for (int i = 0; i < EventosService.MAXIMO_POR_USUARIO + 3; i++) {
            eventos.conectar(1L);
        }
        eventos.conectar(2L);

        assertThat(eventos.conexionesDe(1L)).isEqualTo(EventosService.MAXIMO_POR_USUARIO);
        assertThat(eventos.conexionesDe(2L)).isEqualTo(1);
    }
}
