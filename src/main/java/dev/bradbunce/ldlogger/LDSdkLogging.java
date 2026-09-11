package dev.bradbunce.ldlogger;

import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;

/**
 * Helpers for routing the LaunchDarkly SDK's own logging through SLF4J.
 *
 * <p>This matters because the Java SDK does <em>not</em> use SLF4J by default:
 * {@code Logs.basic()} writes directly to the console at {@code INFO} and does
 * no auto-detection. Until the SDK is configured as below, a flag-driven SDK log
 * level has nothing to act on.
 *
 * <pre>{@code
 * LDConfig config = new LDConfig.Builder()
 *     .logging(LDSdkLogging.slf4j())
 *     .build();
 * }</pre>
 */
public final class LDSdkLogging {

    private LDSdkLogging() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns a logging configuration that sends SDK logs to SLF4J, allowing
     * {@link LDLogLevelController} to control the SDK's log level at runtime.
     *
     * @return a logging configuration builder for {@code LDConfig.Builder.logging}
     */
    public static LoggingConfigurationBuilder slf4j() {
        return Components.logging(LDSLF4J.adapter());
    }
}
