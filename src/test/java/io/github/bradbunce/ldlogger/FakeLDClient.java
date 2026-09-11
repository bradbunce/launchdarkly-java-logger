package io.github.bradbunce.ldlogger;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.MigrationOpTracker;
import com.launchdarkly.sdk.server.MigrationStage;
import com.launchdarkly.sdk.server.MigrationVariation;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stand-in {@link LDClientInterface} serving canned flag values.
 *
 * <p>Only the members {@link LDLogLevelController} actually uses are
 * implemented; everything else throws, so a change in what the controller
 * depends on shows up as a loud failure rather than a silent default.
 */
final class FakeLDClient implements LDClientInterface {

    /** An {@code intVariation} call as the controller made it. */
    record IntVariationCall(String flagKey, LDContext context, int defaultValue) {}

    /** A {@code stringVariation} call as the controller made it. */
    record StringVariationCall(String flagKey, LDContext context, String defaultValue) {}

    private final FakeFlagTracker tracker = new FakeFlagTracker();
    private final Map<String, Integer> intValues = new HashMap<>();
    private final Map<String, String> stringValues = new HashMap<>();
    private final List<IntVariationCall> intVariationCalls = new ArrayList<>();
    private final List<StringVariationCall> stringVariationCalls = new ArrayList<>();

    /** Sets the value {@code intVariation} returns for a flag key. */
    FakeLDClient withIntFlag(String flagKey, int value) {
        intValues.put(flagKey, value);
        return this;
    }

    /** Sets the value {@code stringVariation} returns for a flag key. */
    FakeLDClient withStringFlag(String flagKey, String value) {
        stringValues.put(flagKey, value);
        return this;
    }

    FakeFlagTracker tracker() {
        return tracker;
    }

    List<IntVariationCall> intVariationCalls() {
        return List.copyOf(intVariationCalls);
    }

    List<StringVariationCall> stringVariationCalls() {
        return List.copyOf(stringVariationCalls);
    }

    @Override
    public int intVariation(String flagKey, LDContext context, int defaultValue) {
        intVariationCalls.add(new IntVariationCall(flagKey, context, defaultValue));
        // An unconfigured flag behaves like one the SDK cannot evaluate: the
        // caller's default comes back.
        return intValues.getOrDefault(flagKey, defaultValue);
    }

    @Override
    public String stringVariation(String flagKey, LDContext context, String defaultValue) {
        stringVariationCalls.add(new StringVariationCall(flagKey, context, defaultValue));
        return stringValues.getOrDefault(flagKey, defaultValue);
    }

    @Override
    public FlagTracker getFlagTracker() {
        return tracker;
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    @Override
    public boolean isInitialized() {
        throw unsupported();
    }

    @Override
    public void track(String eventName, LDContext context) {
        throw unsupported();
    }

    @Override
    public void trackData(String eventName, LDContext context, LDValue data) {
        throw unsupported();
    }

    @Override
    public void trackMetric(String eventName, LDContext context, LDValue data, double metricValue) {
        throw unsupported();
    }

    @Override
    public void trackMigration(MigrationOpTracker tracker) {
        throw unsupported();
    }

    @Override
    public void identify(LDContext context) {
        throw unsupported();
    }

    @Override
    public FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options) {
        throw unsupported();
    }

    @Override
    public boolean boolVariation(String flagKey, LDContext context, boolean defaultValue) {
        throw unsupported();
    }

    @Override
    public double doubleVariation(String flagKey, LDContext context, double defaultValue) {
        throw unsupported();
    }

    @Override
    public LDValue jsonValueVariation(String flagKey, LDContext context, LDValue defaultValue) {
        throw unsupported();
    }

    @Override
    public EvaluationDetail<Boolean> boolVariationDetail(
            String flagKey, LDContext context, boolean defaultValue) {
        throw unsupported();
    }

    @Override
    public EvaluationDetail<Integer> intVariationDetail(
            String flagKey, LDContext context, int defaultValue) {
        throw unsupported();
    }

    @Override
    public EvaluationDetail<Double> doubleVariationDetail(
            String flagKey, LDContext context, double defaultValue) {
        throw unsupported();
    }

    @Override
    public EvaluationDetail<String> stringVariationDetail(
            String flagKey, LDContext context, String defaultValue) {
        throw unsupported();
    }

    @Override
    public EvaluationDetail<LDValue> jsonValueVariationDetail(
            String flagKey, LDContext context, LDValue defaultValue) {
        throw unsupported();
    }

    @Override
    public MigrationVariation migrationVariation(
            String flagKey, LDContext context, MigrationStage defaultStage) {
        throw unsupported();
    }

    @Override
    public boolean isFlagKnown(String flagKey) {
        throw unsupported();
    }

    @Override
    public void flush() {
        throw unsupported();
    }

    @Override
    public boolean isOffline() {
        throw unsupported();
    }

    @Override
    public BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider() {
        throw unsupported();
    }

    @Override
    public DataSourceStatusProvider getDataSourceStatusProvider() {
        throw unsupported();
    }

    @Override
    public DataStoreStatusProvider getDataStoreStatusProvider() {
        throw unsupported();
    }

    @Override
    public LDLogger getLogger() {
        throw unsupported();
    }

    @Override
    public String secureModeHash(LDContext context) {
        throw unsupported();
    }

    @Override
    public String version() {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by LDLogLevelController");
    }
}
