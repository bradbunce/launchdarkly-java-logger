package dev.bradbunce.ldlogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.sdk.server.integrations.LoggingConfigurationBuilder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LDSdkLoggingTest {

    @Test
    @DisplayName("slf4j() produces a logging configuration")
    void producesALoggingConfiguration() {
        assertThat(LDSdkLogging.slf4j()).isNotNull().isInstanceOf(LoggingConfigurationBuilder.class);
    }

    @Test
    @DisplayName("slf4j() configures the SLF4J adapter, not the SDK's default console logging")
    void configuresTheSlf4jAdapter() throws ReflectiveOperationException {
        LoggingConfigurationBuilder builder = LDSdkLogging.slf4j();

        // logAdapter is protected on the SDK's builder, so read it reflectively.
        Field logAdapter = LoggingConfigurationBuilder.class.getDeclaredField("logAdapter");
        logAdapter.setAccessible(true);
        Object configured = logAdapter.get(builder);

        assertThat(configured)
                .isInstanceOf(LDLogAdapter.class)
                .hasSameClassAs(LDSLF4J.adapter());
    }

    @Test
    @DisplayName("each call returns an independent builder")
    void returnsIndependentBuilders() {
        assertThat(LDSdkLogging.slf4j()).isNotSameAs(LDSdkLogging.slf4j());
    }

    @Test
    @DisplayName("the class cannot be instantiated")
    void cannotBeInstantiated() throws ReflectiveOperationException {
        Constructor<LDSdkLogging> constructor = LDSdkLogging.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
    }
}
