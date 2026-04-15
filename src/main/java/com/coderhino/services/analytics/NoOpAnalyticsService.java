package com.coderhino.services.analytics;

/**
 * No-op analytics service — deterministic local behavior with no external telemetry.
 * <p>
 * Safe default for offline, testing, and development use.
 */
public final class NoOpAnalyticsService implements AnalyticsService {

    @Override
    public void trackEvent(String eventName, String payload) {
        // no-op — events are silently discarded
    }

    @Override
    public void flush() {
        // no-op — nothing to flush
    }

    @Override
    public void shutdown() {
        // no-op — no resources to release
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
