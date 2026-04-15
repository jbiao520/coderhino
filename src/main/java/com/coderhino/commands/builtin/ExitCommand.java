package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class ExitCommand implements CommandDefinition {
    @Override
    public String name() {
        return "exit";
    }

    @Override
    public String description() {
        return "Exit the interactive session";
    }

    @Override
    public void execute(CommandContext context, String args) {
        context.out().println("Exiting Code Rhino Java.");
        context.bootstrapState().stop();
    }
}
