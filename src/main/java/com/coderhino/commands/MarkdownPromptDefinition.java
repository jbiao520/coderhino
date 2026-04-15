package com.coderhino.commands;

import java.nio.file.Path;
import java.util.List;

public record MarkdownPromptDefinition(
    String name,
    String displayName,
    String description,
    String body,
    List<String> allowedTools,
    String whenToUse,
    boolean userInvocable,
    boolean disableModelInvocation,
    DefinitionType definitionType,
    Scope scope,
    Namespace namespace,
    Path sourcePath,
    Path baseDirectory
) {
    public MarkdownPromptDefinition {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public boolean modelInvocable() {
        return !disableModelInvocation;
    }

    public String renderPrompt(String args) {
        String content = body == null ? "" : body;
        if (definitionType == DefinitionType.SKILL && baseDirectory != null) {
            content = "Base directory for this skill: " + baseDirectory + System.lineSeparator()
                + System.lineSeparator() + content;
        }
        return substituteArguments(content, args);
    }

    private static String substituteArguments(String content, String args) {
        if (args == null) {
            return content;
        }

        String rendered = content;
        String[] parsedArgs = parseArguments(args);
        String original = rendered;

        for (int i = 0; i < parsedArgs.length; i++) {
            rendered = rendered.replace("$ARGUMENTS[" + i + "]", parsedArgs[i]);
            rendered = rendered.replace("$" + i, parsedArgs[i]);
        }
        rendered = rendered.replace("$ARGUMENTS", args);

        if (rendered.equals(original) && !args.isBlank()) {
            rendered = rendered + System.lineSeparator() + System.lineSeparator() + "ARGUMENTS: " + args;
        }
        return rendered;
    }

    private static String[] parseArguments(String args) {
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    public enum DefinitionType {
        COMMAND,
        SKILL
    }

    public enum Scope {
        PROJECT,
        USER
    }

    public enum Namespace {
        OPENCODE,
        CLAUDE
    }
}
