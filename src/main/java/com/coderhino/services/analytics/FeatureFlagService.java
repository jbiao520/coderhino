package com.coderhino.services.analytics;

import java.util.Map;
import java.util.Set;

/**
 * Feature-flag service abstraction for evaluating feature gates.
 * <p>
 * Skeleton interface — mirrors the TypeScript GrowthBook integration pattern.
 * The default {@link NoOpFeatureFlagService} returns deterministic defaults
 * suitable for local/offline use.
 */
public interface FeatureFlagService {

    /**
     * Check whether a feature flag is enabled.
     *
     * @param flagName the feature flag identifier
     * @return {@code true} if the flag is enabled, {@code false} otherwise
     */
    boolean isEnabled(String flagName);

    /**
     * Check whether a feature flag is enabled, returning the provided default
     * if the flag is not configured.
     *
     * @param flagName      the feature flag identifier
     * @param defaultValue  value to return when the flag is absent
     * @return the resolved flag value
     */
    boolean isEnabled(String flagName, boolean defaultValue);

    /**
     * Get a string-valued feature flag, returning the provided default
     * if the flag is not configured.
     *
     * @param flagName     the feature flag identifier
     * @param defaultValue value to return when the flag is absent
     * @return the resolved flag value
     */
    String getString(String flagName, String defaultValue);

    /**
     * Return the names of all known feature flags.
     *
     * @return an unmodifiable set of flag names
     */
    Set<String> flagNames();

    /**
     * Return a snapshot of all flag values as an unmodifiable map.
     * Boolean flags are represented as {@code Boolean} values,
     * string flags as {@code String} values.
     *
     * @return unmodifiable map of flag name → value
     */
    Map<String, Object> snapshot();
}
