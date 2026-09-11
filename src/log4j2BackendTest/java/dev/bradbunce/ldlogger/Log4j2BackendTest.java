package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs with SLF4J bound to Log4j 2 via {@code log4j-slf4j2-impl}. A single
 * classpath can only have one backend bound, so this is the only way to prove
 * detection selects the Log4j 2 bridge rather than merely compiling against it.
 */
class Log4j2BackendTest {

    private static final String TEST_LOGGER = "ldlogger.log4j2.test.target";

    private final Log4j2LogLevelBridge bridge = new Log4j2LogLevelBridge();

    @AfterEach
    void resetConfiguration() {
        // Drop any level this test set, so tests stay independent.
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.reconfigure();
    }

    @Test
    @DisplayName("SLF4J really is bound to Log4j 2 here")
    void log4j2IsBound() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .isEqualTo("org.apache.logging.slf4j.Log4jLoggerFactory");
    }

    @Test
    @DisplayName("detect() selects the Log4j 2 bridge")
    void detectSelectsLog4j2() {
        LogLevelBridge detected = LogLevelBridge.detect();

        assertThat(detected).isInstanceOf(Log4j2LogLevelBridge.class);
        assertThat(detected.isAvailable()).isTrue();
        assertThat(detected.backendName()).isEqualTo("Log4j 2");
    }

    @Test
    @DisplayName("the Logback bridge stays unavailable, since Logback is not bound")
    void logbackBridgeIsUnavailable() {
        assertThat(new LogbackLogLevelBridge().isAvailable()).isFalse();
        assertThat(new JulLogLevelBridge().isAvailable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        // Log4j 2 has a FATAL level, but a logger pinned to it would discard
        // log.error(...) from SLF4J call sites, so FATAL maps to ERROR.
        "FATAL, ERROR",
        "ERROR, ERROR",
        "WARN,  WARN",
        "INFO,  INFO",
        "DEBUG, DEBUG",
        "TRACE, TRACE",
    })
    @DisplayName("every level maps onto the equivalent Log4j 2 level")
    void mapsEveryLevel(LogLevel level, String expectedLog4jLevel) {
        bridge.setLevel(TEST_LOGGER, level);

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        assertThat(context.getLogger(TEST_LOGGER).getLevel())
                .hasToString(expectedLog4jLevel);
    }

    @Test
    @DisplayName("a level change is visible to SLF4J loggers obtained beforehand")
    void affectsAlreadyObtainedLoggers() {
        Logger alreadyHeld = LoggerFactory.getLogger(TEST_LOGGER);
        bridge.setLevel(TEST_LOGGER, LogLevel.ERROR);
        assertThat(alreadyHeld.isDebugEnabled()).isFalse();

        bridge.setLevel(TEST_LOGGER, LogLevel.TRACE);

        assertThat(alreadyHeld.isTraceEnabled()).isTrue();
    }

    @Test
    @DisplayName("the root logger is targeted by SLF4J's name, not Log4j 2's empty string")
    void translatesTheRootLoggerName() {
        // SLF4J calls the root logger "ROOT"; Log4j 2 calls it "". Without the
        // translation this would configure a logger literally named ROOT.
        bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.TRACE);

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        assertThat(context.getRootLogger().getLevel()).hasToString("TRACE");
        assertThat(LoggerFactory.getLogger("some.unconfigured.logger").isTraceEnabled()).isTrue();
    }

    @Test
    @DisplayName("the controller drives Log4j 2 end to end")
    void controllerWorksEndToEnd() {
        FakeLDClient client = new FakeLDClient()
                .withIntFlag("console-log-level", ConsoleLogLevel.DEBUG.flagValue());
        LDLogLevelController controller = LDLogLevelController
                .builder(client, LDContext.create("checkout-service"))
                .consoleLogFlagKey("console-log-level")
                .applicationLoggerName(TEST_LOGGER)
                .build();

        controller.start();

        assertThat(controller.bridge()).isInstanceOf(Log4j2LogLevelBridge.class);
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isDebugEnabled()).isTrue();

        client.tracker().fireValueChange("console-log-level", LDValue.of(2));

        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isDebugEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isWarnEnabled()).isTrue();
    }
}
