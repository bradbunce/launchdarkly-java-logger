package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

class SdkLogLevelTest {

    @ParameterizedTest
    @CsvSource({
        "ERROR, error, ERROR",
        "WARN,  warn,  WARN",
        "INFO,  info,  INFO",
        "DEBUG, debug, DEBUG",
    })
    @DisplayName("each level has the SDK's string flag value and the matching SLF4J level")
    void mapsFlagValuesAndSlf4jLevels(SdkLogLevel level, String flagValue, Level slf4jLevel) {
        assertThat(level.flagValue()).isEqualTo(flagValue);
        assertThat(level.slf4jLevel()).isEqualTo(slf4jLevel);
    }

    @ParameterizedTest
    @EnumSource(SdkLogLevel.class)
    @DisplayName("every level round-trips through its flag value")
    void roundTripsThroughFlagValue(SdkLogLevel level) {
        assertThat(SdkLogLevel.fromFlagValue(level.flagValue())).contains(level);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEBUG", "Debug", "dEbUg"})
    @DisplayName("flag values resolve case-insensitively")
    void resolvesCaseInsensitively(String flagValue) {
        assertThat(SdkLogLevel.fromFlagValue(flagValue)).contains(SdkLogLevel.DEBUG);
    }

    @ParameterizedTest
    @ValueSource(strings = {" debug", "debug ", "  debug  ", "\tdebug\n"})
    @DisplayName("surrounding whitespace is ignored")
    void trimsWhitespace(String flagValue) {
        assertThat(SdkLogLevel.fromFlagValue(flagValue)).contains(SdkLogLevel.DEBUG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "verbose", "fatal", "warning", "err", "debugg", "0"})
    @DisplayName("unrecognized flag values resolve to empty")
    void rejectsUnknownFlagValues(String flagValue) {
        assertThat(SdkLogLevel.fromFlagValue(flagValue)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("a null flag value resolves to empty rather than throwing")
    void rejectsNull(String flagValue) {
        assertThat(SdkLogLevel.fromFlagValue(flagValue)).isEmpty();
    }

    @Test
    @DisplayName("there is no TRACE level, matching com.launchdarkly.logging.LDLogLevel")
    void hasNoTraceLevel() {
        assertThat(SdkLogLevel.fromFlagValue("trace")).isEmpty();
        assertThat(SdkLogLevel.values())
                .extracting(SdkLogLevel::slf4jLevel)
                .doesNotContain(Level.TRACE);
    }

    @Test
    @DisplayName("levels are declared most severe first")
    void isOrderedBySeverity() {
        assertThat(SdkLogLevel.values())
                .containsExactly(
                        SdkLogLevel.ERROR, SdkLogLevel.WARN, SdkLogLevel.INFO, SdkLogLevel.DEBUG);
    }
}
