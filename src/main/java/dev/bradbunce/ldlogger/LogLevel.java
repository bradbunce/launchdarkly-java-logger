package dev.bradbunce.ldlogger;

import org.slf4j.event.Level;

/**
 * A log level as this library applies it, independent of any logging backend.
 *
 * <p>This is the type {@link LogLevelBridge} receives, and it exists because
 * neither of the two flag-facing enums is a superset of the other and because
 * {@link Level} cannot represent everything a backend can.
 * {@link ConsoleLogLevel} contributes {@code FATAL} and {@code TRACE};
 * {@link SdkLogLevel} contributes nothing beyond the middle four. Passing this
 * rather than {@link Level} means a bridge still knows that flag value 0 meant
 * {@code FATAL} and can use a real {@code FATAL} level if its backend has one.
 *
 * <p>There is deliberately no numeric value here. Every Java logging framework
 * numbers its levels differently and some invert the ordering relative to
 * others, so a number would only invite unsafe comparisons. Severity filtering
 * is the backend's job.
 */
public enum LogLevel {
    /** Unrecoverable errors. SLF4J has no equivalent, so it reports as {@code ERROR}. */
    FATAL(Level.ERROR),
    /** Error conditions that should be addressed. */
    ERROR(Level.ERROR),
    /** Potentially harmful situations. */
    WARN(Level.WARN),
    /** General informational messages. */
    INFO(Level.INFO),
    /** Detailed debug information. */
    DEBUG(Level.DEBUG),
    /** Most detailed level for fine-grained debugging. */
    TRACE(Level.TRACE);

    private final Level slf4jLevel;

    LogLevel(Level slf4jLevel) {
        this.slf4jLevel = slf4jLevel;
    }

    /**
     * Returns the nearest SLF4J level.
     *
     * <p>{@link #FATAL} maps to {@link Level#ERROR}, since SLF4J has no
     * {@code FATAL}.
     *
     * @return the equivalent SLF4J level
     */
    public Level slf4jLevel() {
        return slf4jLevel;
    }
}
