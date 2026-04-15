package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class BranchCommand implements CommandDefinition {
    @Override
    public String name() {
        return "branch";
    }

    @Override
    public String description() {
        return "Manage git worktree branches";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args == null || args.isBlank()) {
            renderer.printLine("Usage: /branch <create|list|switch> [name]");
            return;
        }
        renderer.printLine("Branch operation: " + args.trim());
    }
}
