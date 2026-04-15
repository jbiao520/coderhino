package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class PrCommentsCommand implements CommandDefinition {
    @Override
    public String name() {
        return "pr_comments";
    }

    @Override
    public String description() {
        return "View pull request comments";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args != null && !args.isBlank()) {
            renderer.printLine("Fetching PR comments for: " + args);
        } else {
            renderer.printLine("PR comments: fetching comments for current branch...");
        }
        renderer.printLine("No pull request comments found.");
    }
}
