package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class EffortCommand implements CommandDefinition {
    private static final List<String> VALID_LEVELS = List.of("low", "medium", "high", "max");
    private static final AtomicReference<String> currentLevel = new AtomicReference<>("medium");

    public static void clearStore() {
        currentLevel.set("medium");
    }

    public static String currentLevel() {
        return currentLevel.get();
    }

    @Override
    public String name() {
        return "effort";
    }

    @Override
    public String description() {
        return "Show or set the reasoning effort level";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();

        if (args == null || args.isBlank()) {
            renderer.printLine("Current effort level: " + currentLevel.get());
            renderer.printLine("Available levels: " + String.join(", ", VALID_LEVELS));
            renderer.printLine("Usage: /effort <level>");
            return;
        }

        var level = args.trim().toLowerCase();
        if (!VALID_LEVELS.contains(level)) {
            renderer.printLine("Invalid effort level: " + level);
            renderer.printLine("Available levels: " + String.join(", ", VALID_LEVELS));
            return;
        }

        var previous = currentLevel.getAndSet(level);
        renderer.printLine("Effort level changed: " + previous + " -> " + level);
        renderer.printLine("  This affects the model's reasoning depth for subsequent queries.");
    }
}
