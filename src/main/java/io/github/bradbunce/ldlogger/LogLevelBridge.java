package io.github.bradbunce.ldlogger;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Applies a log level to a named logger in the underlying logging backend.
 *
 * <p>SLF4J itself is a facade and deliberately offers no way to change levels at
 * runtime, so this is backend-specific. It is also the library's extension
 * point: a backend this library has never heard of is supported by implementing
 * these three methods and passing the result to
 * {@link LDLogLevelController.Builder#logLevelBridge(LogLevelBridge)}, or by
 * registering it as a {@link ServiceLoader} service (see {@link #detect()}).
 *
 * <h2>Root logger</h2>
 * The logger name passed to {@link #setLevel} uses SLF4J's convention, where the
 * root logger is named {@link org.slf4j.Logger#ROOT_LOGGER_NAME}. Backends that
 * name their root logger differently - Log4j 2 and {@code java.util.logging}
 * both use the empty string - must translate it.
 */
public interface LogLevelBridge {

    /**
     * Sets the level of a single logger.
     *
     * @param loggerName the logger name, or {@link org.slf4j.Logger#ROOT_LOGGER_NAME} for the root
     * @param level the level to apply
     * @throws IllegalStateException if the backend is not available
     */
    void setLevel(String loggerName, LogLevel level);

    /**
     * Whether this bridge can actually change levels.
     *
     * <p>Implementations should report {@code false} unless their backend is the
     * one SLF4J is currently bound to. A bridge that mutates a backend nothing
     * logs through would appear to work while changing nothing.
     *
     * @return true if level changes will take effect
     */
    boolean isAvailable();

    /**
     * Returns a short description of the backend, for diagnostics.
     *
     * @return a human-readable backend name
     */
    String backendName();

    /**
     * Detects the bound logging backend.
     *
     * <p>Bridges registered as {@link ServiceLoader} services for this interface
     * are consulted first, so an application can support a backend this library
     * does not know about without changing any code here. After those, the
     * built-in Logback, Log4j 2 and {@code java.util.logging} bridges are tried
     * in turn. The first bridge reporting {@link #isAvailable()} wins.
     *
     * @return a bridge for the bound backend, or an unavailable bridge if none matched
     */
    static LogLevelBridge detect() {
        for (LogLevelBridge provided : ServiceLoader.load(LogLevelBridge.class)) {
            if (provided.isAvailable()) {
                return provided;
            }
        }

        // Suppliers rather than instances: constructing a bridge must not load
        // backend classes that are absent from the classpath.
        List<Supplier<LogLevelBridge>> builtIn = List.of(
                LogbackLogLevelBridge::new, Log4j2LogLevelBridge::new, JulLogLevelBridge::new);

        for (Supplier<LogLevelBridge> candidate : builtIn) {
            LogLevelBridge bridge = candidate.get();
            if (bridge.isAvailable()) {
                return bridge;
            }
        }
        return new UnavailableLogLevelBridge();
    }
}
