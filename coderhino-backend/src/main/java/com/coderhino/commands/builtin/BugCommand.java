package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class BugCommand implements CommandDefinition {
    @Override
    public String name() {
        return "bug";
    }

    @Override
    public String description() {
        return "Collect system information for a bug report";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();

        renderer.printLine("Bug Report — System Information");
        renderer.printLine("===============================");
        renderer.printLine("");
        renderer.printLine("Version:");
        renderer.printLine("  coderhino: 1.0.0-SNAPSHOT");
        renderer.printLine("");
        renderer.printLine("Runtime:");
        renderer.printLine("  Java version:  " + System.getProperty("java.version"));
        renderer.printLine("  Java vendor:   " + System.getProperty("java.vendor"));
        renderer.printLine("  OS name:       " + System.getProperty("os.name"));
        renderer.printLine("  OS version:    " + System.getProperty("os.version"));
        renderer.printLine("  OS arch:       " + System.getProperty("os.arch"));
        renderer.printLine("  CWD:           " + state.cwd());
        renderer.printLine("");
        renderer.printLine("Session:");
        renderer.printLine("  Session ID:    " + state.sessionRuntime().sessionId());
        renderer.printLine("  Model:         " + state.model());
        renderer.printLine("  Messages:      " + state.messages().size());
        renderer.printLine("  Tool uses:     " + state.totalToolUses());
        renderer.printLine("  Total cost:    $" + String.format("%.6f", state.totalCostUsd()));
        renderer.printLine("");
        renderer.printLine("Services:");
        renderer.printLine("  MCP servers:   " + context.services().mcp().definitions().size());
        renderer.printLine("  LSP servers:   " + context.services().lsp().definitions().size());

        if (args != null && !args.isBlank()) {
            renderer.printLine("");
            renderer.printLine("User note: " + args);
        }

        renderer.printLine("");
        renderer.printLine("Please include the above information when reporting a bug.");
    }
}
