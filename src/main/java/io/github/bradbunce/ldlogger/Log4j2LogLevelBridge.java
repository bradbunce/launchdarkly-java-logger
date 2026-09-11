package io.github.bradbunce.ldlogger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;

/**
 * Applies log levels via Log4j 2's {@link Configurator}.
 *
 * <p>Only used when SLF4J is bound to Log4j 2 through {@code log4j-slf4j2-impl}.
 * If an application logs through the Log4j 2 API directly rather than SLF4J,
 * detection will not select this bridge - pass it explicitly to
 * {@link LDLogLevelController.Builder#logLevelBridge(LogLevelBridge)} instead.
 */
public final class Log4j2LogLevelBridge implements LogLevelBridge {

    private static final String SLF4J_BINDING_PACKAGE = "org.apache.logging.slf4j.";
    private static final String CONFIGURATOR = "org.apache.logging.log4j.core.config.Configurator";

    /** Creates a bridge to the current Log4j 2 configuration. */
    public Log4j2LogLevelBridge() {
        // Stateless; the Log4j 2 configuration is resolved on each call.
    }

    /**
     * Whether SLF4J is currently bound to Log4j 2 and its core is present.
     *
     * <p>{@code log4j-api} alone is not enough: {@code Configurator} lives in
     * {@code log4j-core}, and a {@code log4j-to-slf4j} setup routes the other
     * way entirely.
     */
    static boolean isLog4j2Bound() {
        return Backends.isClassPresent(CONFIGURATOR)
                && Backends.isBoundToPackage(SLF4J_BINDING_PACKAGE);
    }

    @Override
    public boolean isAvailable() {
        return isLog4j2Bound();
    }

    @Override
    public String backendName() {
        return "Log4j 2";
    }

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        if (!isLog4j2Bound()) {
            throw new IllegalStateException(
                    "SLF4J is bound to " + Backends.boundFactoryName()
                            + ", not Log4j 2; log levels cannot be changed at runtime");
        }
        if (Logger.ROOT_LOGGER_NAME.equals(loggerName)) {
            // Log4j 2 names its root logger "", not "ROOT".
            Configurator.setRootLevel(toLog4jLevel(level));
        } else {
            Configurator.setLevel(loggerName, toLog4jLevel(level));
        }
    }

    private static Level toLog4jLevel(LogLevel level) {
        return switch (level) {
            // Log4j 2 does have a FATAL level, but deliberately not used here:
            // SLF4J call sites cannot emit FATAL, so a logger pinned to FATAL
            // would discard even log.error(...) and silence the application.
            // A consumer logging through the Log4j 2 API directly can supply
            // their own bridge if they want true FATAL.
            case FATAL, ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case INFO -> Level.INFO;
            case DEBUG -> Level.DEBUG;
            case TRACE -> Level.TRACE;
        };
    }
}
