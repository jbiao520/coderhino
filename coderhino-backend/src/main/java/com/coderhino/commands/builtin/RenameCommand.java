package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class RenameCommand implements CommandDefinition {
    @Override
    public String name() {
        return "rename";
    }

    @Override
    public String description() {
        return "Rename the current session or a symbol across the workspace";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args == null || args.isBlank()) {
            renderer.printLine("Usage: /rename <new-session-title>  OR  /rename symbol <old-name> <new-name>");
            return;
        }

        var trimmed = args.trim();

        if (trimmed.startsWith("symbol ")) {
            var parts = trimmed.substring(7).trim().split("\\s+", 2);
            if (parts.length < 2 || parts[1].isBlank()) {
                renderer.printLine("Usage: /rename symbol <old-name> <new-name>");
                return;
            }
            renderer.printLine("Rename symbol: " + parts[0] + " → " + parts[1]);
            renderer.printLine("(Use your language server or IDE for workspace-wide rename operations.)");
        } else {
            var newTitle = trimmed;
            var state = context.bootstrapState().get();
            context.sessionStore().saveCustomTitle(state, newTitle);
            context.bootstrapState().update(s ->
                s.withSessionRuntime(s.sessionRuntime().withCustomTitle(newTitle)));
            renderer.printLine("Session renamed to: " + newTitle);
            renderer.printLine("  Session ID: " + state.sessionRuntime().sessionId());
        }
    }
}
