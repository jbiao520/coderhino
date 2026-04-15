package com.coderhino.query;

final class NoOpQueryEventSink implements QueryEventSink {

    static final NoOpQueryEventSink INSTANCE = new NoOpQueryEventSink();

    private NoOpQueryEventSink() {}

    @Override public void onTextChunk(String chunk) {}
    @Override public void onStatus(String message) {}
    @Override public void onToolCall(String toolName, String toolUseId, String argumentsJson) {}
    @Override public void onToolResult(String toolName, String toolUseId, String result) {}
    @Override public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {}
    @Override public void onError(String error) {}
    @Override public void onCompleted(String finalText) {}
}
