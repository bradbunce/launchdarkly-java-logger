package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the warnings the controller emits when something is misconfigured.
 * These messages are the only signal an operator gets that a flag value was
 * rejected, so their content is part of the library's contract.
 */
class LDLogLevelControllerDiagnosticsTest {

    private static final String CONSOLE_FLAG = "console-log-level";
    private static final String SDK_FLAG = "sdk-log-level";

    private final LDContext context = LDContext.create("checkout-service");
    private final FakeLDClient client = new FakeLDClient();
    private final RecordingLogLevelBridge bridge = RecordingLogLevelBridge.available();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private ch.qos.logback.classic.Logger libraryLogger;
    private Level savedLevel;
    private boolean savedAdditivity;

    @BeforeEach
    void captureLibraryLogs() {
        LoggerContext loggerContext = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        libraryLogger = loggerContext.getLogger(LDLogLevelController.class);
        savedLevel = libraryLogger.getLevel();
        savedAdditivity = libraryLogger.isAdditive();
        // logback-test.xml pins this namespace to OFF; turn it back on just here,
        // and keep the captured output off the console.
        libraryLogger.setLevel(Level.WARN);
        libraryLogger.setAdditive(false);
        appender.setContext(loggerContext);
        appender.start();
        libraryLogger.addAppender(appender);
    }

    @AfterEach
    void restoreLibraryLogger() {
        libraryLogger.detachAppender(appender);
        appender.stop();
        libraryLogger.setLevel(savedLevel);
        libraryLogger.setAdditive(savedAdditivity);
    }

    private List<String> warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private LDLogLevelController.Builder builder() {
        return LDLogLevelController.builder(client, context).logLevelBridge(bridge);
    }

    @Test
    @DisplayName("warns with remediation when no logging backend is bound")
    void warnsWhenNoBackendIsBound() {
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .logLevelBridge(RecordingLogLevelBridge.unavailable())
                .consoleLogFlagKey(CONSOLE_FLAG)
                .build();

        controller.start();

        assertThat(warnings())
                .singleElement()
                .asString()
                .contains("inactive")
                .contains("logback-classic");
    }

    @Test
    @DisplayName("warns when a console flag value is outside the valid range")
    void warnsOnOutOfRangeConsoleValue() {
        client.withIntFlag(CONSOLE_FLAG, 99);
        LDLogLevelController controller = builder()
                .consoleLogFlagKey(CONSOLE_FLAG)
                .defaultConsoleLogLevel(ConsoleLogLevel.WARN)
                .build();

        controller.start();

        assertThat(warnings())
                .singleElement()
                .asString()
                .contains(CONSOLE_FLAG)
                .contains("99")
                .contains("not a valid level (0-5)")
                .contains("WARN");
    }

    @Test
    @DisplayName("warns when a console flag value is not a number")
    void warnsOnNonNumericConsoleValue() {
        LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
        controller.start();
        appender.list.clear();

        client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of("debug"));

        assertThat(warnings())
                .singleElement()
                .asString()
                .contains(CONSOLE_FLAG)
                .contains("not a number");
    }

    @Test
    @DisplayName("warns when an SDK flag value is not a recognized level")
    void warnsOnUnknownSdkValue() {
        client.withStringFlag(SDK_FLAG, "verbose");
        LDLogLevelController controller = builder().sdkLogFlagKey(SDK_FLAG).build();

        controller.start();

        assertThat(warnings())
                .singleElement()
                .asString()
                .contains(SDK_FLAG)
                .contains("error/warn/info/debug");
    }

    @Test
    @DisplayName("a valid configuration produces no warnings")
    void staysQuietWhenEverythingIsValid() {
        client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.DEBUG.flagValue())
                .withStringFlag(SDK_FLAG, SdkLogLevel.DEBUG.flagValue());
        LDLogLevelController controller =
                builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();

        controller.start();
        client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(2));
        client.tracker().fireValueChange(SDK_FLAG, LDValue.of("info"));

        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("warns, with the stack trace, when a change callback throws")
    void warnsWhenACallbackThrows() {
        client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.INFO.flagValue());
        LDLogLevelController controller = builder()
                .consoleLogFlagKey(CONSOLE_FLAG)
                .onConsoleLogLevelChange(level -> {
                    throw new IllegalStateException("callback is broken");
                })
                .build();

        controller.start();

        assertThat(warnings()).singleElement().asString().contains("callback threw an exception");
        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> assertThat(event.getThrowableProxy().getMessage())
                        .isEqualTo("callback is broken"));
    }

    @Test
    @DisplayName("names which callback failed, console or SDK")
    void namesTheFailingCallback() {
        client.withStringFlag(SDK_FLAG, SdkLogLevel.INFO.flagValue());
        LDLogLevelController controller = builder()
                .sdkLogFlagKey(SDK_FLAG)
                .onSdkLogLevelChange(level -> {
                    throw new IllegalStateException("callback is broken");
                })
                .build();

        controller.start();

        assertThat(warnings()).singleElement().asString().startsWith("SDK log level change");
    }
}
