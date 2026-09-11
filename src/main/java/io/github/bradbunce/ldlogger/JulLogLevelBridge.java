package io.github.bradbunce.ldlogger;

import java.util.logging.Level;
import org.slf4j.Logger;

/**
 * Applies log levels via {@code java.util.logging}.
 *
 * <p>Only used when SLF4J is bound to JUL through {@code slf4j-jdk14}. Needs no
 * dependency of its own, since JUL ships with the JDK.
 *
 * <p>JUL's levels do not line up one-for-one with SLF4J's, so the mapping is
 * lossy in both directions: {@code FATAL} and {@code ERROR} both become
 * {@link Level#SEVERE}, and JUL's {@code CONFIG} and {@code FINER} are never
 * produced.
 */
public final class JulLogLevelBridge implements LogLevelBridge {

    private static final String SLF4J_BINDING_PACKAGE = "org.slf4j.jul.";

    /** Creates a bridge to the JDK's logging framework. */
    public JulLogLevelBridge() {
        // Stateless; JUL's LogManager is consulted on each call.
    }

    /**
     * Whether SLF4J is currently bound to {@code java.util.logging}.
     *
     * <p>JUL is always on the classpath, so unlike the other backends the only
     * meaningful question is whether SLF4J is actually routed to it.
     */
    static boolean isJulBound() {
        return Backends.isBoundToPackage(SLF4J_BINDING_PACKAGE);
    }

    @Override
    public boolean isAvailable() {
        return isJulBound();
    }

    @Override
    public String backendName() {
        return "java.util.logging";
    }

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        if (!isJulBound()) {
            throw new IllegalStateException(
                    "SLF4J is bound to " + Backends.boundFactoryName()
                            + ", not java.util.logging; log levels cannot be changed at runtime");
        }
        // JUL names its root logger "", not "ROOT".
        String julName = Logger.ROOT_LOGGER_NAME.equals(loggerName) ? "" : loggerName;

        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(julName);
        julLogger.setLevel(toJulLevel(level));

        // A JUL level only takes effect if a handler will also publish it, and
        // the default console handler sits at INFO. Opening up the root
        // handlers is what makes DEBUG and TRACE actually appear.
        for (java.util.logging.Handler handler :
                java.util.logging.Logger.getLogger("").getHandlers()) {
            if (handler.getLevel().intValue() > toJulLevel(level).intValue()) {
                handler.setLevel(toJulLevel(level));
            }
        }
    }

    private static Level toJulLevel(LogLevel level) {
        return switch (level) {
            // JUL has no FATAL; SEVERE is the most severe level it can express.
            case FATAL, ERROR -> Level.SEVERE;
            case WARN -> Level.WARNING;
            case INFO -> Level.INFO;
            // slf4j-jdk14 maps DEBUG to FINE and TRACE to FINEST, so mirror that.
            case DEBUG -> Level.FINE;
            case TRACE -> Level.FINEST;
        };
    }
}
