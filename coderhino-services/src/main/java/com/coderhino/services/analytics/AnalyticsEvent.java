package com.coderhino.services.analytics;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record representing a single analytics event captured by {@link DefaultAnalyticsService}.
 *
 * @param eventName  the event identifier (e.g. "tool_invoked", "command_executed")
 * @param properties free-form metadata associated with the event
 * @param timestamp  the time the event was captured
 */
public record AnalyticsEvent(
        String eventName,
        Map<String, Object> properties,
        Instant timestamp
) {

    /**
     * Compact canonical constructor — validates required fields.
     */
    public AnalyticsEvent {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName must not be blank");
        }
        if (properties == null) {
            properties = Map.of();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Convenience factory — creates an event with the current timestamp.
     *
     * @param eventName  the event identifier
     * @param properties key-value metadata
     * @return a new AnalyticsEvent
     */
    public static AnalyticsEvent of(String eventName, Map<String, Object> properties) {
        return new AnalyticsEvent(eventName, properties, Instant.now());
    }

    /**
     * Convenience factory — creates an event with no extra properties.
     *
     * @param eventName the event identifier
     * @return a new AnalyticsEvent
     */
    public static AnalyticsEvent of(String eventName) {
        return new AnalyticsEvent(eventName, Map.of(), Instant.now());
    }
}
