package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class UsageCommand implements CommandDefinition {
    @Override
    public String name() {
        return "usage";
    }

    @Override
    public String description() {
        return "Show detailed token usage and cost breakdown";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();

        renderer.printLine("Usage Statistics");
        renderer.printLine("================");
        renderer.printLine("");
        renderer.printLine("Token Counts:");
        renderer.printLine("  Input tokens:        " + formatNumber(state.totalInputTokens()));
        renderer.printLine("  Output tokens:       " + formatNumber(state.totalOutputTokens()));
        renderer.printLine("  Cache read tokens:   " + formatNumber(state.totalCacheReadTokens()));
        renderer.printLine("  Cache write tokens:  " + formatNumber(state.totalCacheWriteTokens()));

        var totalTokens = state.totalInputTokens() + state.totalOutputTokens()
            + state.totalCacheReadTokens() + state.totalCacheWriteTokens();
        renderer.printLine("  Total tokens:        " + formatNumber(totalTokens));
        renderer.printLine("");

        renderer.printLine("Cost Breakdown:");
        var inputCost = state.totalInputTokens() * 0.000003;
        var outputCost = state.totalOutputTokens() * 0.000015;
        var cacheReadCost = state.totalCacheReadTokens() * 0.0000003;
        var cacheWriteCost = state.totalCacheWriteTokens() * 0.0000035;
        renderer.printLine(String.format("  Input cost:          $%.6f", inputCost));
        renderer.printLine(String.format("  Output cost:         $%.6f", outputCost));
        renderer.printLine(String.format("  Cache read cost:     $%.6f", cacheReadCost));
        renderer.printLine(String.format("  Cache write cost:    $%.6f", cacheWriteCost));
        renderer.printLine(String.format("  Total cost:          $%.6f", state.totalCostUsd()));
        renderer.printLine("");

        renderer.printLine("Activity:");
        renderer.printLine("  Tool invocations:    " + state.totalToolUses());
        renderer.printLine("  Messages:            " + state.messages().size());
        renderer.printLine("  Transcript entries:  " + state.sessionRuntime().transcript().size());

        // Per-model cost breakdown from CostTracker
        var tracker = context.services().commandCosts();
        var modelUsage = tracker.allModelUsage();
        if (!modelUsage.isEmpty()) {
            renderer.printLine("");
            renderer.printLine("Per-Model Breakdown:");
            for (var entry : modelUsage.entrySet()) {
                var usage = entry.getValue();
                renderer.printLine("  " + entry.getKey() + ": "
                    + formatNumber(usage.inputTokens()) + " in, "
                    + formatNumber(usage.outputTokens()) + " out, "
                    + String.format("$%.6f", usage.costUsd()));
            }
        }
    }

    private static String formatNumber(long n) {
        return String.format("%,d", n);
    }
}
