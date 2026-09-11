package io.github.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogLevelBridgeTest {

    @AfterEach
    void resetServiceBridge() {
        FakeServiceBridge.available = true;
    }

    @Test
    @DisplayName("detect() picks the Logback bridge when Logback is bound")
    void detectsLogback() {
        LogLevelBridge bridge = LogLevelBridge.detect();

        assertThat(bridge).isInstanceOf(LogbackLogLevelBridge.class);
        assertThat(bridge.isAvailable()).isTrue();
        assertThat(bridge.backendName()).isEqualTo("Logback");
    }

    @Test
    @DisplayName("detect() returns a fresh bridge each time rather than shared state")
    void detectIsStateless() {
        assertThat(LogLevelBridge.detect()).isNotSameAs(LogLevelBridge.detect());
    }

    @Test
    @DisplayName("a bridge registered as a service takes precedence over the built-ins")
    void serviceLoaderBridgeWins(@TempDir Path tempDir) throws Exception {
        // This is the path an application takes to support a backend the library
        // has never heard of, so it is worth proving rather than assuming.
        withRegisteredService(tempDir, () ->
                assertThat(LogLevelBridge.detect()).isInstanceOf(FakeServiceBridge.class));
    }

    @Test
    @DisplayName("an unavailable service bridge is skipped in favour of the bound backend")
    void unavailableServiceBridgeIsSkipped(@TempDir Path tempDir) throws Exception {
        FakeServiceBridge.available = false;

        withRegisteredService(tempDir, () ->
                assertThat(LogLevelBridge.detect()).isInstanceOf(LogbackLogLevelBridge.class));
    }

    /** Runs an action with {@link FakeServiceBridge} registered as a service. */
    private static void withRegisteredService(Path tempDir, Runnable action) throws Exception {
        Path servicesFile = tempDir.resolve("bridge-service");
        Files.writeString(servicesFile, FakeServiceBridge.class.getName() + "\n");

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(
                    new ServiceProvidingClassLoader(original, servicesFile.toUri().toURL()));
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    /**
     * Serves a synthetic {@code META-INF/services} entry for
     * {@link LogLevelBridge}, so the test does not need a real service file on
     * the test classpath - which would alter detection for every other test.
     */
    private static final class ServiceProvidingClassLoader extends ClassLoader {

        private static final String SERVICE_RESOURCE =
                "META-INF/services/" + LogLevelBridge.class.getName();

        private final URL servicesUrl;

        ServiceProvidingClassLoader(ClassLoader parent, URL servicesUrl) {
            super(parent);
            this.servicesUrl = servicesUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVICE_RESOURCE.equals(name)) {
                return Collections.enumeration(List.of(servicesUrl));
            }
            return super.getResources(name);
        }
    }
}
