package io.github.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.Level;

class LogLevelTest {

    @ParameterizedTest
    @CsvSource({
        "FATAL, ERROR",
        "ERROR, ERROR",
        "WARN,  WARN",
        "INFO,  INFO",
        "DEBUG, DEBUG",
        "TRACE, TRACE",
    })
    @DisplayName("each level reports its nearest SLF4J level")
    void mapsToSlf4jLevels(LogLevel level, Level slf4jLevel) {
        assertThat(level.slf4jLevel()).isEqualTo(slf4jLevel);
    }

    @Test
    @DisplayName("FATAL collapses onto SLF4J ERROR, since SLF4J has no FATAL")
    void fatalCollapsesToError() {
        assertThat(LogLevel.FATAL.slf4jLevel()).isEqualTo(Level.ERROR);
        assertThat(LogLevel.FATAL.slf4jLevel()).isEqualTo(LogLevel.ERROR.slf4jLevel());
        // The distinction survives here even though the SLF4J mapping loses it,
        // which is the reason bridges receive this type rather than Level.
        assertThat(LogLevel.FATAL).isNotEqualTo(LogLevel.ERROR);
    }

    @Test
    @DisplayName("covers every level both flag enums can produce")
    void isASupersetOfBothFlagEnums() {
        assertThat(ConsoleLogLevel.values())
                .extracting(ConsoleLogLevel::logLevel)
                .containsExactly(
                        LogLevel.FATAL,
                        LogLevel.ERROR,
                        LogLevel.WARN,
                        LogLevel.INFO,
                        LogLevel.DEBUG,
                        LogLevel.TRACE);
        assertThat(SdkLogLevel.values())
                .extracting(SdkLogLevel::logLevel)
                .containsExactly(
                        LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO, LogLevel.DEBUG);
    }

    @ParameterizedTest
    @EnumSource(LogLevel.class)
    @DisplayName("every level is reachable from a console flag value")
    void everyLevelIsReachable(LogLevel level) {
        assertThat(ConsoleLogLevel.values())
                .anySatisfy(console -> assertThat(console.logLevel()).isEqualTo(level));
    }

    @Test
    @DisplayName("levels are declared most severe first")
    void isOrderedBySeverity() {
        assertThat(LogLevel.values())
                .containsExactly(
                        LogLevel.FATAL,
                        LogLevel.ERROR,
                        LogLevel.WARN,
                        LogLevel.INFO,
                        LogLevel.DEBUG,
                        LogLevel.TRACE);
    }

    @Test
    @DisplayName("exposes no numeric value, so callers cannot compare levels unsafely")
    void exposesNoNumericValue() {
        // Frameworks number levels inconsistently and some invert the ordering,
        // so a number here would only invite broken threshold checks.
        assertThat(LogLevel.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("intValue")
                        || method.getName().equals("toInt"));
    }
}
