package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.launchdarkly.sdk.LDContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

/**
 * Runs with logback-classic on the classpath while SLF4J is deliberately bound
 * to a different backend. Mutating Logback's context would then have no effect
 * on the application's actual logging, so the library must refuse rather than
 * appear to work.
 */
class MismatchedLoggingBackendTest {

    private final LDContext context = LDContext.create("checkout-service");
    private final FakeLDClient client = new FakeLDClient();

    @Test
    @DisplayName("Logback is on the classpath but is not the bound backend")
    void logbackIsPresentButNotBound() throws ClassNotFoundException {
        assertThatNoException()
                .isThrownBy(() -> Class.forName("ch.qos.logback.classic.LoggerContext"));
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(NOPLoggerFactory.class);
    }

    @Test
    @DisplayName("the Logback bridge is unavailable when Logback is not the bound backend")
    void bridgeIsUnavailable() {
        assertThat(LogbackLogLevelBridge.isLogbackBound()).isFalse();
        assertThat(new LogbackLogLevelBridge().isAvailable()).isFalse();
    }

    @Test
    @DisplayName("setLevel refuses, naming the backend that is actually bound")
    void setLevelRefusesAndNamesTheBoundBackend() {
        LogbackLogLevelBridge bridge = new LogbackLogLevelBridge();

        assertThatIllegalStateException()
                .isThrownBy(() -> bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.DEBUG))
                .withMessageContaining("SLF4J is bound to")
                .withMessageContaining(NOPLoggerFactory.class.getName())
                .withMessageContaining("not Logback");
    }

    @Test
    @DisplayName("a backend on the classpath but not bound is not claimed")
    void presentButUnboundBackendsAreNotClaimed() throws ClassNotFoundException {
        // log4j-core is on this classpath, so the class-presence half of the
        // check passes; only the binding check rules it out.
        assertThatNoException().isThrownBy(() ->
                Class.forName("org.apache.logging.log4j.core.config.Configurator"));

        assertThat(new Log4j2LogLevelBridge().isAvailable()).isFalse();
        assertThat(new LogbackLogLevelBridge().isAvailable()).isFalse();
        assertThat(new JulLogLevelBridge().isAvailable()).isFalse();
    }

    @Test
    @DisplayName("detect() does not pick Logback just because it is on the classpath")
    void detectDoesNotPickLogback() {
        LogLevelBridge bridge = LogLevelBridge.detect();

        assertThat(bridge).isInstanceOf(UnavailableLogLevelBridge.class);
        assertThat(bridge.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("a controller using the detected bridge stays inactive")
    void controllerIsInactive() {
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .consoleLogFlagKey("console-log-level")
                .build();

        controller.start();

        assertThat(controller.bridge().isAvailable()).isFalse();
        assertThat(client.intVariationCalls()).isEmpty();
        assertThat(client.tracker().registrations()).isEmpty();
    }
}
