package io.github.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class UnavailableLogLevelBridgeTest {

    private final LogLevelBridge bridge = new UnavailableLogLevelBridge();

    @Test
    @DisplayName("reports itself unavailable")
    void isNotAvailable() {
        assertThat(bridge.isAvailable()).isFalse();
        assertThat(bridge.backendName()).isEqualTo("none");
    }

    @Test
    @DisplayName("throws rather than silently discarding a level change")
    void throwsOnSetLevel() {
        assertThatIllegalStateException()
                .isThrownBy(() -> bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.DEBUG))
                .withMessageContaining("No supported SLF4J backend is bound")
                .withMessageContaining("logback-classic")
                .withMessageContaining("log4j-slf4j2-impl")
                .withMessageContaining("slf4j-jdk14");
    }
}
