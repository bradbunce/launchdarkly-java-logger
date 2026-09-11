package dev.bradbunce.ldlogger;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives SLF4J log levels from LaunchDarkly feature flags.
 *
 * <p>On {@link #start()} the configured flags are evaluated and applied, and
 * change listeners are registered so that later flag changes take effect
 * immediately. Because this sets the level of real loggers, existing
 * {@code LoggerFactory.getLogger(...)} calls throughout the application respond
 * without any code changes.
 *
 * <p>Levels are resolved against a single {@link LDContext} representing the
 * service or instance - flag targeting rules can still vary the level by
 * environment, region, or host.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * LDContext serverContext = LDContext.builder("checkout-service")
 *     .kind("service")
 *     .set("env", "production")
 *     .build();
 *
 * LDLogLevelController controller = LDLogLevelController
 *     .builder(ldClient, serverContext)
 *     .consoleLogFlagKey("console-log-level")
 *     .sdkLogFlagKey("sdk-log-level")
 *     .build();
 *
 * controller.start();
 * }</pre>
 *
 * <p>Instances are thread-safe and should be closed on shutdown.
 */
public final class LDLogLevelController implements AutoCloseable {

    /** Default logger name whose level the SDK log flag controls. */
    public static final String DEFAULT_SDK_LOGGER_NAME = "com.launchdarkly";

    private static final Logger LOG = LoggerFactory.getLogger(LDLogLevelController.class);

    private final LDClientInterface client;
    private final LDContext context;
    private final String consoleLogFlagKey;
    private final String sdkLogFlagKey;
    private final String applicationLoggerName;
    private final String sdkLoggerName;
    private final ConsoleLogLevel defaultConsoleLogLevel;
    private final SdkLogLevel defaultSdkLogLevel;
    private final Consumer<ConsoleLogLevel> onConsoleLogLevelChange;
    private final Consumer<SdkLogLevel> onSdkLogLevelChange;
    private final LogLevelBridge bridge;

    private final Object lock = new Object();
    private final List<FlagChangeListener> listeners = new ArrayList<>();
    private boolean started;
    private ConsoleLogLevel currentConsoleLogLevel;
    private SdkLogLevel currentSdkLogLevel;

    private LDLogLevelController(Builder builder) {
        this.client = builder.client;
        this.context = builder.context;
        this.consoleLogFlagKey = builder.consoleLogFlagKey;
        this.sdkLogFlagKey = builder.sdkLogFlagKey;
        this.applicationLoggerName = builder.applicationLoggerName;
        this.sdkLoggerName = builder.sdkLoggerName;
        this.defaultConsoleLogLevel = builder.defaultConsoleLogLevel;
        this.defaultSdkLogLevel = builder.defaultSdkLogLevel;
        this.onConsoleLogLevelChange = builder.onConsoleLogLevelChange;
        this.onSdkLogLevelChange = builder.onSdkLogLevelChange;
        this.bridge = builder.bridge != null ? builder.bridge : LogLevelBridge.detect();
        this.currentConsoleLogLevel = builder.defaultConsoleLogLevel;
        this.currentSdkLogLevel = builder.defaultSdkLogLevel;
    }

    /**
     * Creates a builder.
     *
     * @param client the LaunchDarkly client
     * @param context the context that log levels are evaluated against
     * @return a new builder
     */
    public static Builder builder(LDClientInterface client, LDContext context) {
        return new Builder(client, context);
    }

    /**
     * Evaluates the configured flags, applies the resulting levels, and
     * subscribes to further changes.
     *
     * <p>Calling this more than once has no additional effect.
     */
    public void start() {
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;

            if (!bridge.isAvailable()) {
                LOG.warn(
                        "LaunchDarkly log level control is inactive: no supported SLF4J backend is bound. "
                                + "Add ch.qos.logback:logback-classic, "
                                + "org.apache.logging.log4j:log4j-slf4j2-impl with log4j-core, or "
                                + "org.slf4j:slf4j-jdk14 to the runtime classpath - or supply your own "
                                + "LogLevelBridge.");
                return;
            }

            FlagTracker tracker = client.getFlagTracker();

            if (consoleLogFlagKey != null) {
                applyConsoleLogLevel(readConsoleLogLevel());
                listeners.add(tracker.addFlagValueChangeListener(
                        consoleLogFlagKey, context, this::onConsoleFlagChanged));
            }

            if (sdkLogFlagKey != null) {
                applySdkLogLevel(readSdkLogLevel());
                listeners.add(tracker.addFlagValueChangeListener(
                        sdkLogFlagKey, context, this::onSdkFlagChanged));
            }
        }
    }

    /** Unsubscribes from flag changes. Applied levels are left as they are. */
    @Override
    public void close() {
        synchronized (lock) {
            if (!started) {
                return;
            }
            FlagTracker tracker = client.getFlagTracker();
            for (FlagChangeListener listener : listeners) {
                tracker.removeFlagChangeListener(listener);
            }
            listeners.clear();
            started = false;
        }
    }

    /**
     * Returns the application log level currently in effect.
     *
     * @return the current level, or the configured default before {@link #start()}
     */
    public ConsoleLogLevel currentConsoleLogLevel() {
        synchronized (lock) {
            return currentConsoleLogLevel;
        }
    }

    /**
     * Returns the SDK log level currently in effect.
     *
     * @return the current level, or empty if no SDK log flag is configured
     */
    public Optional<SdkLogLevel> currentSdkLogLevel() {
        synchronized (lock) {
            return sdkLogFlagKey == null ? Optional.empty() : Optional.of(currentSdkLogLevel);
        }
    }

    /**
     * Returns the backend that level changes are applied through.
     *
     * @return the bridge in use
     */
    public LogLevelBridge bridge() {
        return bridge;
    }

    private ConsoleLogLevel readConsoleLogLevel() {
        int raw = client.intVariation(
                consoleLogFlagKey, context, defaultConsoleLogLevel.flagValue());
        return resolveConsoleLogLevel(LDValue.of(raw));
    }

    private SdkLogLevel readSdkLogLevel() {
        String raw = client.stringVariation(
                sdkLogFlagKey, context, defaultSdkLogLevel.flagValue());
        return resolveSdkLogLevel(LDValue.of(raw));
    }

    private ConsoleLogLevel resolveConsoleLogLevel(LDValue value) {
        if (!value.isNumber()) {
            LOG.warn(
                    "Flag '{}' returned {}, which is not a number; falling back to {}",
                    consoleLogFlagKey, value, defaultConsoleLogLevel);
            return defaultConsoleLogLevel;
        }
        Optional<ConsoleLogLevel> level = ConsoleLogLevel.fromFlagValue(value.intValue());
        if (level.isEmpty()) {
            LOG.warn(
                    "Flag '{}' returned {}, which is not a valid level (0-5); falling back to {}",
                    consoleLogFlagKey, value.intValue(), defaultConsoleLogLevel);
            return defaultConsoleLogLevel;
        }
        return level.get();
    }

    private SdkLogLevel resolveSdkLogLevel(LDValue value) {
        Optional<SdkLogLevel> level = SdkLogLevel.fromFlagValue(value.stringValue());
        if (level.isEmpty()) {
            LOG.warn(
                    "Flag '{}' returned {}, which is not one of error/warn/info/debug; falling back to {}",
                    sdkLogFlagKey, value, defaultSdkLogLevel);
            return defaultSdkLogLevel;
        }
        return level.get();
    }

    private void onConsoleFlagChanged(FlagValueChangeEvent event) {
        applyConsoleLogLevel(resolveConsoleLogLevel(event.getNewValue()));
    }

    private void onSdkFlagChanged(FlagValueChangeEvent event) {
        applySdkLogLevel(resolveSdkLogLevel(event.getNewValue()));
    }

    private void applyConsoleLogLevel(ConsoleLogLevel level) {
        synchronized (lock) {
            bridge.setLevel(applicationLoggerName, level.logLevel());
            currentConsoleLogLevel = level;
        }
        notifyListener(onConsoleLogLevelChange, level, "console");
    }

    private void applySdkLogLevel(SdkLogLevel level) {
        synchronized (lock) {
            bridge.setLevel(sdkLoggerName, level.logLevel());
            currentSdkLogLevel = level;
        }
        notifyListener(onSdkLogLevelChange, level, "SDK");
    }

    private static <T> void notifyListener(Consumer<T> listener, T level, String which) {
        if (listener == null) {
            return;
        }
        try {
            listener.accept(level);
        } catch (RuntimeException e) {
            // A misbehaving callback must never break log level control.
            LOG.warn("{} log level change callback threw an exception", which, e);
        }
    }

    /** Builder for {@link LDLogLevelController}. */
    public static final class Builder {
        private final LDClientInterface client;
        private final LDContext context;
        private String consoleLogFlagKey;
        private String sdkLogFlagKey;
        private String applicationLoggerName = Logger.ROOT_LOGGER_NAME;
        private String sdkLoggerName = DEFAULT_SDK_LOGGER_NAME;
        private ConsoleLogLevel defaultConsoleLogLevel = ConsoleLogLevel.ERROR;
        private SdkLogLevel defaultSdkLogLevel = SdkLogLevel.ERROR;
        private Consumer<ConsoleLogLevel> onConsoleLogLevelChange;
        private Consumer<SdkLogLevel> onSdkLogLevelChange;
        private LogLevelBridge bridge;

        private Builder(LDClientInterface client, LDContext context) {
            this.client = Objects.requireNonNull(client, "client must not be null");
            this.context = Objects.requireNonNull(context, "context must not be null");
        }

        /**
         * Sets the numeric flag (0-5) controlling the application log level.
         *
         * @param flagKey the flag key
         * @return this builder
         */
        public Builder consoleLogFlagKey(String flagKey) {
            this.consoleLogFlagKey = flagKey;
            return this;
        }

        /**
         * Sets the string flag (error/warn/info/debug) controlling the SDK's own
         * log level.
         *
         * <p>This only has an effect if the SDK is configured to log through
         * SLF4J; see {@link LDSdkLogging#slf4j()}.
         *
         * @param flagKey the flag key
         * @return this builder
         */
        public Builder sdkLogFlagKey(String flagKey) {
            this.sdkLogFlagKey = flagKey;
            return this;
        }

        /**
         * Sets which logger the application log level is applied to. Defaults to
         * the root logger, so the whole application is affected.
         *
         * @param loggerName the logger name
         * @return this builder
         */
        public Builder applicationLoggerName(String loggerName) {
            this.applicationLoggerName = Objects.requireNonNull(loggerName, "loggerName");
            return this;
        }

        /**
         * Sets which logger the SDK log level is applied to. Defaults to
         * {@value #DEFAULT_SDK_LOGGER_NAME}.
         *
         * @param loggerName the logger name
         * @return this builder
         */
        public Builder sdkLoggerName(String loggerName) {
            this.sdkLoggerName = Objects.requireNonNull(loggerName, "loggerName");
            return this;
        }

        /**
         * Sets the level used when the console flag is missing or invalid.
         *
         * @param level the fallback level
         * @return this builder
         */
        public Builder defaultConsoleLogLevel(ConsoleLogLevel level) {
            this.defaultConsoleLogLevel = Objects.requireNonNull(level, "level");
            return this;
        }

        /**
         * Sets the level used when the SDK flag is missing or invalid.
         *
         * @param level the fallback level
         * @return this builder
         */
        public Builder defaultSdkLogLevel(SdkLogLevel level) {
            this.defaultSdkLogLevel = Objects.requireNonNull(level, "level");
            return this;
        }

        /**
         * Registers a callback invoked whenever the application log level changes.
         *
         * @param callback the callback
         * @return this builder
         */
        public Builder onConsoleLogLevelChange(Consumer<ConsoleLogLevel> callback) {
            this.onConsoleLogLevelChange = callback;
            return this;
        }

        /**
         * Registers a callback invoked whenever the SDK log level changes.
         *
         * @param callback the callback
         * @return this builder
         */
        public Builder onSdkLogLevelChange(Consumer<SdkLogLevel> callback) {
            this.onSdkLogLevelChange = callback;
            return this;
        }

        /**
         * Overrides the backend bridge.
         *
         * <p>By default the backend is detected automatically, which covers
         * Logback, Log4j 2 and {@code java.util.logging}. This is the supported
         * way to drive a backend the library does not know about: implement
         * {@link LogLevelBridge} and pass it here. It is also how tests inject a
         * fake.
         *
         * @param bridge the bridge to use
         * @return this builder
         */
        public Builder logLevelBridge(LogLevelBridge bridge) {
            this.bridge = bridge;
            return this;
        }

        /**
         * Builds the controller.
         *
         * @return a new controller
         * @throws IllegalArgumentException if no flag keys were configured
         */
        public LDLogLevelController build() {
            if (consoleLogFlagKey == null && sdkLogFlagKey == null) {
                throw new IllegalArgumentException(
                        "At least one of consoleLogFlagKey or sdkLogFlagKey must be set");
            }
            return new LDLogLevelController(this);
        }
    }
}
