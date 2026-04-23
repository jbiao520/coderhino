package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.concurrent.atomic.AtomicBoolean;

public final class VerboseCommand implements CommandDefinition {
    private static final AtomicBoolean verboseOverride = new AtomicBoolean(false);

    public static void clearStore() {
        verboseOverride.set(false);
    }

    public static boolean isVerbose() {
        return verboseOverride.get();
    }

    @Override
    public String name() {
        return "verbose";
    }

    @Override
    public String description() {
        return "Toggle verbose output mode";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty()) {
            var current = context.bootstrapState().get().verbose() || verboseOverride.get();
            renderer.printLine("Verbose mode: " + (current ? "ON" : "OFF"));
            renderer.printLine("  State verbose: " + context.bootstrapState().get().verbose());
            renderer.printLine("  Override:      " + verboseOverride.get());
            renderer.printLine("Usage: /verbose [on|off|toggle]");
        } else if (sub.equals("on")) {
            verboseOverride.set(true);
            renderer.printLine("Verbose mode enabled.");
            renderer.printLine("  Detailed logging and tool output will be shown.");
        } else if (sub.equals("off")) {
            verboseOverride.set(false);
            renderer.printLine("Verbose mode disabled.");
        } else if (sub.equals("toggle")) {
            var previous = verboseOverride.get();
            verboseOverride.set(!previous);
            renderer.printLine("Verbose mode: " + (!previous ? "ON" : "OFF"));
        } else {
            renderer.printLine("Usage: /verbose [on|off|toggle]");
        }
    }
}
