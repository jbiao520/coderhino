package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class TodoCreateTool implements ToolDefinition<TodoCreateTool.Input, TodoCreateTool.Output> {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    @Override
    public String name() {
        return "todo_create";
    }

    @Override
    public String description() {
        return "Create a todo/task entry with a title and optional description";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "title", Map.of("type", "string"),
            "description", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.title() == null || input.title().isBlank()) {
            return PermissionResult.deny("title must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        long id = COUNTER.incrementAndGet();
        var summary = new StringBuilder();
        summary.append("Todo created").append(System.lineSeparator());
        summary.append("- id: ").append(id).append(System.lineSeparator());
        summary.append("- title: ").append(input.title()).append(System.lineSeparator());
        if (input.description() != null && !input.description().isBlank()) {
            summary.append("- description: ").append(input.description()).append(System.lineSeparator());
        }
        summary.append("- status: pending");
        return new Output(id, input.title(), input.description(), "pending", summary.toString());
    }

    public record Input(String title, String description) {
        public Input {
            if (title != null) title = title.strip();
            if (description != null) description = description.strip();
        }
    }

    public record Output(long id, String title, String description, String status, String summary) {
    }
}
