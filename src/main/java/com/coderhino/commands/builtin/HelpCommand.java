package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.Comparator;
import java.util.stream.Collectors;

public final class HelpCommand implements CommandDefinition {
    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "List built-in slash commands";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        renderer.printLine("Available commands:");
        context.registry().all().stream()
            .filter(command -> !command.hidden())
            .sorted(Comparator.comparing(CommandDefinition::name))
            .forEach(command -> {
                var aliasSuffix = command.aliases().isEmpty()
                    ? ""
                    : " (aliases: " + command.aliases().stream()
                        .map(alias -> "/" + alias)
                        .collect(Collectors.joining(", ")) + ")";
                renderer.printLine("  /" + command.name() + aliasSuffix + " - " + command.description());
            });
    }
}
