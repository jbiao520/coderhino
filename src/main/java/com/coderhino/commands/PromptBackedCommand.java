package com.coderhino.commands;

import java.util.List;

public interface PromptBackedCommand extends CommandDefinition {
    String prompt(String args);

    default List<String> allowedTools() {
        return List.of();
    }

    @Override
    default void execute(CommandContext context, String args) {
        if (context.promptExecutor() == null) {
            context.renderer().printError("Prompt command execution is unavailable for /" + name());
            return;
        }

        try {
            String result = context.promptExecutor().execute(context, this, prompt(args));
            if (result != null && !result.isBlank()) {
                context.out().println(result);
            }
        } catch (Exception exception) {
            context.renderer().printError("Command failed: " + exception.getMessage());
        }
    }
}
