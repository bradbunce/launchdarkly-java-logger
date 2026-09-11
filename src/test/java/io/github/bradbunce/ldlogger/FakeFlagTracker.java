package io.github.bradbunce.ldlogger;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.FlagChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * An in-memory {@link FlagTracker} that lets a test fire flag value changes and
 * assert on what was subscribed.
 *
 * <p>It mirrors the real tracker's contract in the way that matters here:
 * {@link #addFlagValueChangeListener} returns an opaque handle which is the same
 * object that must later be passed to {@link #removeFlagChangeListener}.
 */
final class FakeFlagTracker implements FlagTracker {

    /** A value-change subscription. */
    record Registration(String flagKey, LDContext context, FlagValueChangeListener listener) {}

    private final List<Registration> registrations = new ArrayList<>();
    private final List<FlagChangeListener> handles = new ArrayList<>();
    private int registrationCount;

    /** Every live subscription, in the order it was added. */
    List<Registration> registrations() {
        return List.copyOf(registrations);
    }

    /** Flag keys with a live subscription, in subscription order. */
    List<String> subscribedFlagKeys() {
        return registrations.stream().map(Registration::flagKey).toList();
    }

    /** Total subscriptions ever added, including ones since removed. */
    int registrationCount() {
        return registrationCount;
    }

    /**
     * Delivers a value change to every live listener for a flag key. Listeners
     * for other keys are not notified, just as the real tracker filters by key.
     */
    void fireValueChange(String flagKey, LDValue oldValue, LDValue newValue) {
        FlagValueChangeEvent event = new FlagValueChangeEvent(flagKey, oldValue, newValue);
        for (Registration registration : List.copyOf(registrations)) {
            if (registration.flagKey().equals(flagKey)) {
                registration.listener().onFlagValueChange(event);
            }
        }
    }

    /** Delivers a value change whose previous value is irrelevant to the test. */
    void fireValueChange(String flagKey, LDValue newValue) {
        fireValueChange(flagKey, LDValue.ofNull(), newValue);
    }

    @Override
    public FlagChangeListener addFlagValueChangeListener(
            String flagKey, LDContext context, FlagValueChangeListener listener) {
        registrations.add(new Registration(flagKey, context, listener));
        registrationCount++;
        FlagChangeListener handle = new Handle(flagKey);
        handles.add(handle);
        return handle;
    }

    @Override
    public void addFlagChangeListener(FlagChangeListener listener) {
        throw new UnsupportedOperationException("not used by LDLogLevelController");
    }

    @Override
    public void removeFlagChangeListener(FlagChangeListener listener) {
        int index = handles.indexOf(listener);
        if (index < 0) {
            // The real tracker ignores unknown listeners; surface it here so a
            // mismatched handle in the controller cannot pass silently.
            throw new IllegalArgumentException("removed a listener that was never added");
        }
        handles.remove(index);
        registrations.remove(index);
    }

    /** The handle returned to the caller, deliberately distinct from the listener. */
    private static final class Handle implements FlagChangeListener {
        private final String flagKey;

        Handle(String flagKey) {
            this.flagKey = flagKey;
        }

        @Override
        public void onFlagChange(FlagChangeEvent event) {
            throw new UnsupportedOperationException("handles are only used for removal");
        }

        @Override
        public String toString() {
            return "Handle[" + flagKey + "]";
        }
    }
}
