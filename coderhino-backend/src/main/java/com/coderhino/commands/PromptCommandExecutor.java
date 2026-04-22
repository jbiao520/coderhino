package com.coderhino.commands;

@FunctionalInterface
public interface PromptCommandExecutor {
    String execute(CommandContext context, CommandDefinition definition, String prompt) throws Exception;
}
