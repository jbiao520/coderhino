package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.types.PermissionMode;

public final class PermissionsCommand implements CommandDefinition {
    @Override
    public String name() {
        return "permissions";
    }

    @Override
    public String description() {
        return "Show and manage permission mode and rules";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty() || sub.equals("show") || sub.equals("status")) {
            var mode = state.permissionMode();
            renderer.printLine("Permission mode: " + mode.name().toLowerCase());
            renderer.printLine("");
            renderer.printLine("Mode descriptions:");
            for (var m : PermissionMode.values()) {
                var active = m == mode ? " [ACTIVE]" : "";
                renderer.printLine("  " + m.name().toLowerCase() + active + " - " + describeMode(m));
            }
        } else if (sub.startsWith("set ")) {
            var modeName = sub.substring(4).trim().toUpperCase();
            try {
                var newMode = PermissionMode.valueOf(modeName);
                context.bootstrapState().update(s -> s.withPermissionMode(newMode));
                renderer.printLine("Permission mode set to: " + newMode.name().toLowerCase());
            } catch (IllegalArgumentException e) {
                renderer.printLine("Unknown permission mode: " + modeName.toLowerCase());
                renderer.printLine("Valid modes: default, plan, bypass, auto, dont_ask, accept_edits");
            }
        } else {
            renderer.printLine("Usage: /permissions [show|set <mode>]");
            renderer.printLine("  show          - Show current permission mode (default)");
            renderer.printLine("  set <mode>    - Set permission mode: default|plan|bypass|auto|dont_ask|accept_edits");
        }
    }

    private static String describeMode(PermissionMode mode) {
        return switch (mode) {
            case DEFAULT -> "Normal operation with user prompts for sensitive operations";
            case PLAN -> "Plan mode — Claude proposes actions before executing";
            case BYPASS -> "Bypass all checks — allow everything (use with caution)";
            case AUTO -> "Auto mode — automatically approve safe operations using heuristics";
            case DONT_ASK -> "Never prompt — auto-deny if not clearly safe";
            case ACCEPT_EDITS -> "Auto-accept file edits, prompt for destructive operations";
        };
    }
}
