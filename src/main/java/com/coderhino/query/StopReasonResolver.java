package com.coderhino.query;

final class StopReasonResolver {
    QueryResult resolveEndTurn(String text, int iterationsUsed, ModelResponse.Usage usage) {
        return new QueryResult(text, QueryResult.StopReason.END_TURN, iterationsUsed, usage);
    }

    QueryResult resolveToolLimit(int maxIterations, ModelResponse.Usage usage) {
        return new QueryResult(
            "Query engine stopped after reaching the tool iteration limit.",
            QueryResult.StopReason.TOOL_LIMIT,
            maxIterations,
            usage
        );
    }

    QueryResult resolveError(String errorMessage, int iterationsUsed, ModelResponse.Usage usage) {
        return new QueryResult(
            "Query engine error: " + errorMessage,
            QueryResult.StopReason.ERROR,
            iterationsUsed,
            usage
        );
    }

    QueryResult resolveBudgetExceeded(int iterationsUsed, ModelResponse.Usage usage) {
        return new QueryResult(
            "Query engine stopped: budget limit exceeded.",
            QueryResult.StopReason.ERROR,
            iterationsUsed,
            usage
        );
    }
}
