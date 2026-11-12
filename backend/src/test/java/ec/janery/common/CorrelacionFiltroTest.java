package ec.janery.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelacionFiltroTest {

    private final CorrelacionFiltro filtro = new CorrelacionFiltro();

    @Test
    void elCodigoDeLaRespuestaEsElMismoQueVeElLogDuranteLaPeticion() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<String> enLaPeticion = new AtomicReference<>();

        filtro.doFilter(new MockHttpServletRequest(), respuesta, (req, res) -> enLaPeticion.set(CorrelacionFiltro.actual()));

        assertThat(enLaPeticion.get()).isNotBlank().isEqualTo(respuesta.getHeader(CorrelacionFiltro.CABECERA));
        assertThat(MDC.get("correlacion")).isNull();
    }

    @Test
    void respetaElCodigoDelProxySoloSiEsSeguroParaElLog() throws Exception {
        assertThat(codigoCon("abc-12345-proxy")).isEqualTo("abc-12345-proxy");
        assertThat(codigoCon("x\nLINEA FALSA EN EL LOG")).isNotEqualTo("x\nLINEA FALSA EN EL LOG").doesNotContain("\n");
    }

    private String codigoCon(String cabecera) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader(CorrelacionFiltro.CABECERA, cabecera);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, (req, res) -> { });
        return respuesta.getHeader(CorrelacionFiltro.CABECERA);
    }
}
