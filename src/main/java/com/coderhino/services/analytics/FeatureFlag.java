package com.coderhino.services.analytics;

/**
 * String constants for all known feature flags.
 * <p>
 * Mirrors the TypeScript {@code bun:bundle} feature flags used for dead-code
 * elimination. In Java, flags are evaluated at runtime via
 * {@link FeatureFlagService#isEnabled(String)}.
 * <p>
 * Usage:
 * <pre>{@code
 * if (serviceRegistry.featureFlags().isEnabled(FeatureFlag.PROACTIVE)) {
 *     // proactive-mode path
 * }
 * }</pre>
 */
public final class FeatureFlag {

    public static final String PROACTIVE = "PROACTIVE";
    public static final String KAIROS = "KAIROS";
    public static final String DAEMON = "DAEMON";
    public static final String VOICE_MODE = "VOICE_MODE";
    public static final String AGENT_TRACERS = "AGENT_TRACERS";
    public static final String COORDINATOR_MODE = "COORDINATOR_MODE";
    public static final String HISTORY_SNIP = "HISTORY_SNIP";

    private FeatureFlag() {
        // non-instantiable
    }
}
