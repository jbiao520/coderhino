package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class CostCommand implements CommandDefinition {
    @Override
    public String name() {
        return "cost";
    }

    @Override
    public String description() {
        return "Show accumulated token and cost usage";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        context.out().printf("inputTokens=%d outputTokens=%d totalCostUsd=%.6f%n",
            state.totalInputTokens(),
            state.totalOutputTokens(),
            state.totalCostUsd());
    }
}
