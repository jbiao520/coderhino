package com.coderhino.query;

/**
 * Abstraction for receiving structured streaming events from the query pipeline.
 * <p>
 * The CLI path uses {@link NoOpQueryEventSink}; the web path uses an SSE implementation
 * that publishes events to the {@code SessionEventBus}.
 */
public interface QueryEventSink {

    /**
     * Called for each streamed text fragment from the assistant reply.
     *
     * @param chunk a fragment of the assistant's response text
     */
    void onTextChunk(String chunk);

    /**
     * Called for streamed model thinking or reasoning text.
     *
     * @param thinking a fragment of intermediate model thinking text
     */
    default void onThinkingDelta(String thinking) {
    }

    /**
     * Called for incremental tool-input JSON emitted before a finalized tool call.
     *
     * @param toolName    the tool currently being assembled
     * @param toolUseId   the tool-use identifier when available
     * @param partialJson the incremental tool-input JSON fragment
     */
    default void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
    }

    /**
     * Called when the query is waiting for the user to answer an interactive question.
     *
     * @param toolUseId the tool-use identifier for the outstanding ask-user-question request
     * @param question the question text
     * @param choices optional predefined choices
     * @return the user's answer to send back as the tool result
     */
    default String onAskUserQuestion(String toolUseId, String question, java.util.List<String> choices) {
        return null;
    }

    /**
     * Called for status updates such as tool start notifications.
     *
     * @param message human-readable status message
     */
    void onStatus(String message);

    /**
     * Called when a tool invocation begins.
     *
     * @param toolName      the name of the tool being called
     * @param toolUseId     the unique identifier for this tool-use invocation
     * @param argumentsJson JSON-serialized tool arguments
     */
    void onToolCall(String toolName, String toolUseId, String argumentsJson);

    /**
     * Called when a tool invocation completes.
     *
     * @param toolName  the name of the tool that completed
     * @param toolUseId the unique identifier for this tool-use invocation
     * @param result    the tool's result as a string
     */
    void onToolResult(String toolName, String toolUseId, String result);

    /**
     * Called after usage information is accumulated.
     *
     * @param inputTokens  total input tokens consumed
     * @param outputTokens total output tokens produced
     */
    void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens);

    /**
     * Called when an error occurs in the query pipeline.
     *
     * @param error error message
     */
    void onError(String error);

    /**
     * Called when the query pipeline completes successfully.
     *
     * @param finalText the final assistant response text
     */
    void onCompleted(String finalText);
}
