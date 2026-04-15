package com.coderhino.tools.builtin;

import com.coderhino.services.tasks.TaskRecord;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class TaskGetTool implements ToolDefinition<TaskGetTool.Input, TaskGetTool.Output> {
    @Override
    public String name() {
        return "TaskGet";
    }

    @Override
    public String description() {
        return "Retrieve a local task by ID";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "taskId", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.taskId() == null || input.taskId().isBlank()) {
            return PermissionResult.deny("taskId must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var taskService = context.services().tasks();
        var taskId = input.taskId().trim();
        var task = taskService.get(taskId).orElse(null);
        if (task == null) {
            return new Output(null);
        }
        var output = taskService.getOutputAwait(taskId).orElse(null);
        return new Output(new TaskSnapshot(task, output));
    }

    public record Input(String taskId) {
    }

    public record Output(TaskSnapshot task) {
    }

    public record TaskSnapshot(String id, String description, String status, String output) {
        public TaskSnapshot(TaskRecord record, String output) {
            this(record.id().toString(), record.description(), record.status(), output);
        }
    }
}
