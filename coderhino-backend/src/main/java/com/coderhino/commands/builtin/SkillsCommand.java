package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.commands.MarkdownCommandDefinition;

import java.util.Comparator;

public final class SkillsCommand implements CommandDefinition {
    @Override
    public String name() {
        return "skills";
    }

    @Override
    public String description() {
        return "List and inspect available skills";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var sub = args == null ? "" : args.trim();
        var skills = context.registry().all().stream()
            .filter(MarkdownCommandDefinition.class::isInstance)
            .map(MarkdownCommandDefinition.class::cast)
            .filter(skill -> skill.includeInModelContext())
            .sorted(Comparator.comparing(MarkdownCommandDefinition::name))
            .toList();

        if (sub.isEmpty() || sub.equals("list")) {
            if (skills.isEmpty()) {
                renderer.printLine("No skills registered.");
                renderer.printLine("Skills can be added under ~/.claude/skills, ~/.opencode/skills, or project equivalents.");
            } else {
                renderer.printLine("Available skills (" + skills.size() + "):");
                for (var skill : skills) {
                    var definition = skill.promptDefinition();
                    renderer.printLine("  " + definition.name() + " - " + definition.description());
                    if (definition.whenToUse() != null && !definition.whenToUse().isBlank()) {
                        renderer.printLine("    " + definition.whenToUse());
                    }
                }
            }
        } else if (sub.startsWith("show ")) {
            var id = sub.substring(5).trim();
            var found = skills.stream()
                .filter(skill -> skill.name().equals(id))
                .findFirst();
            if (found.isEmpty()) {
                renderer.printLine("Skill not found: " + id);
            } else {
                var skill = found.get().promptDefinition();
                renderer.printLine("Skill: " + skill.name());
                renderer.printLine("  desc: " + skill.description());
                renderer.printLine("  file: " + skill.sourcePath());
                renderer.printLine("  user-invocable: " + skill.userInvocable());
                renderer.printLine("  model-invocable: " + skill.modelInvocable());
            }
        } else {
            renderer.printLine("Usage: /skills [list|show <id>]");
            renderer.printLine("  list       - List all registered skills (default)");
            renderer.printLine("  show <id>  - Show details about a specific skill");
        }
    }
}
