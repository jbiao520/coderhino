package com.coderhino.query;

/**
 * Rich result from {@link QueryEngine#execute}, exposing stop reason,
 * iteration count, accumulated usage, and the final assistant text.
 */
public record QueryResult(
    String text,
    StopReason stopReason,
    int iterationsUsed,
    ModelResponse.Usage usage
) {
    public enum StopReason {
        END_TURN,
        TOOL_LIMIT,
        ERROR
    }

    public boolean isEndTurn() {
        return stopReason == StopReason.END_TURN;
    }

    public boolean isToolLimitReached() {
        return stopReason == StopReason.TOOL_LIMIT;
    }

    public boolean isError() {
        return stopReason == StopReason.ERROR;
    }
}
