package ec.janery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {

    @Test
    void rechazaSecretoAusenteOCorto() {
        SecurityConfig config = new SecurityConfig();

        assertThatThrownBy(() -> config.claveJwt(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.claveJwt("demasiado-corto"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soloLasLecturasFueraDeLaApiSonDelFrontend() {
        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("GET", "/"))).isTrue();
        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("GET", "/creditos"))).isTrue();
        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("GET", "/main-ABC123.js"))).isTrue();

        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("GET", "/api/v1/cuentas"))).isFalse();
        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("GET", "/api"))).isFalse();
        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("GET", "/actuator/health"))).isFalse();
        assertThat(SecurityConfig.esFrontend(new MockHttpServletRequest("POST", "/creditos"))).isFalse();
    }

    @Test
    void lasRutasDeAngularDevuelvenLaAplicacionPeroNoLosArchivosQueFaltan() {
        assertThat(FrontendConfig.esRutaDeLaAplicacion("creditos")).isTrue();
        assertThat(FrontendConfig.esRutaDeLaAplicacion("admin/usuarios")).isTrue();

        assertThat(FrontendConfig.esRutaDeLaAplicacion("api/v1/nada")).isFalse();
        assertThat(FrontendConfig.esRutaDeLaAplicacion("actuator/health")).isFalse();
        assertThat(FrontendConfig.esRutaDeLaAplicacion("main-NOEXISTE.js")).isFalse();
    }
}
