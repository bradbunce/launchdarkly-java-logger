package io.github.bradbunce.ldlogger;

/**
 * A bridge used when no supported logging backend is bound.
 *
 * <p>It reports itself unavailable rather than silently discarding level
 * changes, so that {@link LDLogLevelController} can warn loudly instead of
 * appearing to work.
 */
final class UnavailableLogLevelBridge implements LogLevelBridge {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String backendName() {
        return "none";
    }

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        throw new IllegalStateException(
                "No supported SLF4J backend is bound (SLF4J is bound to "
                        + Backends.boundFactoryName()
                        + "), so log levels cannot be changed at runtime. Add one of "
                        + "ch.qos.logback:logback-classic, org.apache.logging.log4j:log4j-slf4j2-impl "
                        + "with log4j-core, or org.slf4j:slf4j-jdk14 to the runtime classpath.");
    }
}
