package io.github.bradbunce.ldlogger.docs;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import io.github.bradbunce.ldlogger.ConsoleLogLevel;
import io.github.bradbunce.ldlogger.LDLogLevelController;
import io.github.bradbunce.ldlogger.LDSdkLogging;
import io.github.bradbunce.ldlogger.LogLevel;
import io.github.bradbunce.ldlogger.LogLevelBridge;
import io.github.bradbunce.ldlogger.SdkLogLevel;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every Java example in README.md, held here so the compiler checks it.
 *
 * <p>This class is never executed. It exists because documentation that does not
 * compile is worse than no documentation, and because being in a separate
 * package means the examples can only use the library's public API - so it also
 * catches a public surface that is missing something the docs promise.
 *
 * <p>{@code ReadmeSnippetsTest} asserts that each example here still matches the
 * README, so the two cannot drift apart.
 */
@SuppressWarnings("unused")
public class ReadmeExamples {

    private static final Logger log = LoggerFactory.getLogger(ReadmeExamples.class);

    private final String sdkKey = "sdk-key-placeholder";
    private final LDClient client = null;
    private final LDContext serverContext = null;
    private final LDLogLevelController logLevels = null;
    private final Metrics metrics = null;

    /** Stand-in for whatever a real service reports metrics through. */
    public interface Metrics {
        void gauge(String name, int value);
    }

    private static void runApplication() {
        // Stand-in for the application's own work.
    }

    void applicationLoggingLevels() {
        log.error("API error");           // Level 1
        log.warn("Deprecated usage");     // Level 2
        log.info("User logged in");       // Level 3
        log.debug("API response");        // Level 4
        log.trace("Function called");     // Level 5
    }

    void routeSdkLogsThroughSlf4j() {
        LDConfig config = new LDConfig.Builder()
            .logging(LDSdkLogging.slf4j())
            .build();

        LDClient client = new LDClient(sdkKey, config);
    }

    void createTheController() {
        // Log levels are resolved against one context representing the service or
        // instance, so targeting rules can vary the level by environment or region.
        LDContext serverContext = LDContext.builder("checkout-service")
            .kind("service")
            .set("env", "production")
            .build();

        LDLogLevelController logLevels = LDLogLevelController
            .builder(client, serverContext)
            .consoleLogFlagKey("console-log-level")
            .sdkLogFlagKey("sdk-log-level")
            .build();

        logLevels.start();
    }

    public class CheckoutService {
        private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

        public void checkout(String orderId) {
            log.debug("Starting checkout for {}", orderId);
        }
    }

    void targetSpecificLoggers() {
        LDLogLevelController.builder(client, serverContext)
            .consoleLogFlagKey("console-log-level")
            .applicationLoggerName("com.example.checkout")
            .sdkLogFlagKey("sdk-log-level")
            .sdkLoggerName("com.example.vendor.launchdarkly")
            .build();
    }

    void chooseFallbackLevels() {
        LDLogLevelController.builder(client, serverContext)
            .consoleLogFlagKey("console-log-level")
            .defaultConsoleLogLevel(ConsoleLogLevel.WARN)
            .sdkLogFlagKey("sdk-log-level")
            .defaultSdkLogLevel(SdkLogLevel.ERROR)
            .build();
    }

    void reactToChanges() {
        LDLogLevelController.builder(client, serverContext)
            .consoleLogFlagKey("console-log-level")
            .onConsoleLogLevelChange(level ->
                metrics.gauge("log.level", level.flagValue()))
            .onSdkLogLevelChange(level ->
                System.out.println("SDK log level changed to: " + level.flagValue()))
            .build();
    }

    void readTheCurrentLevel() {
        ConsoleLogLevel appLevel = logLevels.currentConsoleLogLevel();
        Optional<SdkLogLevel> sdkLevel = logLevels.currentSdkLogLevel();  // empty if no SDK flag
    }

    void shutDown() {
        try (LDLogLevelController logLevels = LDLogLevelController
                .builder(client, serverContext)
                .consoleLogFlagKey("console-log-level")
                .build()) {
            logLevels.start();
            runApplication();
        }
    }

    public final class MyBackendBridge implements LogLevelBridge {

        @Override
        public void setLevel(String loggerName, LogLevel level) {
            // Apply it. loggerName uses SLF4J's convention, so the root logger is
            // named Logger.ROOT_LOGGER_NAME ("ROOT"), not "" as some backends use.
        }

        @Override
        public boolean isAvailable() {
            // Report false unless your backend is the one SLF4J is bound to.
            return true;
        }

        @Override
        public String backendName() {
            return "My Backend";
        }
    }

    void supplyACustomBridge() {
        LDLogLevelController.builder(client, serverContext)
            .consoleLogFlagKey("console-log-level")
            .logLevelBridge(new MyBackendBridge())
            .build();
    }
}
