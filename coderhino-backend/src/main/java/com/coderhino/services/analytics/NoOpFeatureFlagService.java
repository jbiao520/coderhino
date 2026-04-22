package com.coderhino.services.analytics;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * No-op feature-flag service — all flags return their default values.
 * <p>
 * Safe default for offline, testing, and development use.
 * No external GrowthBook or network calls.
 */
public final class NoOpFeatureFlagService implements FeatureFlagService {

    private static final Set<String> EMPTY_FLAGS = Collections.emptySet();
    private static final Map<String, Object> EMPTY_SNAPSHOT = Collections.emptyMap();

    @Override
    public boolean isEnabled(String flagName) {
        return false;
    }

    @Override
    public boolean isEnabled(String flagName, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public String getString(String flagName, String defaultValue) {
        return defaultValue;
    }

    @Override
    public Set<String> flagNames() {
        return EMPTY_FLAGS;
    }

    @Override
    public Map<String, Object> snapshot() {
        return EMPTY_SNAPSHOT;
    }
}
