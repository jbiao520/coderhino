package com.coderhino.commands;

public final class MarkdownCommandDefinition implements PromptBackedCommand {
    private final MarkdownPromptDefinition definition;

    public MarkdownCommandDefinition(MarkdownPromptDefinition definition) {
        this.definition = definition;
    }

    public MarkdownPromptDefinition promptDefinition() {
        return definition;
    }

    @Override
    public String name() {
        return definition.name();
    }

    @Override
    public String description() {
        return definition.description();
    }

    @Override
    public boolean hidden() {
        return !definition.userInvocable();
    }

    @Override
    public boolean includeInModelContext() {
        return definition.modelInvocable();
    }

    @Override
    public boolean userInvocable() {
        return definition.userInvocable();
    }

    @Override
    public void execute(CommandContext context, String args) {
        if (!definition.userInvocable()) {
            context.renderer().printError("Unknown command: /" + definition.name());
            return;
        }
        PromptBackedCommand.super.execute(context, args);
    }

    @Override
    public String prompt(String args) {
        return definition.renderPrompt(args);
    }

    @Override
    public java.util.List<String> allowedTools() {
        return definition.allowedTools();
    }
}
