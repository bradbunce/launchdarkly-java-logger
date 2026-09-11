package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.integrations.TestData;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Proves the SDK log level flag takes effect on a running client, with no
 * re-initialization.
 *
 * <p>This is worth an integration test against a real {@link LDClient} rather
 * than a fake, because the question it answers is about the SDK's internals: the
 * SDK can filter its own output before anything reaches SLF4J, in which case
 * raising the level at runtime would do nothing. It does not, because
 * {@code LDSLF4J}'s adapter implements
 * {@code LDLogAdapter.IsConfiguredExternally}, which makes the SDK skip its own
 * level filter and delegate filtering entirely to SLF4J.
 */
class SdkLogLevelIntegrationTest {

    private static final String SDK_FLAG = "sdk-log-level";

    private final TestData testData = TestData.dataSource();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LDClient client;
    private ch.qos.logback.classic.Logger sdkLogger;
    private Level savedLevel;
    private boolean savedAdditivity;

    @BeforeEach
    void startClient() {
        testData.update(testData.flag(SDK_FLAG).valueForAll(LDValue.of("error")));

        LDConfig config = new LDConfig.Builder()
                .logging(LDSdkLogging.slf4j())
                .dataSource(testData)
                .events(Components.noEvents())
                .startWait(Duration.ofSeconds(10))
                .build();
        client = new LDClient("integration-test-no-key", config);

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        sdkLogger = context.getLogger(LDLogLevelController.DEFAULT_SDK_LOGGER_NAME);
        savedLevel = sdkLogger.getLevel();
        savedAdditivity = sdkLogger.isAdditive();
        sdkLogger.setAdditive(false);
        appender.setContext(context);
        appender.start();
        sdkLogger.addAppender(appender);
    }

    @AfterEach
    void stopClient() throws IOException {
        sdkLogger.detachAppender(appender);
        appender.stop();
        sdkLogger.setLevel(savedLevel);
        sdkLogger.setAdditive(savedAdditivity);
        client.close();
    }

    private List<String> capturedAt(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    @DisplayName("the SDK logs through SLF4J under the logger the flag controls")
    void sdkLogsUnderTheControlledLogger() {
        sdkLogger.setLevel(Level.TRACE);

        client.getLogger().warn("probe from the SDK's own logger");

        // Confirms the SDK's logger name really does sit under the name this
        // library targets, which is what makes one flag able to control it.
        assertThat(capturedAt(Level.WARN)).contains("probe from the SDK's own logger");
        assertThat(appender.list)
                .allSatisfy(event -> assertThat(event.getLoggerName())
                        .startsWith(LDLogLevelController.DEFAULT_SDK_LOGGER_NAME));
    }

    @Test
    @DisplayName("raising the SDK log level on a running client takes effect without a re-init")
    void raisingTheLevelNeedsNoReinit() {
        try (LDLogLevelController controller = LDLogLevelController
                .builder(client, LDContext.create("checkout-service"))
                .sdkLogFlagKey(SDK_FLAG)
                .build()) {
            controller.start();
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.ERROR);

            client.getLogger().debug("suppressed while the flag says error");
            assertThat(capturedAt(Level.DEBUG)).isEmpty();

            // The same client instance, never rebuilt.
            LDClient sameClient = client;
            testData.update(testData.flag(SDK_FLAG).valueForAll(LDValue.of("debug")));
            awaitSdkLevel(controller, SdkLogLevel.DEBUG);

            sameClient.getLogger().debug("emitted after the flag changed to debug");

            assertThat(capturedAt(Level.DEBUG))
                    .containsExactly("emitted after the flag changed to debug");
        }
    }

    @Test
    @DisplayName("lowering the SDK log level suppresses output again, still without a re-init")
    void loweringTheLevelNeedsNoReinit() {
        testData.update(testData.flag(SDK_FLAG).valueForAll(LDValue.of("debug")));

        try (LDLogLevelController controller = LDLogLevelController
                .builder(client, LDContext.create("checkout-service"))
                .sdkLogFlagKey(SDK_FLAG)
                .build()) {
            controller.start();
            client.getLogger().debug("emitted while the flag says debug");
            assertThat(capturedAt(Level.DEBUG)).hasSize(1);

            testData.update(testData.flag(SDK_FLAG).valueForAll(LDValue.of("error")));
            awaitSdkLevel(controller, SdkLogLevel.ERROR);

            client.getLogger().debug("suppressed after the flag changed back");

            assertThat(capturedAt(Level.DEBUG))
                    .containsExactly("emitted while the flag says debug");
        }
    }

    @Test
    @DisplayName("the SDK applies no level filter of its own when logging through SLF4J")
    void sdkAppliesNoInternalFilter() {
        // If the SDK wrapped its adapter in a LevelFilter, no amount of Logback
        // configuration could make debug output appear on a running client.
        sdkLogger.setLevel(Level.DEBUG);

        client.getLogger().debug("reached SLF4J unfiltered by the SDK");

        assertThat(capturedAt(Level.DEBUG)).containsExactly("reached SLF4J unfiltered by the SDK");
    }

    private static void awaitSdkLevel(LDLogLevelController controller, SdkLogLevel expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (controller.currentSdkLogLevel().orElse(null) == expected) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("SDK log level never became " + expected);
    }
}
