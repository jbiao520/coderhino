package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class InsightsCommand implements CommandDefinition {
    @Override
    public String name() {
        return "insights";
    }

    @Override
    public String description() {
        return "Show session stats: token usage, costs, tool calls, and conversation metrics";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();

        renderer.printLine("Session Insights");
        renderer.printLine("================");
        renderer.printLine("Session ID:       " + state.sessionRuntime().sessionId());
        if (state.sessionRuntime().customTitle() != null) {
            renderer.printLine("Session title:    " + state.sessionRuntime().customTitle());
        }
        renderer.printLine("");
        renderer.printLine("Token Usage:");
        renderer.printLine("  Input tokens:        " + state.totalInputTokens());
        renderer.printLine("  Output tokens:       " + state.totalOutputTokens());
        renderer.printLine("  Cache read tokens:   " + state.totalCacheReadTokens());
        renderer.printLine("  Cache write tokens:  " + state.totalCacheWriteTokens());
        renderer.printLine("  Total cost (USD):    " + String.format("%.6f", state.totalCostUsd()));
        renderer.printLine("");
        renderer.printLine("Activity:");
        renderer.printLine("  Tool invocations:    " + state.totalToolUses());
        renderer.printLine("  Messages:            " + state.messages().size());
        renderer.printLine("  Transcript length:   " + state.sessionRuntime().transcript().size());
        renderer.printLine("");
        renderer.printLine("Configuration:");
        renderer.printLine("  Model:              " + state.model());
        renderer.printLine("  Permission mode:    " + state.permissionMode().name().toLowerCase());
        renderer.printLine("  Interactive:        " + state.interactive());
        renderer.printLine("  Verbose:            " + state.verbose());
    }
}
