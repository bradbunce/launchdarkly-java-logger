package io.github.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Backs up the class-level claim that instances are thread-safe. The controller
 * is typically started from application bootstrap while the SDK's own event
 * thread delivers flag changes, so those paths really do overlap in production.
 */
class LDLogLevelControllerConcurrencyTest {

    private static final String CONSOLE_FLAG = "console-log-level";
    private static final String SDK_FLAG = "sdk-log-level";
    private static final int THREADS = 8;

    private final LDContext context = LDContext.create("checkout-service");
    private final FakeLDClient client = new FakeLDClient();
    private final RecordingLogLevelBridge bridge = RecordingLogLevelBridge.available();

    /** Runs an action on every thread at once and rethrows the first failure. */
    private static void inParallel(int threads, Runnable action) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        if (!go.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timed out waiting to start");
                        }
                        action.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failure.compareAndSet(null, e);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
        }
        if (failure.get() != null) {
            throw new AssertionError("a worker thread failed", failure.get());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("concurrent start() calls evaluate and subscribe exactly once")
    void concurrentStartsAreIdempotent() throws InterruptedException {
        client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.DEBUG.flagValue());
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .logLevelBridge(bridge)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .sdkLogFlagKey(SDK_FLAG)
                .build();

        inParallel(THREADS, controller::start);

        assertThat(client.intVariationCalls()).hasSize(1);
        assertThat(client.stringVariationCalls()).hasSize(1);
        assertThat(client.tracker().registrationCount()).isEqualTo(2);
        assertThat(bridge.changes()).hasSize(2);
    }

    @Test
    @Timeout(30)
    @DisplayName("concurrent flag changes all take effect and leave a valid level")
    void concurrentChangesAreSerialized() throws InterruptedException {
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .logLevelBridge(bridge)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .build();
        controller.start();
        bridge.clearChanges();
        AtomicInteger callbacks = new AtomicInteger();
        int changesPerThread = 50;

        inParallel(THREADS, () -> {
            for (int i = 0; i < changesPerThread; i++) {
                int flagValue = i % 6;
                client.tracker().fireValueChange(CONSOLE_FLAG, LDValue.of(flagValue));
                callbacks.incrementAndGet();
                // Interleave reads with writes to shake out unsynchronized state.
                assertThat(controller.currentConsoleLogLevel()).isNotNull();
            }
        });

        int expected = THREADS * changesPerThread;
        assertThat(callbacks).hasValue(expected);
        assertThat(bridge.changes()).hasSize(expected);
        assertThat(controller.currentConsoleLogLevel()).isIn(List.of(ConsoleLogLevel.values()));
    }

    @Test
    @Timeout(30)
    @DisplayName("concurrent close() calls unsubscribe exactly once")
    void concurrentClosesAreIdempotent() throws InterruptedException {
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .logLevelBridge(bridge)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .sdkLogFlagKey(SDK_FLAG)
                .build();
        controller.start();

        inParallel(THREADS, controller::close);

        assertThat(client.tracker().registrations()).isEmpty();
    }

    @Test
    @Timeout(30)
    @DisplayName("reads during start() never see a torn state")
    void readsDuringStartAreConsistent() throws InterruptedException {
        client.withIntFlag(CONSOLE_FLAG, ConsoleLogLevel.TRACE.flagValue());
        LDLogLevelController controller = LDLogLevelController.builder(client, context)
                .logLevelBridge(bridge)
                .consoleLogFlagKey(CONSOLE_FLAG)
                .defaultConsoleLogLevel(ConsoleLogLevel.ERROR)
                .build();

        inParallel(THREADS, () -> {
            controller.start();
            // Either the pre-start default or the applied value, never anything else.
            assertThat(controller.currentConsoleLogLevel())
                    .isIn(ConsoleLogLevel.ERROR, ConsoleLogLevel.TRACE);
        });

        assertThat(controller.currentConsoleLogLevel()).isEqualTo(ConsoleLogLevel.TRACE);
    }
}
