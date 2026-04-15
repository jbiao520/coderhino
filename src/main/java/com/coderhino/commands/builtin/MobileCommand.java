package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.List;

public final class MobileCommand implements CommandDefinition {
    private static final String IOS_URL = "https://apps.apple.com/app/claude-by-anthropic/id6473753684";
    private static final String ANDROID_URL = "https://play.google.com/store/apps/details?id=com.anthropic.claude";

    @Override
    public String name() {
        return "mobile";
    }

    @Override
    public String description() {
        return "Show download links and handoff details for the Claude mobile app";
    }

    @Override
    public List<String> aliases() {
        return List.of("ios", "android");
    }

    @Override
    public boolean webCompatible() {
        return false;
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var subcommand = args == null ? "" : args.trim().toLowerCase();

        if (!subcommand.isEmpty() && !subcommand.equals("ios") && !subcommand.equals("android") && !subcommand.equals("all")) {
            renderer.printLine("Usage: /mobile [ios|android|all]");
            return;
        }

        var platform = subcommand.isEmpty() ? "all" : subcommand;

        renderer.printLine("Claude mobile app links");
        if (!platform.equals("android")) {
            renderer.printLine("  iOS:     " + IOS_URL);
        }
        if (!platform.equals("ios")) {
            renderer.printLine("  Android: " + ANDROID_URL);
        }
        renderer.printLine("Tip: use /mobile ios or /mobile android to focus on one platform.");
    }
}
