package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class AgentsCommand implements CommandDefinition {
    @Override
    public String name() {
        return "agents";
    }

    @Override
    public String description() {
        return "Show agent and multi-agent coordinator status";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var coordinator = context.services().coordinatorService();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty() || sub.equals("status")) {
            renderer.printLine("Agent Status");
            renderer.printLine("============");
            renderer.printLine("  Coordinator mode:   " + coordinator.currentMode());
            renderer.printLine("  Multi-agent active: " + coordinator.isMultiAgent());
            renderer.printLine("  Session ID:         " + context.bootstrapState().get().sessionRuntime().sessionId());

            var tasks = context.services().tasks().list();
            renderer.printLine("  Active tasks:       " + tasks.size());
            if (!tasks.isEmpty()) {
                for (var task : tasks) {
                    renderer.printLine("    - " + task.id() + " [" + task.status() + "] " + task.description());
                }
            }
        } else if (sub.equals("list")) {
            renderer.printLine("Coordinator: " + coordinator.currentMode());
            renderer.printLine("Multi-agent support: " + (coordinator.isMultiAgent() ? "enabled" : "disabled"));
        } else {
            renderer.printLine("Usage: /agents [status|list]");
            renderer.printLine("  status - Show detailed agent status (default)");
            renderer.printLine("  list   - Show brief agent list");
        }
    }
}
