package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Checks that a bridge for a backend that is not bound refuses to act.
 *
 * <p>This suite runs with Logback bound, so the Log4j 2 and JUL bridges are both
 * the wrong bridge here - which is exactly the situation being tested. Each
 * backend's happy path lives in its own source set.
 */
class BridgeGuardsTest {

    @Test
    @DisplayName("the Log4j 2 bridge is unavailable when Log4j 2 is not bound")
    void log4j2BridgeIsUnavailable() {
        Log4j2LogLevelBridge bridge = new Log4j2LogLevelBridge();

        assertThat(bridge.isAvailable()).isFalse();
        assertThat(bridge.backendName()).isEqualTo("Log4j 2");
    }

    @Test
    @DisplayName("the Log4j 2 bridge refuses to set a level, naming the bound backend")
    void log4j2BridgeRefusesToSetLevel() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new Log4j2LogLevelBridge().setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.DEBUG))
                .withMessageContaining("SLF4J is bound to")
                .withMessageContaining("LoggerContext")
                .withMessageContaining("not Log4j 2");
    }

    @Test
    @DisplayName("the java.util.logging bridge is unavailable when JUL is not bound")
    void julBridgeIsUnavailable() {
        JulLogLevelBridge bridge = new JulLogLevelBridge();

        assertThat(bridge.isAvailable()).isFalse();
        assertThat(bridge.backendName()).isEqualTo("java.util.logging");
    }

    @Test
    @DisplayName("the java.util.logging bridge refuses to set a level")
    void julBridgeRefusesToSetLevel() {
        // Without this guard the bridge would happily set a JUL level that
        // nothing publishes, looking like it worked.
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new JulLogLevelBridge().setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.DEBUG))
                .withMessageContaining("not java.util.logging");
    }

    @Test
    @DisplayName("the bound factory is reported for diagnostics")
    void reportsTheBoundFactory() {
        assertThat(Backends.boundFactoryName()).isEqualTo("ch.qos.logback.classic.LoggerContext");
    }

    @Test
    @DisplayName("class presence is detected without initializing the class")
    void detectsClassPresence() {
        assertThat(Backends.isClassPresent("ch.qos.logback.classic.LoggerContext")).isTrue();
        assertThat(Backends.isClassPresent("org.apache.logging.log4j.core.config.Configurator"))
                .isFalse();
        assertThat(Backends.isClassPresent("does.not.Exist")).isFalse();
    }

    @Test
    @DisplayName("Backends cannot be instantiated")
    void backendsCannotBeInstantiated() throws ReflectiveOperationException {
        Constructor<Backends> constructor = Backends.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
    }
}
