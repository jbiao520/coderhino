package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.List;

public final class TasksCommand implements CommandDefinition {
    @Override
    public String name() {
        return "tasks";
    }

    @Override
    public String description() {
        return "List and inspect background tasks";
    }

    @Override
    public List<String> aliases() {
        return List.of("bashes");
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var subcommand = args == null ? "" : args.trim();
        var taskService = context.services().tasks();

        if (subcommand.isEmpty() || subcommand.equals("list")) {
            var tasks = taskService.list();
            if (tasks.isEmpty()) {
                renderer.printLine("No background tasks.");
                return;
            }

            renderer.printLine("Background tasks (" + tasks.size() + "):");
            tasks.forEach(task -> renderer.printLine("  " + task.id() + "  " + task.status() + "  " + task.description()));
            return;
        }

        if (subcommand.startsWith("show ")) {
            var id = subcommand.substring("show ".length()).trim();
            var task = taskService.get(id);
            if (task.isEmpty()) {
                renderer.printLine("Unknown task: " + id);
                return;
            }

            var progress = taskService.getProgressMessages(id);
            renderer.printLine("Task:        " + task.get().id());
            renderer.printLine("Status:      " + task.get().status());
            renderer.printLine("Description: " + task.get().description());
            renderer.printLine("Created:     " + task.get().createdAt());
            renderer.printLine("Updated:     " + task.get().updatedAt());
            renderer.printLine("Output:      " + (task.get().output() == null || task.get().output().isBlank() ? "(none)" : "captured"));
            renderer.printLine("Progress:    " + progress.size() + " message(s)");
            return;
        }

        if (subcommand.startsWith("output ")) {
            var id = subcommand.substring("output ".length()).trim();
            var task = taskService.get(id);
            if (task.isEmpty()) {
                renderer.printLine("Unknown task: " + id);
                return;
            }

            var output = taskService.getOutputAwait(id).orElse("");
            if (output.isBlank()) {
                renderer.printLine("No output captured for task: " + id);
            } else {
                renderer.printLine("Output for task " + id + ":");
                renderer.renderLongOutput(output, 40);
            }
            return;
        }

        if (subcommand.startsWith("cancel ")) {
            var id = subcommand.substring("cancel ".length()).trim();
            var task = taskService.get(id);
            if (task.isEmpty()) {
                renderer.printLine("Unknown task: " + id);
                return;
            }

            if (taskService.cancel(id)) {
                renderer.printLine("Cancellation requested: " + id);
            } else {
                renderer.printLine("Task is not running: " + id + " (status=" + task.get().status() + ")");
            }
            return;
        }

        renderer.printLine("Usage: /tasks [list|show <id>|output <id>|cancel <id>]");
    }
}
