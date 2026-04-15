package com.coderhino.query;

/**
 * Receives incremental model-stream events before a terminal {@link ModelResponse} is available.
 */
public interface ModelStreamEventSink {

    String RETRY_STATUS_PREFIX = "Retrying LLM request: ";

    void onTextDelta(String text);

    default void onStatus(String message) {
    }

    default void onThinkingDelta(String thinking) {
    }

    default void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
    }

    default void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
    }
}
