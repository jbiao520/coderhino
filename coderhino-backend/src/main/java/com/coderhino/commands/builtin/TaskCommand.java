package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class TaskCommand implements CommandDefinition {
    @Override
    public String name() {
        return "task";
    }

    @Override
    public String description() {
        return "Create, list, inspect, or stop local tasks";
    }

    @Override
    public void execute(CommandContext context, String args) {
        if (args == null || args.isBlank() || args.equals("list")) {
            var tasks = context.services().tasks().list();
            if (tasks.isEmpty()) {
                context.out().println("No tasks.");
                return;
            }
            tasks.forEach(task -> context.out().printf("%s %s %s%n", task.id(), task.status(), task.description()));
            return;
        }

        if (args.startsWith("create ")) {
            var description = args.substring("create ".length()).trim();
            if (description.isBlank()) {
                context.err().println("Usage: /task create <description>");
                return;
            }
            var task = context.services().tasks().create(description);
            context.out().printf("Task created: %s%n", task.id());
            return;
        }

        if (args.startsWith("show ")) {
            var id = args.substring("show ".length()).trim();
            var task = context.services().tasks().get(id);
            if (task.isEmpty()) {
                context.err().printf("Unknown task: %s%n", id);
                return;
            }
            context.out().printf("id=%s%nstatus=%s%ndescription=%s%ncreatedAt=%s%nupdatedAt=%s%n",
                task.get().id(),
                task.get().status(),
                task.get().description(),
                task.get().createdAt(),
                task.get().updatedAt());
            return;
        }

        if (args.startsWith("stop ")) {
            var id = args.substring("stop ".length()).trim();
            var task = context.services().tasks().stop(id);
            if (task.isEmpty()) {
                context.err().printf("Unknown task: %s%n", id);
                return;
            }
            context.out().printf("Task stopped: %s%n", task.get().id());
            return;
        }

        if (args.startsWith("update ")) {
            var remainder = args.substring("update ".length()).trim();
            var parts = remainder.split("\\s+", 2);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                context.err().println("Usage: /task update <id> <status>");
                return;
            }
            var task = context.services().tasks().update(parts[0], parts[1]);
            if (task.isEmpty()) {
                context.err().printf("Unknown task: %s%n", parts[0]);
                return;
            }
            context.out().printf("Task updated: %s status=%s%n", task.get().id(), task.get().status());
            return;
        }

        if (args.startsWith("delete ")) {
            var id = args.substring("delete ".length()).trim();
            var task = context.services().tasks().delete(id);
            if (task.isEmpty()) {
                context.err().printf("Unknown task: %s%n", id);
                return;
            }
            context.out().printf("Task deleted: %s%n", task.get().id());
            return;
        }

        context.err().println("Usage: /task [list|create <description>|show <id>|stop <id>|update <id> <status>|delete <id>]");
    }
}
