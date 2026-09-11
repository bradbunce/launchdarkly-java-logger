package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import dev.bradbunce.ldlogger.RecordingLogLevelBridge.LevelChange;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

class LDLogLevelControllerTest {

    private static final String CONSOLE_FLAG = "console-log-level";
    private static final String SDK_FLAG = "sdk-log-level";
    private static final String ROOT = Logger.ROOT_LOGGER_NAME;

    private final LDContext context = LDContext.create("checkout-service");
    private final FakeLDClient client = new FakeLDClient();
    private final RecordingLogLevelBridge bridge = RecordingLogLevelBridge.available();

    private LDLogLevelController.Builder builder() {
        return LDLogLevelController.builder(client, context).logLevelBridge(bridge);
    }

    @Nested
    @DisplayName("builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("rejects a null client")
        void rejectsNullClient() {
            assertThatNullPointerException()
                    .isThrownBy(() -> LDLogLevelController.builder(null, context))
                    .withMessageContaining("client");
        }

        @Test
        @DisplayName("rejects a null context")
        void rejectsNullContext() {
            assertThatNullPointerException()
                    .isThrownBy(() -> LDLogLevelController.builder(client, null))
                    .withMessageContaining("context");
        }

        @Test
        @DisplayName("rejects a configuration with no flag keys, which would do nothing")
        void rejectsNoFlagKeys() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder().build())
                    .withMessageContaining("consoleLogFlagKey")
                    .withMessageContaining("sdkLogFlagKey");
        }

        @Test
        @DisplayName("builds with only a console flag")
        void buildsWithConsoleFlagAlone() {
            assertThatNoException().isThrownBy(() -> builder().consoleLogFlagKey(CONSOLE_FLAG).build());
        }

        @Test
        @DisplayName("builds with only an SDK flag")
        void buildsWithSdkFlagAlone() {
            assertThatNoException().isThrownBy(() -> builder().sdkLogFlagKey(SDK_FLAG).build());
        }

        @Test
        @DisplayName("rejects null logger names and default levels")
        void rejectsNullSettings() {
            assertThatNullPointerException().isThrownBy(() -> builder().applicationLoggerName(null));
            assertThatNullPointerException().isThrownBy(() -> builder().sdkLoggerName(null));
            assertThatNullPointerException().isThrownBy(() -> builder().defaultConsoleLogLevel(null));
            assertThatNullPointerException().isThrownBy(() -> builder().defaultSdkLogLevel(null));
        }

        @Test
        @DisplayName("null flag keys are allowed, meaning 'not configured'")
        void allowsNullFlagKeys() {
            assertThatNoException()
                    .isThrownBy(() ->
                            builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(null).build());
        }
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("applies the console flag's level to the root logger by default")
        void appliesConsoleLevelToRoot() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.DEBUG.flagValue());
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();

            controller.start();

            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.DEBUG));
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.DEBUG);
        }

        @Test
        @DisplayName("applies the SDK flag's level to the com.launchdarkly logger by default")
        void appliesSdkLevelToSdkLogger() {
            client.withStringFlag(SDK_FLAG, SdkLogLevel.DEBUG.flagValue());
            LDLogLevelController controller = builder().sdkLogFlagKey(SDK_FLAG).build();

            controller.start();

            assertThat(LDLogLevelController.DEFAULT_SDK_LOGGER_NAME).isEqualTo("com.launchdarkly");
            assertThat(bridge.changes())
                    .containsExactly(
                            new LevelChange(
                                    LDLogLevelController.DEFAULT_SDK_LOGGER_NAME, LogLevel.DEBUG));
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.DEBUG);
        }

        @Test
        @DisplayName("applies both levels when both flags are configured")
        void appliesBothLevels() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.WARN.flagValue())
                    .withStringFlag(SDK_FLAG, SdkLogLevel.INFO.flagValue());
            LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();

            controller.start();

            assertThat(bridge.changes())
                    .containsExactly(
                            new LevelChange(ROOT, LogLevel.WARN),
                            new LevelChange("com.launchdarkly", LogLevel.INFO));
        }

        @Test
        @DisplayName("honours custom logger names")
        void honoursCustomLoggerNames() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.TRACE.flagValue())
                    .withStringFlag(SDK_FLAG, SdkLogLevel.WARN.flagValue());
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .applicationLoggerName("com.example.checkout")
                    .sdkLoggerName("com.example.vendor.ld")
                    .build();

            controller.start();

            assertThat(bridge.changes())
                    .containsExactly(
                            new LevelChange("com.example.checkout", LogLevel.TRACE),
                            new LevelChange("com.example.vendor.ld", LogLevel.WARN));
        }

        @Test
        @DisplayName("evaluates each flag against the configured context, defaulting to the configured level")
        void evaluatesAgainstTheConfiguredContext() {
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.FATAL)
                    .defaultSdkLogLevel(SdkLogLevel.WARN)
                    .build();

            controller.start();

            assertThat(client.intVariationCalls())
                    .containsExactly(new FakeLDClient.IntVariationCall(CONSOLE_FLAG, context, 0));
            assertThat(client.stringVariationCalls())
                    .containsExactly(new FakeLDClient.StringVariationCall(SDK_FLAG, context, "warn"));
        }

        @Test
        @DisplayName("subscribes to each configured flag with the same context")
        void subscribesToConfiguredFlags() {
            LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();

            controller.start();

            assertThat(client.tracker().subscribedFlagKeys()).containsExactly(CONSOLE_FLAG, SDK_FLAG);
            assertThat(client.tracker().registrations())
                    .allSatisfy(registration -> assertThat(registration.context()).isSameAs(context));
        }

        @Test
        @DisplayName("touches nothing for a flag that is not configured")
        void ignoresUnconfiguredFlags() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();

            controller.start();

            assertThat(client.stringVariationCalls()).isEmpty();
            assertThat(client.tracker().subscribedFlagKeys()).containsExactly(CONSOLE_FLAG);
            assertThat(bridge.lastLevelOf("com.launchdarkly")).isEmpty();
            assertThat(controller.currentSdkLogLevel()).isEmpty();
        }

        @Test
        @DisplayName("does not evaluate the console flag when only the SDK flag is configured")
        void ignoresUnconfiguredConsoleFlag() {
            LDLogLevelController controller = builder().sdkLogFlagKey(SDK_FLAG).build();

            controller.start();

            assertThat(client.intVariationCalls()).isEmpty();
            assertThat(bridge.lastLevelOf(ROOT)).isEmpty();
        }

        @Test
        @DisplayName("is idempotent: a second call re-evaluates nothing and re-subscribes nothing")
        void isIdempotent() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.INFO.flagValue());
            LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();

            controller.start();
            controller.start();
            controller.start();

            assertThat(client.intVariationCalls()).hasSize(1);
            assertThat(client.stringVariationCalls()).hasSize(1);
            assertThat(client.tracker().registrationCount()).isEqualTo(2);
            assertThat(bridge.changes()).hasSize(2);
        }

        @Test
        @DisplayName("FATAL reaches the bridge as FATAL, not pre-collapsed to ERROR")
        void appliesFatalAsFatal() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.FATAL.flagValue());
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();

            controller.start();

            // Backends that have a real FATAL level need to see it; collapsing to
            // ERROR is each bridge's decision, not the controller's.
            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.FATAL));
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.FATAL);
            assertThat(ConsoleLogLevel.FATAL.slf4jLevel())
                    .isEqualTo(org.slf4j.event.Level.ERROR);
        }
    }

    @Nested
    @DisplayName("before start()")
    class BeforeStart {

        @Test
        @DisplayName("reports the configured defaults and touches nothing")
        void reportsDefaults() {
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.INFO)
                    .defaultSdkLogLevel(SdkLogLevel.DEBUG)
                    .build();

            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.INFO);
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.DEBUG);
            assertThat(bridge.changes()).isEmpty();
            assertThat(client.intVariationCalls()).isEmpty();
            assertThat(client.tracker().registrations()).isEmpty();
        }

        @Test
        @DisplayName("defaults to ERROR for both levels")
        void defaultsToError() {
            LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();

            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.ERROR);
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.ERROR);
        }

        @Test
        @DisplayName("exposes the bridge in use")
        void exposesTheBridge() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();

            assertThat(controller.bridge()).isSameAs(bridge);
        }

        @Test
        @DisplayName("detects the backend when no bridge is supplied")
        void detectsTheBackendByDefault() {
            LDLogLevelController controller = LDLogLevelController.builder(client, context)
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .build();

            assertThat(controller.bridge()).isInstanceOf(LogbackLogLevelBridge.class);
        }
    }

    @Nested
    @DisplayName("invalid flag values")
    class InvalidFlagValues {

        @ParameterizedTest
        @ValueSource(ints = {-1, 6, 42})
        @DisplayName("a console value outside 0-5 falls back to the default level")
        void outOfRangeConsoleValueFallsBack(int flagValue) {
            client.withIntFlag(CONSOLE_FLAG, flagValue);
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.WARN)
                    .build();

            controller.start();

            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.WARN));
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.WARN);
        }

        @ParameterizedTest
        @ValueSource(strings = {"verbose", "trace", "", "fatal"})
        @DisplayName("an unrecognized SDK value falls back to the default level")
        void unknownSdkValueFallsBack(String flagValue) {
            client.withStringFlag(SDK_FLAG, flagValue);
            LDLogLevelController controller = builder()
                    .sdkLogFlagKey(SDK_FLAG)
                    .defaultSdkLogLevel(SdkLogLevel.INFO)
                    .build();

            controller.start();

            assertThat(bridge.changes())
                    .containsExactly(new LevelChange("com.launchdarkly", LogLevel.INFO));
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.INFO);
        }

        @Test
        @DisplayName("an SDK value with odd casing and whitespace is still accepted")
        void normalizesSdkValue() {
            client.withStringFlag(SDK_FLAG, "  DeBuG  ");
            LDLogLevelController controller = builder().sdkLogFlagKey(SDK_FLAG).build();

            controller.start();

            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.DEBUG);
        }

        @Test
        @DisplayName("a missing flag yields the SDK default, which is the configured level")
        void missingFlagUsesDefault() {
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.TRACE)
                    .defaultSdkLogLevel(SdkLogLevel.DEBUG)
                    .build();

            controller.start();

            assertThat(bridge.changes())
                    .containsExactly(
                            new LevelChange(ROOT, LogLevel.TRACE),
                            new LevelChange("com.launchdarkly", LogLevel.DEBUG));
        }
    }

    @Nested
    @DisplayName("flag changes")
    class FlagChanges {

        @Test
        @DisplayName("a console flag change applies the new level immediately")
        void appliesConsoleChange() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.ERROR.flagValue());
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(4));

            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.DEBUG));
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.DEBUG);
        }

        @Test
        @DisplayName("an SDK flag change applies the new level immediately")
        void appliesSdkChange() {
            client.withStringFlag(SDK_FLAG, SdkLogLevel.ERROR.flagValue());
            LDLogLevelController controller = builder().sdkLogFlagKey(SDK_FLAG).build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(SDK_FLAG, LDValue.of("info"));

            assertThat(bridge.changes())
                    .containsExactly(new LevelChange("com.launchdarkly", LogLevel.INFO));
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.INFO);
        }

        @Test
        @DisplayName("successive changes each take effect")
        void appliesSuccessiveChanges() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(5));
            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(2));
            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(3));

            assertThat(bridge.changes())
                    .containsExactly(
                            new LevelChange(ROOT, LogLevel.TRACE),
                            new LevelChange(ROOT, LogLevel.WARN),
                            new LevelChange(ROOT, LogLevel.INFO));
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.INFO);
        }

        @Test
        @DisplayName("a change affects only the flag that changed")
        void changesAreIndependent() {
            LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(4));

            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.DEBUG));
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.ERROR);
        }

        @Test
        @DisplayName("a non-numeric console value falls back to the default level")
        void nonNumericConsoleValueFallsBack() {
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.WARN)
                    .build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of("debug"));

            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.WARN));
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.WARN);
        }

        @Test
        @DisplayName("a whole-numbered double console value is accepted")
        void acceptsWholeNumberedDouble() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(4.0));

            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.DEBUG);
        }

        @Test
        @DisplayName("a null console value falls back to the default level")
        void nullConsoleValueFallsBack() {
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.INFO)
                    .build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.ofNull());

            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.INFO);
        }

        @Test
        @DisplayName("a non-string SDK value falls back to the default level")
        void nonStringSdkValueFallsBack() {
            LDLogLevelController controller = builder()
                    .sdkLogFlagKey(SDK_FLAG)
                    .defaultSdkLogLevel(SdkLogLevel.WARN)
                    .build();
            controller.start();
            bridge.clearChanges();

            // Unlike the start()-time read, which goes through stringVariation and
            // is always a string, a change event carries the raw flag value.
            client.tracker().fireValueChange(SDK_FLAG, LDValue.of(3));

            assertThat(bridge.changes())
                    .containsExactly(new LevelChange("com.launchdarkly", LogLevel.WARN));
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.WARN);
        }

        @Test
        @DisplayName("an invalid value does not strand the controller: later valid values still apply")
        void recoversFromInvalidValues() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();
            bridge.clearChanges();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(99));
            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(5));

            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.TRACE);
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {

        @Test
        @DisplayName("unsubscribes from every flag")
        void unsubscribes() {
            LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).sdkLogFlagKey(SDK_FLAG).build();
            controller.start();

            controller.close();

            assertThat(client.tracker().registrations()).isEmpty();
        }

        @Test
        @DisplayName("stops responding to later flag changes")
        void stopsRespondingToChanges() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.INFO.flagValue());
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();
            bridge.clearChanges();

            controller.close();
            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(5));

            assertThat(bridge.changes()).isEmpty();
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.INFO);
        }

        @Test
        @DisplayName("leaves the levels it already applied in place")
        void leavesAppliedLevelsAlone() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.TRACE.flagValue());
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();

            controller.close();

            assertThat(bridge.lastLevelOf(ROOT)).contains(LogLevel.TRACE);
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.TRACE);
        }

        @Test
        @DisplayName("is a no-op before start()")
        void isANoOpBeforeStart() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();

            assertThatNoException().isThrownBy(controller::close);
            assertThat(bridge.changes()).isEmpty();
        }

        @Test
        @DisplayName("is idempotent")
        void isIdempotent() {
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();

            assertThatNoException().isThrownBy(() -> {
                controller.close();
                controller.close();
            });
        }

        @Test
        @DisplayName("a closed controller can be started again")
        void canBeRestarted() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.WARN.flagValue());
            LDLogLevelController controller = builder().consoleLogFlagKey(CONSOLE_FLAG).build();
            controller.start();
            controller.close();
            bridge.clearChanges();

            controller.start();

            assertThat(bridge.changes()).containsExactly(new LevelChange(ROOT, LogLevel.WARN));
            assertThat(client.tracker().subscribedFlagKeys()).containsExactly(CONSOLE_FLAG);
            assertThat(client.tracker().registrationCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("works as an AutoCloseable resource")
        void worksInTryWithResources() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.DEBUG.flagValue());

            try (LDLogLevelController controller =
                    builder().consoleLogFlagKey(CONSOLE_FLAG).build()) {
                controller.start();
                assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.DEBUG);
            }

            assertThat(client.tracker().registrations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("change callbacks")
    class Callbacks {

        @Test
        @DisplayName("fire on start() with the level that was applied")
        void fireOnStart() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.DEBUG.flagValue())
                    .withStringFlag(SDK_FLAG, SdkLogLevel.WARN.flagValue());
            List<ConsoleLogLevel> consoleLevels = new ArrayList<>();
            List<SdkLogLevel> sdkLevels = new ArrayList<>();
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .onConsoleLogLevelChange(consoleLevels::add)
                    .onSdkLogLevelChange(sdkLevels::add)
                    .build();

            controller.start();

            assertThat(consoleLevels).containsExactly(ConsoleLogLevel.DEBUG);
            assertThat(sdkLevels).containsExactly(SdkLogLevel.WARN);
        }

        @Test
        @DisplayName("fire on every flag change, including fallbacks to the default")
        void fireOnEveryChange() {
            List<ConsoleLogLevel> levels = new ArrayList<>();
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .defaultConsoleLogLevel(ConsoleLogLevel.ERROR)
                    .onConsoleLogLevelChange(levels::add)
                    .build();
            controller.start();

            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(5));
            client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(99));

            assertThat(levels)
                    .containsExactly(
                            ConsoleLogLevel.ERROR, ConsoleLogLevel.TRACE, ConsoleLogLevel.ERROR);
        }

        @Test
        @DisplayName("observe the new level as already in effect")
        void observeTheNewLevel() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.DEBUG.flagValue());
            AtomicReference<LDLogLevelController> controllerRef = new AtomicReference<>();
            AtomicReference<ConsoleLogLevel> observed = new AtomicReference<>();
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .onConsoleLogLevelChange(
                            level -> observed.set(controllerRef.get().currentConsoleLogLevel()))
                    .build();
            controllerRef.set(controller);

            controller.start();

            assertThat(observed.get()).isEqualTo(ConsoleLogLevel.DEBUG);
        }

        @Test
        @DisplayName("a callback that throws does not break level control")
        void aThrowingCallbackIsContained() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.INFO.flagValue());
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .onConsoleLogLevelChange(level -> {
                        throw new IllegalStateException("callback is broken");
                    })
                    .build();

            assertThatNoException().isThrownBy(controller::start);
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.INFO);

            // Control must survive the failure, not just the first call.
            assertThatNoException()
                    .isThrownBy(() -> client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(5)));
            assertThat(bridge.lastLevelOf(ROOT)).contains(LogLevel.TRACE);
            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.TRACE);
        }

        @Test
        @DisplayName("an SDK callback that throws does not break level control")
        void aThrowingSdkCallbackIsContained() {
            client.withStringFlag(SDK_FLAG, SdkLogLevel.INFO.flagValue());
            LDLogLevelController controller = builder()
                    .sdkLogFlagKey(SDK_FLAG)
                    .onSdkLogLevelChange(level -> {
                        throw new IllegalStateException("callback is broken");
                    })
                    .build();

            assertThatNoException().isThrownBy(controller::start);
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.INFO);
        }

        @Test
        @DisplayName("are optional")
        void areOptional() {
            LDLogLevelController controller = builder()
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .onConsoleLogLevelChange(null)
                    .onSdkLogLevelChange(null)
                    .build();

            assertThatNoException().isThrownBy(controller::start);
        }
    }

    @Nested
    @DisplayName("with no usable logging backend")
    class NoBackend {

        private final RecordingLogLevelBridge unavailable = RecordingLogLevelBridge.unavailable();

        private LDLogLevelController controller() {
            return LDLogLevelController.builder(client, context)
                    .logLevelBridge(unavailable)
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .sdkLogFlagKey(SDK_FLAG)
                    .build();
        }

        @Test
        @DisplayName("start() gives up instead of throwing")
        void startDoesNotThrow() {
            assertThatNoException().isThrownBy(controller()::start);
        }

        @Test
        @DisplayName("start() evaluates nothing and subscribes to nothing")
        void doesNothing() {
            controller().start();

            assertThat(unavailable.changes()).isEmpty();
            assertThat(client.intVariationCalls()).isEmpty();
            assertThat(client.stringVariationCalls()).isEmpty();
            assertThat(client.tracker().registrations()).isEmpty();
        }

        @Test
        @DisplayName("reported levels stay at the configured defaults")
        void reportsDefaults() {
            client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.TRACE.flagValue());
            LDLogLevelController controller = controller();

            controller.start();

            assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.ERROR);
            assertThat(controller.currentSdkLogLevel()).contains(SdkLogLevel.ERROR);
        }

        @Test
        @DisplayName("close() after a no-op start is safe")
        void closeIsSafe() {
            LDLogLevelController controller = controller();
            controller.start();

            assertThatNoException().isThrownBy(controller::close);
        }

        @Test
        @DisplayName("callbacks never fire")
        void callbacksNeverFire() {
            List<ConsoleLogLevel> levels = new ArrayList<>();
            LDLogLevelController controller = LDLogLevelController.builder(client, context)
                    .logLevelBridge(unavailable)
                    .consoleLogFlagKey(CONSOLE_FLAG)
                    .onConsoleLogLevelChange(levels::add)
                    .build();

            controller.start();

            assertThat(levels).isEmpty();
        }
    }
}
