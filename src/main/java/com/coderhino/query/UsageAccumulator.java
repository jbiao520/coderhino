package com.coderhino.query;

import com.coderhino.services.CostTracker;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;

final class UsageAccumulator {
    private final CostTracker costTracker;
    private long accumulatedInput = 0L;
    private long accumulatedOutput = 0L;
    private long accumulatedCacheCreation = 0L;
    private long accumulatedCacheRead = 0L;
    private int accumulatedToolUses = 0;
    private long appliedInput = 0L;
    private long appliedOutput = 0L;
    private long appliedCacheCreation = 0L;
    private long appliedCacheRead = 0L;
    private int appliedToolUses = 0;

    UsageAccumulator() {
        this(null);
    }

    UsageAccumulator(CostTracker costTracker) {
        this.costTracker = costTracker;
    }

    void add(ModelResponse.Usage usage) {
        accumulatedInput += usage.inputTokens();
        accumulatedOutput += usage.outputTokens();
        accumulatedCacheCreation += usage.cacheCreationTokens();
        accumulatedCacheRead += usage.cacheReadTokens();
    }

    void add(ModelResponse response) {
        if (response instanceof ModelResponse.AssistantReply assistantReply) {
            add(assistantReply.usage());
        } else if (response instanceof ModelResponse.ToolRequest toolRequest) {
            add(toolRequest.usage());
        }
    }

    void recordToolUse() {
        accumulatedToolUses += 1;
    }

    void setCurrentUsage(BootstrapState bootstrapState) {
        bootstrapState.update(state -> state.withCurrentUsage(new AppState.CurrentUsage(
            accumulatedInput,
            accumulatedOutput,
            accumulatedCacheRead,
            accumulatedCacheCreation,
            accumulatedToolUses
        )));
    }

    void applyToState(BootstrapState bootstrapState) {
        long deltaInput = accumulatedInput - appliedInput;
        long deltaOutput = accumulatedOutput - appliedOutput;
        long deltaCacheCreation = accumulatedCacheCreation - appliedCacheCreation;
        long deltaCacheRead = accumulatedCacheRead - appliedCacheRead;
        int deltaToolUses = accumulatedToolUses - appliedToolUses;
        if (deltaInput == 0 && deltaOutput == 0 && deltaCacheCreation == 0 && deltaCacheRead == 0 && deltaToolUses == 0) {
            return;
        }
        final double costDelta;
        var model = bootstrapState.get().model();
        if (costTracker != null) {
            costDelta = costTracker.addUsage(model, deltaInput, deltaOutput, deltaCacheRead, deltaCacheCreation);
        } else {
            costDelta = 0;
        }
        bootstrapState.update(state -> {
            var next = state.addUsage(deltaInput, deltaOutput, deltaCacheRead, deltaCacheCreation, costDelta);
            if (deltaToolUses > 0) {
                for (int i = 0; i < deltaToolUses; i++) {
                    next = next.incrementToolUses();
                }
            }
            return next;
        });
        appliedInput = accumulatedInput;
        appliedOutput = accumulatedOutput;
        appliedCacheCreation = accumulatedCacheCreation;
        appliedCacheRead = accumulatedCacheRead;
        appliedToolUses = accumulatedToolUses;
    }

    ModelResponse.Usage total() {
        return new ModelResponse.Usage(accumulatedInput, accumulatedOutput, accumulatedCacheCreation, accumulatedCacheRead);
    }

    long inputTokens() {
        return accumulatedInput;
    }

    long outputTokens() {
        return accumulatedOutput;
    }

    int toolUses() {
        return accumulatedToolUses;
    }
}
