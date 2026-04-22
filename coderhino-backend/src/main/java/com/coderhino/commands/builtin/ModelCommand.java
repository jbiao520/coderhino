package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class ModelCommand implements CommandDefinition {
    @Override
    public String name() {
        return "model";
    }

    @Override
    public String description() {
        return "Show or update the active model";
    }

    @Override
    public void execute(CommandContext context, String args) {
        if (args == null || args.isBlank()) {
            context.out().printf("Active model: %s%n", context.bootstrapState().get().model());
            return;
        }

        context.bootstrapState().update(state -> state.withModel(args));
        context.out().printf("Active model set to: %s%n", args);
    }
}
