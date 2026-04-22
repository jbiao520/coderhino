package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class StatusCommand implements CommandDefinition {
    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "Show current session status and health";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();

        renderer.printLine("Session Status");
        renderer.printLine("==============");
        renderer.printLine("  Session ID:       " + state.sessionRuntime().sessionId());
        renderer.printLine("  Running:          " + state.running());
        renderer.printLine("  Interactive:      " + state.interactive());
        renderer.printLine("  Model:            " + state.model());
        renderer.printLine("  Permission mode:  " + state.permissionMode().name().toLowerCase());
        renderer.printLine("  Verbose:          " + state.verbose());
        renderer.printLine("  CWD:              " + state.cwd());
        renderer.printLine("");
        renderer.printLine("Conversation:");
        renderer.printLine("  Messages:         " + state.messages().size());
        renderer.printLine("  Transcript:       " + state.sessionRuntime().transcript().size() + " entries");
        renderer.printLine("  Tool invocations: " + state.totalToolUses());
        renderer.printLine("");
        renderer.printLine("Token Usage:");
        renderer.printLine("  Input tokens:     " + state.totalInputTokens());
        renderer.printLine("  Output tokens:    " + state.totalOutputTokens());
        renderer.printLine("  Cache read:       " + state.totalCacheReadTokens());
        renderer.printLine("  Cache write:      " + state.totalCacheWriteTokens());
        renderer.printLine("  Total cost:       $" + String.format("%.6f", state.totalCostUsd()));
        renderer.printLine("");
        renderer.printLine("Services:");
        renderer.printLine("  MCP servers:      " + context.services().mcp().definitions().size());
        renderer.printLine("  LSP servers:      " + context.services().lsp().definitions().size());
        renderer.printLine("  Server running:   " + context.services().serverService().isRunning());
    }
}
