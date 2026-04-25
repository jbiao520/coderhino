package com.coderhino.query;

import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

final class RetryHandler {
    static final int DEFAULT_MAX_ATTEMPTS = 3;
    static final long BASE_DELAY_MS = 500L;
    static final long MAX_DELAY_MS = 8000L;

    private final int maxAttempts;
    private final long baseDelayMs;
    private int attemptsMade = 0;

    RetryHandler(int maxAttempts, long baseDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
    }

    RetryHandler() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MS);
    }

    boolean canRetry() {
        return attemptsMade < maxAttempts;
    }

    int attemptsMade() {
        return attemptsMade;
    }

    ModelResponse executeWithRetry(Supplier<ModelResponse> action) throws Exception {
        Exception lastException = null;
        while (attemptsMade < maxAttempts) {
            try {
                attemptsMade++;
                return action.get();
            } catch (Exception e) {
                lastException = e;
                if (isRetryable(e) && attemptsMade < maxAttempts) {
                    sleepBeforeRetry(attemptsMade);
                } else {
                    throw e;
                }
            }
        }
        throw lastException != null ? lastException : new IllegalStateException("No attempts made");
    }

    static boolean isRetryable(Throwable e) {
        if (e == null) return false;
        if (e instanceof HttpTimeoutException) return true;
        var msg = e.getMessage();
        if (msg == null) return false;
        var lower = msg.toLowerCase();
        return lower.contains("timeout")
            || lower.contains("timed out")
            || lower.contains("connection reset")
            || lower.contains("connection refused")
            || lower.contains("503")
            || lower.contains("529")
            || lower.contains("overloaded")
            || lower.contains("rate limit")
            || lower.contains("429");
    }

    private void sleepBeforeRetry(int attempt) {
        long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), MAX_DELAY_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
