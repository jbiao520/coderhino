package com.coderhino.web.events;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Normalized event model for SSE transport.
 * <p>
 * Represents a single event sent over the SSE stream with a typed event name
 * and structured JSON payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionEvent(EventType type, Object payload) {

    /**
     * Event types for session and run lifecycle.
     */
    public enum EventType {
        ready,
        status,
        completed,
        cancelled,
        failed,
        server_shutdown,
        text_chunk,
        thinking_delta,
        tool_input_delta,
        tool_call,
        tool_result,
        ask_user_question,
        usage,
        approval_requested,
        approval_resolved
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Static factory methods for each event type
    // ──────────────────────────────────────────────────────────────────────────

    public static SessionEvent ready(String sessionId, int messageCount) {
        return new SessionEvent(EventType.ready, new ReadyPayload(sessionId, messageCount));
    }

    public static SessionEvent status(String runId, String status) {
        return status(runId, status, null);
    }

    public static SessionEvent status(String runId, String status, Long sequence) {
        return new SessionEvent(EventType.status, new RunPayload(runId, status, null, null, null, sequence, null, null));
    }

    public static SessionEvent completed(String runId, String finalText) {
        return completed(runId, finalText, null, null, null, null);
    }

    public static SessionEvent completed(String runId, String finalText, FileChangeSummaryPayload fileSummary) {
        return completed(runId, finalText, fileSummary, null, null, null);
    }

    public static SessionEvent completed(String runId, String finalText, FileChangeSummaryPayload fileSummary, Long sequence) {
        return completed(runId, finalText, fileSummary, sequence, null, null);
    }

    public static SessionEvent completed(String runId, String finalText, FileChangeSummaryPayload fileSummary, Long sequence,
                                         String projectId, String sessionId) {
        return new SessionEvent(EventType.completed, new RunPayload(runId, "COMPLETED", null, finalText, fileSummary, sequence, projectId, sessionId));
    }

    public static SessionEvent cancelled(String runId) {
        return cancelled(runId, null);
    }

    public static SessionEvent cancelled(String runId, Long sequence) {
        return new SessionEvent(EventType.cancelled, new RunPayload(runId, "CANCELLED", null, null, null, sequence, null, null));
    }

    public static SessionEvent failed(String runId, String error) {
        return failed(runId, error, null);
    }

    public static SessionEvent failed(String runId, String error, Long sequence) {
        return new SessionEvent(EventType.failed, new RunPayload(runId, "FAILED", error, null, null, sequence, null, null));
    }

    public static SessionEvent serverShutdown() {
        return new SessionEvent(EventType.server_shutdown, new ShutdownPayload());
    }

    public static SessionEvent textChunk(String runId, String chunk) {
        return textChunk(runId, chunk, null);
    }

    public static SessionEvent textChunk(String runId, String chunk, Long sequence) {
        return new SessionEvent(EventType.text_chunk, new TextChunkPayload(runId, chunk, sequence));
    }

    public static SessionEvent thinkingDelta(String runId, String thinking) {
        return thinkingDelta(runId, thinking, null);
    }

    public static SessionEvent thinkingDelta(String runId, String thinking, Long sequence) {
        return new SessionEvent(EventType.thinking_delta, new ThinkingDeltaPayload(runId, thinking, sequence));
    }

    public static SessionEvent toolInputDelta(String runId, String toolName, String toolUseId, String partialJson) {
        return toolInputDelta(runId, toolName, toolUseId, partialJson, null);
    }

    public static SessionEvent toolInputDelta(String runId, String toolName, String toolUseId, String partialJson, Long sequence) {
        return new SessionEvent(EventType.tool_input_delta, new ToolInputDeltaPayload(runId, toolName, toolUseId, partialJson, sequence));
    }

    public static SessionEvent toolCall(String runId, String toolName, String toolUseId, String argumentsJson) {
        return toolCall(runId, toolName, toolUseId, argumentsJson, null);
    }

    public static SessionEvent toolCall(String runId, String toolName, String toolUseId, String argumentsJson, Long sequence) {
        return new SessionEvent(EventType.tool_call, new ToolCallPayload(runId, toolName, toolUseId, argumentsJson, sequence));
    }

    public static SessionEvent toolResult(String runId, String toolName, String toolUseId, String result) {
        return toolResult(runId, toolName, toolUseId, result, null);
    }

    public static SessionEvent toolResult(String runId, String toolName, String toolUseId, String result, Long sequence) {
        return new SessionEvent(EventType.tool_result, new ToolResultPayload(runId, toolName, toolUseId, result, sequence));
    }

    public static SessionEvent askUserQuestion(String runId, String toolUseId, String question, java.util.List<String> choices) {
        return askUserQuestion(runId, toolUseId, question, choices, null);
    }

    public static SessionEvent askUserQuestion(String runId, String toolUseId, String question, java.util.List<String> choices, Long sequence) {
        return new SessionEvent(EventType.ask_user_question, new AskUserQuestionPayload(runId, toolUseId, question, choices, sequence));
    }

    public static SessionEvent usage(String runId, long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens, long toolUses, long contextLength) {
        return usage(runId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, toolUses, contextLength, null);
    }

    public static SessionEvent usage(String runId, long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens, long toolUses, long contextLength, Long sequence) {
        return new SessionEvent(EventType.usage, new UsagePayload(runId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, toolUses, contextLength, sequence));
    }

    public static SessionEvent approvalRequested(String sessionId, String approvalId, String action, String summary) {
        return new SessionEvent(EventType.approval_requested,
                new ApprovalPayload(sessionId, approvalId, action, summary, null, null));
    }

    public static SessionEvent approvalResolved(String sessionId, String approvalId, String resolution) {
        return new SessionEvent(EventType.approval_resolved,
                new ApprovalPayload(sessionId, approvalId, null, null, resolution, null));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Payload record types
    // ──────────────────────────────────────────────────────────────────────────

    public record ReadyPayload(String sessionId, int messageCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunPayload(String runId, String status, String error, String finalText, FileChangeSummaryPayload fileSummary,
                             Long sequence, String projectId, String sessionId) {
        public RunPayload(String runId, String status, String error, String finalText) {
            this(runId, status, error, finalText, null, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileChangeSummaryPayload(int totalChanges, java.util.List<String> created, java.util.List<String> modified, java.util.List<String> deleted) {}

    public record ShutdownPayload() {}

    public record TextChunkPayload(String runId, String chunk, Long sequence) {}

    public record ThinkingDeltaPayload(String runId, String thinking, Long sequence) {}

    public record ToolInputDeltaPayload(String runId, String toolName, String toolUseId, String partialJson, Long sequence) {}

    public record ToolCallPayload(String runId, String toolName, String toolUseId, String argumentsJson, Long sequence) {}

    public record ToolResultPayload(String runId, String toolName, String toolUseId, String result, Long sequence) {}

    public record AskUserQuestionPayload(String runId, String toolUseId, String question, java.util.List<String> choices, Long sequence) {}

    public record UsagePayload(String runId, long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens, long toolUses, long contextLength, Long sequence) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApprovalPayload(String sessionId, String approvalId, String action, String summary,
                                  String resolution, String error) {}
}
