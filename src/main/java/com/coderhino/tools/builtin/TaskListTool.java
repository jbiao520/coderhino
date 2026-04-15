package com.coderhino.tools.builtin;

import com.coderhino.services.tasks.TaskRecord;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;

public final class TaskListTool implements ToolDefinition<TaskListTool.Input, TaskListTool.Output> {
    @Override
    public String name() {
        return "TaskList";
    }

    @Override
    public String description() {
        return "List local task records";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of());
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var tasks = context.services().tasks().list().stream()
            .map(TaskSnapshot::new)
            .toList();
        return new Output(tasks);
    }

    public record Input() {
    }

    public record Output(List<TaskSnapshot> tasks) {
    }

    public record TaskSnapshot(String id, String description, String status, String output) {
        public TaskSnapshot(TaskRecord record) {
            this(record.id().toString(), record.description(), record.status(), record.output());
        }
    }
}
