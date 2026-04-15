package com.coderhino.state;

import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;

import java.util.ArrayList;
import java.util.List;

public record AppState(
    boolean verbose,
    String model,
    String cwd,
    boolean interactive,
    boolean running,
    PermissionMode permissionMode,
    double totalCostUsd,
    long totalInputTokens,
    long totalOutputTokens,
    long totalCacheReadTokens,
    long totalCacheWriteTokens,
    int totalToolUses,
    CurrentUsage currentUsage,
    SessionRuntime sessionRuntime,
    List<Message> messages
) {
    public record CurrentUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        int toolUses
    ) {
        public long contextLength() {
            return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
        }
    }

    public AppState {
        messages = List.copyOf(messages);
    }

    public AppState(
        boolean verbose,
        String model,
        String cwd,
        boolean interactive,
        boolean running,
        PermissionMode permissionMode,
        double totalCostUsd,
        SessionRuntime sessionRuntime,
        List<Message> messages
    ) {
        this(verbose, model, cwd, interactive, running, permissionMode, totalCostUsd, 0L, 0L, 0L, 0L, 0, null, sessionRuntime, messages);
    }

    public AppState(
        boolean verbose,
        String model,
        String cwd,
        boolean interactive,
        boolean running,
        PermissionMode permissionMode,
        double totalCostUsd,
        long totalInputTokens,
        long totalOutputTokens,
        long totalCacheReadTokens,
        long totalCacheWriteTokens,
        int totalToolUses,
        SessionRuntime sessionRuntime,
        List<Message> messages
    ) {
        this(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd,
            totalInputTokens,
            totalOutputTokens,
            totalCacheReadTokens,
            totalCacheWriteTokens,
            totalToolUses,
            null,
            sessionRuntime,
            messages
        );
    }

    public AppState addMessage(Message message) {
        var updatedMessages = new ArrayList<>(messages);
        updatedMessages.add(message);
        return new AppState(verbose, model, cwd, interactive, running, permissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, sessionRuntime, updatedMessages);
    }

    public AppState clearMessages() {
        return new AppState(verbose, model, cwd, interactive, running, permissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, sessionRuntime, List.of());
    }

    public AppState resetForNewSession() {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            0.0,
            0L,
            0L,
            0L,
            0L,
            0,
            null,
            SessionRuntime.create(),
            List.of()
        );
    }

    public AppState withMessages(List<Message> nextMessages) {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd,
            totalInputTokens,
            totalOutputTokens,
            totalCacheReadTokens,
            totalCacheWriteTokens,
            totalToolUses,
            currentUsage,
            sessionRuntime,
            nextMessages
        );
    }

    public AppState stop() {
        return new AppState(verbose, model, cwd, interactive, false, permissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, sessionRuntime, messages);
    }

    public AppState withModel(String nextModel) {
        return new AppState(verbose, nextModel, cwd, interactive, running, permissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, sessionRuntime, messages);
    }

    public AppState withPermissionMode(PermissionMode nextPermissionMode) {
        return new AppState(verbose, model, cwd, interactive, running, nextPermissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, sessionRuntime, messages);
    }

    public AppState withSessionRuntime(SessionRuntime nextSessionRuntime) {
        return new AppState(verbose, model, cwd, interactive, running, permissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, nextSessionRuntime, messages);
    }

    public AppState withCwd(String nextCwd) {
        return new AppState(verbose, model, nextCwd, interactive, running, permissionMode, totalCostUsd, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalToolUses, currentUsage, sessionRuntime, messages);
    }

    public AppState withUsageTotals(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens, int toolUses) {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd,
            inputTokens,
            outputTokens,
            cacheReadTokens,
            cacheWriteTokens,
            toolUses,
            currentUsage,
            sessionRuntime,
            messages
        );
    }

    public AppState withCurrentUsage(CurrentUsage nextCurrentUsage) {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd,
            totalInputTokens,
            totalOutputTokens,
            totalCacheReadTokens,
            totalCacheWriteTokens,
            totalToolUses,
            nextCurrentUsage,
            sessionRuntime,
            messages
        );
    }

    public AppState withTotalCostUsd(double nextTotalCostUsd) {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            nextTotalCostUsd,
            totalInputTokens,
            totalOutputTokens,
            totalCacheReadTokens,
            totalCacheWriteTokens,
            totalToolUses,
            currentUsage,
            sessionRuntime,
            messages
        );
    }

    public AppState addUsage(long inputTokens, long outputTokens) {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd,
            totalInputTokens + inputTokens,
            totalOutputTokens + outputTokens,
            totalCacheReadTokens,
            totalCacheWriteTokens,
            totalToolUses,
            currentUsage,
            sessionRuntime,
            messages
        );
    }

    public AppState addUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens, double costDelta) {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd + costDelta,
            totalInputTokens + inputTokens,
            totalOutputTokens + outputTokens,
            totalCacheReadTokens + cacheReadTokens,
            totalCacheWriteTokens + cacheWriteTokens,
            totalToolUses,
            currentUsage,
            sessionRuntime,
            messages
        );
    }

    public AppState incrementToolUses() {
        return new AppState(
            verbose,
            model,
            cwd,
            interactive,
            running,
            permissionMode,
            totalCostUsd,
            totalInputTokens,
            totalOutputTokens,
            totalCacheReadTokens,
            totalCacheWriteTokens,
            totalToolUses + 1,
            currentUsage,
            sessionRuntime,
            messages
        );
    }
}
