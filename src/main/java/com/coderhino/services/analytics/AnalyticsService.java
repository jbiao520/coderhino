package com.coderhino.services.analytics;

/**
 * Analytics service abstraction for tracking CLI usage events.
 * <p>
 * Skeleton interface — future implementations may send events to
 * external telemetry backends. The default {@link NoOpAnalyticsService}
 * performs no external calls and is safe for local/offline use.
 */
public interface AnalyticsService {

    /**
     * Track an event with a name and optional string payload.
     *
     * @param eventName the event identifier (e.g. "tool_invoked", "command_executed")
     * @param payload   free-form metadata associated with the event
     */
    void trackEvent(String eventName, String payload);

    /**
     * Flush any buffered events to the backend (no-op for local implementations).
     */
    void flush();

    /**
     * Shut down the analytics service and release resources.
     */
    void shutdown();

    /**
     * Returns {@code true} if analytics collection is enabled.
     * No-op implementations always return {@code false}.
     */
    boolean isEnabled();
}
