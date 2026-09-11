package io.github.bradbunce.ldlogger;

import java.util.Locale;
import java.util.Optional;
import org.slf4j.event.Level;

/**
 * Log levels for the LaunchDarkly SDK's own internal logging, matching the
 * string flag values used by the LaunchDarkly React logger.
 *
 * <p>These mirror {@code com.launchdarkly.logging.LDLogLevel}, which has no
 * {@code TRACE} level.
 *
 * <p>{@code LDLogLevel} also has a {@code NONE} value, for disabling the SDK's
 * logging entirely, which is deliberately not exposed here. The four values
 * above are exactly what the LaunchDarkly React logger documents, so one flag
 * stays portable across a browser app and a Java service. An application that
 * wants the SDK silent can configure its logging backend directly - for Logback,
 * {@code <logger name="com.launchdarkly" level="OFF"/>} - without needing a flag
 * value for it.
 */
public enum SdkLogLevel {
    /** Error conditions only. */
    ERROR("error", LogLevel.ERROR),
    /** Warnings and errors. */
    WARN("warn", LogLevel.WARN),
    /** Informational messages and above. */
    INFO("info", LogLevel.INFO),
    /** Verbose SDK diagnostics, useful for debugging flag evaluation. */
    DEBUG("debug", LogLevel.DEBUG);

    private final String flagValue;
    private final LogLevel logLevel;

    SdkLogLevel(String flagValue, LogLevel logLevel) {
        this.flagValue = flagValue;
        this.logLevel = logLevel;
    }

    /**
     * Returns the string feature flag value for this level.
     *
     * @return the flag value, such as {@code "debug"}
     */
    public String flagValue() {
        return flagValue;
    }

    /**
     * Returns the backend-independent level this maps onto, as applied by
     * {@link LogLevelBridge}.
     *
     * @return the equivalent level
     */
    public LogLevel logLevel() {
        return logLevel;
    }

    /**
     * Returns the SLF4J level this maps onto.
     *
     * @return the equivalent SLF4J level
     */
    public Level slf4jLevel() {
        return logLevel.slf4jLevel();
    }

    /**
     * Resolves a string flag value to a level, case-insensitively.
     *
     * @param flagValue the string flag value; may be null
     * @return the matching level, or empty if unrecognized
     */
    public static Optional<SdkLogLevel> fromFlagValue(String flagValue) {
        if (flagValue == null) {
            return Optional.empty();
        }
        String normalized = flagValue.trim().toLowerCase(Locale.ROOT);
        for (SdkLogLevel level : values()) {
            if (level.flagValue.equals(normalized)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
