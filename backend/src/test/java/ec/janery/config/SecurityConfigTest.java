package ec.janery.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void rechazaSecretoAusenteOCorto() {
        SecurityConfig config = new SecurityConfig();

        assertThatThrownBy(() -> config.claveJwt(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.claveJwt("demasiado-corto"))
                .isInstanceOf(IllegalStateException.class);
    }
}
