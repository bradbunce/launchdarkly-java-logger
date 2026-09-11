package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exercises the bridge against the real Logback context bound in this test run.
 * Levels touched here are restored afterwards so tests stay independent.
 */
class LogbackLogLevelBridgeTest {

    private static final String TEST_LOGGER = "ldlogger.bridge.test.target";

    private final LogbackLogLevelBridge bridge = new LogbackLogLevelBridge();
    private final Map<String, ch.qos.logback.classic.Level> savedLevels = new HashMap<>();

    @AfterEach
    void restoreLevels() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        savedLevels.forEach((name, level) -> context.getLogger(name).setLevel(level));
        savedLevels.clear();
    }

    private void save(String loggerName) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        savedLevels.putIfAbsent(loggerName, context.getLogger(loggerName).getLevel());
    }

    @Test
    @DisplayName("reports itself available when Logback is the bound backend")
    void isAvailableUnderLogback() {
        assertThat(LogbackLogLevelBridge.isLogbackBound()).isTrue();
        assertThat(bridge.isAvailable()).isTrue();
        assertThat(bridge.backendName()).isEqualTo("Logback");
    }

    @ParameterizedTest
    @CsvSource({
        // Logback has no FATAL, so the most severe level it can express is ERROR.
        "FATAL, ERROR",
        "ERROR, ERROR",
        "WARN,  WARN",
        "INFO,  INFO",
        "DEBUG, DEBUG",
        "TRACE, TRACE",
    })
    @DisplayName("every level maps onto the equivalent Logback level")
    void mapsEveryLevel(LogLevel level, String expectedLogbackLevel) {
        save(TEST_LOGGER);

        bridge.setLevel(TEST_LOGGER, level);

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertThat(context.getLogger(TEST_LOGGER).getLevel())
                .hasToString(expectedLogbackLevel);
    }

    @Test
    @DisplayName("a level change is visible to loggers obtained before the change")
    void affectsAlreadyObtainedLoggers() {
        save(TEST_LOGGER);
        // This is the library's core promise: call sites that captured a logger
        // at class-init time still observe later level changes.
        Logger alreadyHeld = LoggerFactory.getLogger(TEST_LOGGER);
        bridge.setLevel(TEST_LOGGER, LogLevel.ERROR);
        assertThat(alreadyHeld.isDebugEnabled()).isFalse();

        bridge.setLevel(TEST_LOGGER, LogLevel.TRACE);

        assertThat(alreadyHeld.isTraceEnabled()).isTrue();
        assertThat(alreadyHeld.isDebugEnabled()).isTrue();
    }

    @Test
    @DisplayName("setting a level enables that level and every more severe one")
    void enablesTheLevelAndAboveIt() {
        save(TEST_LOGGER);

        bridge.setLevel(TEST_LOGGER, LogLevel.INFO);

        Logger logger = LoggerFactory.getLogger(TEST_LOGGER);
        assertThat(logger.isErrorEnabled()).isTrue();
        assertThat(logger.isWarnEnabled()).isTrue();
        assertThat(logger.isInfoEnabled()).isTrue();
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isTraceEnabled()).isFalse();
    }

    @Test
    @DisplayName("the root logger can be targeted by name, affecting unconfigured loggers")
    void setsTheRootLogger() {
        save(Logger.ROOT_LOGGER_NAME);
        save(TEST_LOGGER);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Clear any explicit level so this logger inherits from root.
        context.getLogger(TEST_LOGGER).setLevel(null);

        bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.TRACE);

        assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).hasToString("TRACE");
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isTraceEnabled()).isTrue();
    }

    @Test
    @DisplayName("a child logger's explicit level overrides the root level")
    void childLevelOverridesRoot() {
        save(Logger.ROOT_LOGGER_NAME);
        save(TEST_LOGGER);

        bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.ERROR);
        bridge.setLevel(TEST_LOGGER, LogLevel.DEBUG);

        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isDebugEnabled()).isTrue();
        assertThat(LoggerFactory.getLogger("some.other.logger").isDebugEnabled()).isFalse();
    }

    @Test
    @DisplayName("naming a logger that does not exist yet creates and configures it")
    void createsUnknownLoggers() {
        String fresh = TEST_LOGGER + ".created." + System.nanoTime();
        save(fresh);

        bridge.setLevel(fresh, LogLevel.WARN);

        assertThat(LoggerFactory.getLogger(fresh).isWarnEnabled()).isTrue();
        assertThat(LoggerFactory.getLogger(fresh).isInfoEnabled()).isFalse();
    }

    @Test
    @DisplayName("repeated changes to the same logger take effect in order")
    void appliesRepeatedChanges() {
        save(TEST_LOGGER);

        bridge.setLevel(TEST_LOGGER, LogLevel.TRACE);
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isTraceEnabled()).isTrue();

        bridge.setLevel(TEST_LOGGER, LogLevel.WARN);
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isTraceEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger(TEST_LOGGER).isWarnEnabled()).isTrue();
    }
}
