package dev.bradbunce.ldlogger;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Applies log levels via Logback's {@link LoggerContext}.
 *
 * <p>Because this mutates the levels of real Logback loggers, every existing
 * {@code LoggerFactory.getLogger(...)} call in the application observes the
 * change - no call sites need to be modified.
 */
public final class LogbackLogLevelBridge implements LogLevelBridge {

    private static final String LOGGER_CONTEXT = "ch.qos.logback.classic.LoggerContext";

    /** Creates a bridge to the currently bound Logback context. */
    public LogbackLogLevelBridge() {
        // Stateless; the Logback context is resolved on each call so that a
        // reconfigured context is picked up.
    }

    /**
     * Whether SLF4J is currently bound to Logback.
     *
     * @return true if Logback is on the classpath and bound
     */
    static boolean isLogbackBound() {
        return Backends.isClassPresent(LOGGER_CONTEXT)
                && LoggerFactory.getILoggerFactory() instanceof LoggerContext;
    }

    @Override
    public boolean isAvailable() {
        return isLogbackBound();
    }

    @Override
    public String backendName() {
        return "Logback";
    }

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            throw new IllegalStateException(
                    "SLF4J is bound to " + Backends.boundFactoryName()
                            + ", not Logback; log levels cannot be changed at runtime");
        }
        // Logback names its root logger "ROOT", the same as SLF4J, so the name
        // needs no translation here.
        context.getLogger(loggerName).setLevel(toLogbackLevel(level));
    }

    private static ch.qos.logback.classic.Level toLogbackLevel(LogLevel level) {
        return switch (level) {
            // Logback has no FATAL, so the most severe level it can express is ERROR.
            case FATAL, ERROR -> ch.qos.logback.classic.Level.ERROR;
            case WARN -> ch.qos.logback.classic.Level.WARN;
            case INFO -> ch.qos.logback.classic.Level.INFO;
            case DEBUG -> ch.qos.logback.classic.Level.DEBUG;
            case TRACE -> ch.qos.logback.classic.Level.TRACE;
        };
    }
}
