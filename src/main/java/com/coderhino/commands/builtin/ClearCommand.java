package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.List;

public final class ClearCommand implements CommandDefinition {
    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String description() {
        return "Clear the in-memory conversation";
    }

    @Override
    public List<String> aliases() {
        return List.of("reset", "new");
    }

    @Override
    public void execute(CommandContext context, String args) {
        context.services().costTracker().reset();
        context.bootstrapState().update(state -> state.resetForNewSession());
        context.renderer().printLine("Conversation cleared.");
    }
}
