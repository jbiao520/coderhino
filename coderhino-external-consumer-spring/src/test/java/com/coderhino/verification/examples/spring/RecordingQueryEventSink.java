package com.coderhino.verification.examples.spring;

import com.coderhino.query.QueryEventSink;

import java.util.ArrayList;
import java.util.List;

final class RecordingQueryEventSink implements QueryEventSink {
    private final List<String> textChunks = new ArrayList<>();
    private final List<String> thinkingDeltas = new ArrayList<>();
    private final List<ToolCallRecord> toolCalls = new ArrayList<>();
    private final List<ToolResultRecord> toolResults = new ArrayList<>();
    private final List<ToolInputDeltaRecord> toolInputDeltas = new ArrayList<>();
    private final List<StatusRecord> statuses = new ArrayList<>();
    private final List<UsageRecord> usages = new ArrayList<>();
    private final List<AskUserQuestionRecord> questions = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private String nextAnswer;
    private String completedText;

    void answerNextQuestionWith(String answer) {
        this.nextAnswer = answer;
    }

    List<String> textChunks() {
        return List.copyOf(textChunks);
    }

    List<String> thinkingDeltas() {
        return List.copyOf(thinkingDeltas);
    }

    List<ToolCallRecord> toolCalls() {
        return List.copyOf(toolCalls);
    }

    List<ToolResultRecord> toolResults() {
        return List.copyOf(toolResults);
    }

    List<ToolInputDeltaRecord> toolInputDeltas() {
        return List.copyOf(toolInputDeltas);
    }

    List<StatusRecord> statuses() {
        return List.copyOf(statuses);
    }

    List<UsageRecord> usages() {
        return List.copyOf(usages);
    }

    List<AskUserQuestionRecord> questions() {
        return List.copyOf(questions);
    }

    List<String> errors() {
        return List.copyOf(errors);
    }

    String completedText() {
        return completedText;
    }

    @Override
    public void onTextChunk(String chunk) {
        textChunks.add(chunk);
    }

    @Override
    public void onThinkingDelta(String thinking) {
        thinkingDeltas.add(thinking);
    }

    @Override
    public void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
        toolInputDeltas.add(new ToolInputDeltaRecord(toolName, toolUseId, partialJson));
    }

    @Override
    public String onAskUserQuestion(String toolUseId, String question, List<String> choices) {
        questions.add(new AskUserQuestionRecord(toolUseId, question, choices == null ? List.of() : List.copyOf(choices)));
        return nextAnswer;
    }

    @Override
    public void onStatus(String message) {
        statuses.add(new StatusRecord(message));
    }

    @Override
    public void onToolCall(String toolName, String toolUseId, String argumentsJson) {
        toolCalls.add(new ToolCallRecord(toolName, toolUseId, argumentsJson));
    }

    @Override
    public void onToolResult(String toolName, String toolUseId, String result) {
        toolResults.add(new ToolResultRecord(toolName, toolUseId, result));
    }

    @Override
    public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
        usages.add(new UsageRecord(inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens));
    }

    @Override
    public void onError(String error) {
        errors.add(error);
    }

    @Override
    public void onCompleted(String finalText) {
        completedText = finalText;
    }

    record ToolCallRecord(String toolName, String toolUseId, String argumentsJson) {
    }

    record ToolResultRecord(String toolName, String toolUseId, String result) {
    }

    record ToolInputDeltaRecord(String toolName, String toolUseId, String partialJson) {
    }

    record StatusRecord(String message) {
    }

    record UsageRecord(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
    }

    record AskUserQuestionRecord(String toolUseId, String question, List<String> choices) {
    }
}
