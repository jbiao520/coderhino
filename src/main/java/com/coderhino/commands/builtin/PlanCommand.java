package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.types.PermissionMode;

public final class PlanCommand implements CommandDefinition {
    @Override
    public String name() {
        return "plan";
    }

    @Override
    public String description() {
        return "Enter or exit plan mode; show current plan state";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var sub = args == null ? "" : args.trim();

        switch (sub) {
            case "on", "enter" -> {
                context.bootstrapState().update(s -> s.withPermissionMode(PermissionMode.PLAN));
                renderer.printLine("Plan mode enabled. Claude will propose actions before executing them.");
            }
            case "off", "exit" -> {
                context.bootstrapState().update(s -> s.withPermissionMode(PermissionMode.DEFAULT));
                renderer.printLine("Plan mode disabled. Returned to DEFAULT permission mode.");
            }
            case "status", "" -> {
                var mode = state.permissionMode();
                var inPlan = mode == PermissionMode.PLAN;
                renderer.printLine("Plan mode: " + (inPlan ? "ACTIVE" : "inactive"));
                renderer.printLine("  Permission mode: " + mode.name().toLowerCase());
                renderer.printLine("  Messages in session: " + state.messages().size());
                renderer.printLine("  Tool uses: " + state.totalToolUses());
                if (inPlan) {
                    renderer.printLine("  (Claude will confirm actions before executing in plan mode)");
                }
            }
            default -> {
                renderer.printLine("Usage: /plan [on|off|status]");
                renderer.printLine("  on     - Enter plan mode (restricts auto-execution)");
                renderer.printLine("  off    - Exit plan mode");
                renderer.printLine("  status - Show current plan mode status (default)");
            }
        }
    }
}
