package dev.bradbunce.ldlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@link LogLevelBridge} that records the level changes asked of it instead of
 * touching a real logging backend, and whose availability can be toggled.
 */
final class RecordingLogLevelBridge implements LogLevelBridge {

    /** A single {@link #setLevel} invocation. */
    record LevelChange(String loggerName, LogLevel level) {}

    private final List<LevelChange> changes = new ArrayList<>();
    private final boolean available;

    private RecordingLogLevelBridge(boolean available) {
        this.available = available;
    }

    static RecordingLogLevelBridge available() {
        return new RecordingLogLevelBridge(true);
    }

    static RecordingLogLevelBridge unavailable() {
        return new RecordingLogLevelBridge(false);
    }

    List<LevelChange> changes() {
        return List.copyOf(changes);
    }

    /** The most recent level applied to a logger, or empty if it was never touched. */
    Optional<LogLevel> lastLevelOf(String loggerName) {
        for (int i = changes.size() - 1; i >= 0; i--) {
            if (changes.get(i).loggerName().equals(loggerName)) {
                return Optional.of(changes.get(i).level());
            }
        }
        return Optional.empty();
    }

    void clearChanges() {
        changes.clear();
    }

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        if (!available) {
            throw new IllegalStateException("recording bridge is unavailable");
        }
        changes.add(new LevelChange(loggerName, level));
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String backendName() {
        return "recording";
    }
}
