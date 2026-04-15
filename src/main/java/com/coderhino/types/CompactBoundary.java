package com.coderhino.types;

/**
 * Represents boundaries/limits for conversation compaction.
 * Used to determine when and how to compact messages.
 */
public record CompactBoundary(
    long maxTokens,
    long targetTokens,
    int maxMessages
) {
    public CompactBoundary {
        if (maxTokens < 0) maxTokens = 0;
        if (targetTokens < 0) targetTokens = 0;
        if (maxMessages < 0) maxMessages = 0;
    }

    /**
     * Creates a boundary with conservative defaults.
     */
    public static CompactBoundary conservative() {
        return new CompactBoundary(50_000, 40_000, 50);
    }

    /**
     * Creates a boundary with aggressive compaction settings.
     */
    public static CompactBoundary aggressive() {
        return new CompactBoundary(30_000, 20_000, 30);
    }

    /**
     * Creates a boundary with moderate settings.
     */
    public static CompactBoundary moderate() {
        return new CompactBoundary(40_000, 30_000, 40);
    }

    /**
     * Returns true if the given token count exceeds the maximum.
     */
    public boolean exceedsMax(long tokenCount) {
        return tokenCount > maxTokens;
    }

    /**
     * Returns true if the given token count meets or exceeds the target.
     */
    public boolean meetsTarget(long tokenCount) {
        return tokenCount >= targetTokens;
    }
}