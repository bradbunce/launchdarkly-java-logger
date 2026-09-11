package io.github.bradbunce.ldlogger;

import java.util.Optional;
import org.slf4j.event.Level;

/**
 * Application log levels, matching the numeric flag values used by the
 * LaunchDarkly React logger: {@code FATAL=0} through {@code TRACE=5}.
 *
 * <p>Setting the flag to a level enables that level and every more severe level.
 *
 * <p>Note that SLF4J has no {@code FATAL} level, so {@link #FATAL} and
 * {@link #ERROR} both map to {@link Level#ERROR}. The distinction is preserved
 * here only so that flag values {@code 0} and {@code 1} remain valid.
 */
public enum ConsoleLogLevel {
    /** Most severe level, for unrecoverable errors. Maps to SLF4J {@code ERROR}. */
    FATAL(0, LogLevel.FATAL),
    /** Error conditions that should be addressed. */
    ERROR(1, LogLevel.ERROR),
    /** Warning messages for potentially harmful situations. */
    WARN(2, LogLevel.WARN),
    /** General informational messages. */
    INFO(3, LogLevel.INFO),
    /** Detailed debug information. */
    DEBUG(4, LogLevel.DEBUG),
    /** Most detailed level for fine-grained debugging. */
    TRACE(5, LogLevel.TRACE);

    private final int flagValue;
    private final LogLevel logLevel;

    ConsoleLogLevel(int flagValue, LogLevel logLevel) {
        this.flagValue = flagValue;
        this.logLevel = logLevel;
    }

    /**
     * Returns the numeric feature flag value for this level.
     *
     * @return the flag value, 0 through 5
     */
    public int flagValue() {
        return flagValue;
    }

    /**
     * Returns the backend-independent level this maps onto, as applied by
     * {@link LogLevelBridge}.
     *
     * <p>Unlike {@link #slf4jLevel()} this preserves {@link LogLevel#FATAL}, so
     * a backend with a real {@code FATAL} level can use it.
     *
     * @return the equivalent level
     */
    public LogLevel logLevel() {
        return logLevel;
    }

    /**
     * Returns the SLF4J level this maps onto.
     *
     * <p>Both {@link #FATAL} and {@link #ERROR} return {@link Level#ERROR},
     * since SLF4J has no {@code FATAL}.
     *
     * @return the equivalent SLF4J level
     */
    public Level slf4jLevel() {
        return logLevel.slf4jLevel();
    }

    /**
     * Resolves a numeric flag value to a level.
     *
     * @param flagValue the numeric flag value
     * @return the matching level, or empty if the value is out of range
     */
    public static Optional<ConsoleLogLevel> fromFlagValue(int flagValue) {
        for (ConsoleLogLevel level : values()) {
            if (level.flagValue == flagValue) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
