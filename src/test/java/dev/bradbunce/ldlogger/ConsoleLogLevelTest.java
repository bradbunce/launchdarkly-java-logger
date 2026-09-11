package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

class ConsoleLogLevelTest {

    @ParameterizedTest
    @CsvSource({
        "FATAL, 0, ERROR",
        "ERROR, 1, ERROR",
        "WARN,  2, WARN",
        "INFO,  3, INFO",
        "DEBUG, 4, DEBUG",
        "TRACE, 5, TRACE",
    })
    @DisplayName("each level has the React logger's flag value and the matching SLF4J level")
    void mapsFlagValuesAndSlf4jLevels(ConsoleLogLevel level, int flagValue, Level slf4jLevel) {
        assertThat(level.flagValue()).isEqualTo(flagValue);
        assertThat(level.slf4jLevel()).isEqualTo(slf4jLevel);
    }

    @ParameterizedTest
    @EnumSource(ConsoleLogLevel.class)
    @DisplayName("every level round-trips through its flag value")
    void roundTripsThroughFlagValue(ConsoleLogLevel level) {
        assertThat(ConsoleLogLevel.fromFlagValue(level.flagValue())).contains(level);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6, 7, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("flag values outside 0-5 resolve to empty")
    void rejectsOutOfRangeFlagValues(int flagValue) {
        assertThat(ConsoleLogLevel.fromFlagValue(flagValue)).isEmpty();
    }

    @Test
    @DisplayName("FATAL and ERROR both map to SLF4J ERROR but stay distinct levels")
    void fatalAndErrorShareAnSlf4jLevel() {
        assertThat(ConsoleLogLevel.FATAL.slf4jLevel()).isEqualTo(ConsoleLogLevel.ERROR.slf4jLevel());
        assertThat(ConsoleLogLevel.FATAL).isNotEqualTo(ConsoleLogLevel.ERROR);
        assertThat(ConsoleLogLevel.fromFlagValue(0)).contains(ConsoleLogLevel.FATAL);
        assertThat(ConsoleLogLevel.fromFlagValue(1)).contains(ConsoleLogLevel.ERROR);
    }

    @Test
    @DisplayName("levels are declared most severe first, so ordinals track severity")
    void isOrderedBySeverity() {
        assertThat(ConsoleLogLevel.values())
                .containsExactly(
                        ConsoleLogLevel.FATAL,
                        ConsoleLogLevel.ERROR,
                        ConsoleLogLevel.WARN,
                        ConsoleLogLevel.INFO,
                        ConsoleLogLevel.DEBUG,
                        ConsoleLogLevel.TRACE);
    }

    @Test
    @DisplayName("flag values are contiguous from 0 to 5")
    void coversEveryValueInRange() {
        for (int flagValue = 0; flagValue <= 5; flagValue++) {
            Optional<ConsoleLogLevel> level = ConsoleLogLevel.fromFlagValue(flagValue);
            assertThat(level).as("flag value %d", flagValue).isPresent();
        }
    }
}
