package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs with SLF4J bound to {@code java.util.logging} via {@code slf4j-jdk14}.
 *
 * <p>JUL is the awkward backend: its level names and numbering match nothing
 * else, and a level change only becomes visible if a handler will publish it.
 */
class JulBackendTest {

    private static final String TEST_LOGGER = "ldlogger.jul.test.target";

    private final JulLogLevelBridge bridge = new JulLogLevelBridge();
    private final Map<String, Level> savedLevels = new HashMap<>();

    @AfterEach
    void restoreLevels() {
        savedLevels.forEach((name, level) ->
                java.util.logging.Logger.getLogger(name).setLevel(level));
        savedLevels.clear();
    }

    private void save(String loggerName) {
        savedLevels.putIfAbsent(
                loggerName, java.util.logging.Logger.getLogger(loggerName).getLevel());
    }

    @Test
    @DisplayName("SLF4J really is bound to java.util.logging here")
    void julIsBound() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .isEqualTo("org.slf4j.jul.JDK14LoggerFactory");
    }

    @Test
    @DisplayName("detect() selects the java.util.logging bridge")
    void detectSelectsJul() {
        LogLevelBridge detected = LogLevelBridge.detect();

        assertThat(detected).isInstanceOf(JulLogLevelBridge.class);
        assertThat(detected.isAvailable()).isTrue();
        assertThat(detected.backendName()).isEqualTo("java.util.logging");
    }

    @Test
    @DisplayName("the other bridges stay unavailable, since neither backend is bound")
    void otherBridgesAreUnavailable() {
        assertThat(new LogbackLogLevelBridge().isAvailable()).isFalse();
        assertThat(new Log4j2LogLevelBridge().isAvailable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        // JUL has no FATAL, and its finer levels have no SLF4J equivalent.
        "FATAL, SEVERE",
        "ERROR, SEVERE",
        "WARN,  WARNING",
        "INFO,  INFO",
        "DEBUG, FINE",
        "TRACE, FINEST",
    })
    @DisplayName("every level maps onto the JUL level slf4j-jdk14 expects")
    void mapsEveryLevel(LogLevel level, String expectedJulLevel) {
        save(TEST_LOGGER);

        bridge.setLevel(TEST_LOGGER, level);

        assertThat(java.util.logging.Logger.getLogger(TEST_LOGGER).getLevel())
                .hasToString(expectedJulLevel);
    }

    @Test
    @DisplayName("a level change is visible to SLF4J loggers obtained beforehand")
    void affectsAlreadyObtainedLoggers() {
        save(TEST_LOGGER);
        Logger alreadyHeld = LoggerFactory.getLogger(TEST_LOGGER);
        bridge.setLevel(TEST_LOGGER, LogLevel.ERROR);
        assertThat(alreadyHeld.isDebugEnabled()).isFalse();

        bridge.setLevel(TEST_LOGGER, LogLevel.TRACE);

        assertThat(alreadyHeld.isTraceEnabled()).isTrue();
        assertThat(alreadyHeld.isDebugEnabled()).isTrue();
    }

    @Test
    @DisplayName("the root logger is targeted by SLF4J's name, not JUL's empty string")
    void translatesTheRootLoggerName() {
        save("");
        save(TEST_LOGGER);
        // Clear the explicit level so this logger inherits from root.
        java.util.logging.Logger.getLogger(TEST_LOGGER).setLevel(null);

        bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.TRACE);

        assertThat(java.util.logging.Logger.getLogger("").getLevel()).hasToString("FINEST");
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isTraceEnabled()).isTrue();
        // A logger literally named ROOT must not have been created instead.
        assertThat(savedLevels).doesNotContainKey("ROOT");
    }

    @Test
    @DisplayName("root handlers are opened up, or a finer level would never be published")
    void widensRootHandlers() {
        save("");
        bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.TRACE);

        // JUL's default console handler sits at INFO, so without this the level
        // change would be invisible in the output despite the logger accepting it.
        assertThat(java.util.logging.Logger.getLogger("").getHandlers())
                .allSatisfy(handler -> assertThat(handler.getLevel().intValue())
                        .isLessThanOrEqualTo(Level.FINEST.intValue()));
    }

    @Test
    @DisplayName("the controller drives java.util.logging end to end")
    void controllerWorksEndToEnd() {
        save(TEST_LOGGER);
        FakeLDClient client = new FakeLDClient()
                .withIntFlag("console-log-level", ConsoleLogLevel.DEBUG.flagValue());
        LDLogLevelController controller = LDLogLevelController
                .builder(client, LDContext.create("checkout-service"))
                .consoleLogFlagKey("console-log-level")
                .applicationLoggerName(TEST_LOGGER)
                .build();

        controller.start();

        assertThat(controller.bridge()).isInstanceOf(JulLogLevelBridge.class);
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isDebugEnabled()).isTrue();

        client.tracker().fireValueChange("console-log-level", LDValue.of(2));

        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isDebugEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isWarnEnabled()).isTrue();
    }
}
