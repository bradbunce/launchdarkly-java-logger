package io.github.bradbunce.ldlogger;

/**
 * A {@link LogLevelBridge} loaded through {@link java.util.ServiceLoader} in
 * {@link LogLevelBridgeTest}. Must be public with a no-arg constructor, which is
 * what ServiceLoader requires of a provider.
 */
public final class FakeServiceBridge implements LogLevelBridge {

    /** Set by the test to decide whether this bridge claims the backend. */
    static volatile boolean available = true;

    /** Required by ServiceLoader. */
    public FakeServiceBridge() {
        // Nothing to set up.
    }

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        // Never called; the test only checks which bridge detection selects.
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String backendName() {
        return "fake-service";
    }
}
