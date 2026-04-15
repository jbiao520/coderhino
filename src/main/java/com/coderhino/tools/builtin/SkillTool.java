package com.coderhino.tools.builtin;

import com.coderhino.commands.CommandDefinition;
import com.coderhino.commands.MarkdownCommandDefinition;
import com.coderhino.commands.PromptBackedCommand;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkillTool implements ToolDefinition<SkillTool.Input, SkillTool.Output> {

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return "Execute a skill or slash command";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "skill", Map.of("type", "string"),
            "args", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null) {
            return PermissionResult.deny("Input must not be null.");
        }

        String skillName = input.skill();
        if (skillName == null || skillName.isBlank()) {
            return PermissionResult.deny("Skill name must not be blank.");
        }

        String normalizedName = normalizeSkillName(skillName);

        if (isForkedSkill(normalizedName)) {
            return PermissionResult.allow();
        }

        Optional<CommandDefinition> command = findCommand(normalizedName, context);

        if (command.isEmpty()) {
            return PermissionResult.deny("Unknown skill: " + normalizedName);
        }

        if (!command.get().includeInModelContext()) {
            return PermissionResult.deny("Unknown skill: " + normalizedName);
        }

        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        String skillName = input.skill();
        String normalizedName = normalizeSkillName(skillName);

        if (isForkedSkill(normalizedName)) {
            String agentId = "agent-" + normalizedName.replaceAll("[^a-zA-Z0-9]", "-");
            return new Output.ForkedResult(
                normalizedName,
                true,
                agentId,
                "Forked skill execution: " + normalizedName + " with args: " + (input.args() != null ? input.args() : "")
            );
        }

        Optional<CommandDefinition> commandOpt = findCommand(normalizedName, context);

        if (commandOpt.isEmpty()) {
            return new Output.InlineResult(normalizedName, false, "Unknown skill: " + normalizedName, List.of());
        }

        CommandDefinition command = commandOpt.get();
        if (!command.includeInModelContext()) {
            return new Output.InlineResult(normalizedName, false, "Unknown skill: " + normalizedName, List.of());
        }

        if (command instanceof PromptBackedCommand promptBackedCommand) {
            return new Output.InlineResult(
                normalizedName,
                true,
                promptBackedCommand.prompt(input.args() != null ? input.args() : ""),
                promptBackedCommand.allowedTools()
            );
        }

        return new Output.InlineResult(
            normalizedName,
            true,
            command.description(),
            List.of()
        );
    }

    private String normalizeSkillName(String skill) {
        if (skill.startsWith("/")) {
            return skill.substring(1);
        }
        return skill;
    }

    private Optional<CommandDefinition> findCommand(String name, ToolContext context) {
        if (context.commandRegistry() == null) {
            return Optional.empty();
        }
        return context.commandRegistry().find(name);
    }

    private boolean isForkedSkill(String skillName) {
        return skillName.startsWith("agent:") || skillName.endsWith(":fork");
    }

    public record Input(String skill, String args) {
    }

    public sealed interface Output permits Output.InlineResult, Output.ForkedResult {
        String commandName();
        boolean success();

        record InlineResult(
            String commandName,
            boolean success,
            String description,
            List<String> allowedTools
        ) implements Output {
        }

        record ForkedResult(
            String commandName,
            boolean success,
            String agentId,
            String result
        ) implements Output {
        }
    }
}
