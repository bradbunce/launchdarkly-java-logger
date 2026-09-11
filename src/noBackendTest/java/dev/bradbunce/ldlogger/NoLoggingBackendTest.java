package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

/**
 * Runs with slf4j-api but no backend on the classpath, which is what a consumer
 * who never added Logback gets. The library must degrade quietly rather than
 * fail to load or blow up at startup.
 */
class NoLoggingBackendTest {

    private static final String CONSOLE_FLAG = "console-log-level";
    private static final String SDK_FLAG = "sdk-log-level";

    private final LDContext context = LDContext.create("checkout-service");
    private final FakeLDClient client = new FakeLDClient();

    private LDLogLevelController controller() {
        return LDLogLevelController.builder(client, context)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .sdkLogFlagKey(SDK_FLAG)
                .build();
    }

    @Test
    @DisplayName("this source set really does run without a logging backend")
    void hasNoBackendBound() {
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(NOPLoggerFactory.class);
        assertThatThrownBy(() -> Class.forName("ch.qos.logback.classic.LoggerContext"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("the Logback bridge reports Logback absent instead of failing to load")
    void logbackBridgeLoadsWithoutLogback() {
        // Referencing the class at all would fail if the JVM eagerly resolved
        // its Logback references, so this is a load-time regression guard too.
        assertThat(LogbackLogLevelBridge.isLogbackBound()).isFalse();
    }

    @Test
    @DisplayName("detect() falls back to the unavailable bridge")
    void detectFallsBackToUnavailable() {
        LogLevelBridge bridge = LogLevelBridge.detect();

        assertThat(bridge).isInstanceOf(UnavailableLogLevelBridge.class);
        assertThat(bridge.isAvailable()).isFalse();
        assertThat(bridge.backendName()).isEqualTo("none");
        assertThatIllegalStateException()
                .isThrownBy(() -> bridge.setLevel(Logger.ROOT_LOGGER_NAME, LogLevel.DEBUG))
                .withMessageContaining("logback-classic");
    }

    @Test
    @DisplayName("a controller built with the detected bridge is inactive")
    void controllerIsInactive() {
        LDLogLevelController controller = controller();

        assertThat(controller.bridge().isAvailable()).isFalse();
        assertThatNoException().isThrownBy(controller::start);
        assertThat(client.intVariationCalls()).isEmpty();
        assertThat(client.stringVariationCalls()).isEmpty();
        assertThat(client.tracker().registrations()).isEmpty();
    }

    @Test
    @DisplayName("an inactive controller still reports its defaults")
    void reportsDefaults() {
        client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.TRACE.flagValue());
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .sdkLogFlagKey(SDK_FLAG)
                .defaultConsoleLogLevel(ConsoleLogLevel.INFO)
                .defaultSdkLogLevel(SdkLogLevel.WARN)
                .build();

        controller.start();

        assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.INFO);
        assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.WARN);
    }

    @Test
    @DisplayName("an inactive controller closes cleanly and can be restarted")
    void closesCleanly() {
        LDLogLevelController controller = controller();
        controller.start();

        assertThatNoException().isThrownBy(controller::close);
        assertThatNoException().isThrownBy(controller::start);
    }

    @Test
    @DisplayName("no callbacks fire, since no level is ever applied")
    void callbacksNeverFire() {
        List<ConsoleLogLevel> levels = new ArrayList<>();
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .onConsoleLogLevelChange(levels::add)
                .build();

        controller.start();

        assertThat(levels).isEmpty();
    }

    @Test
    @DisplayName("the level enums work with no backend, since they touch no logger")
    void enumsStillWork() {
        assertThat(ConsoleLogLevel.fromFlagValue(4)).contains(ConsoleLogLevel.DEBUG);
        assertThat(SdkLogLevel.fromFlagValue("debug")).contains(SdkLogLevel.DEBUG);
        assertThat(LDValue.of(4).intValue()).isEqualTo(4);
    }
}
