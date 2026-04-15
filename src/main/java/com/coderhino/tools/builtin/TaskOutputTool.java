package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.Set;

public final class TaskOutputTool implements ToolDefinition<TaskOutputTool.Input, TaskOutputTool.Output> {

    private static final Set<String> DONE_STATUSES = Set.of(
        "completed", "stopped", "failed", "error", "done", "cancelled"
    );

    @Override
    public String name() {
        return "task_output";
    }

    @Override
    public String description() {
        return "Get output from a background task. Use full_session=true to fetch session messages with filters.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "task_id", Map.of("type", "string"),
            "block", Map.of("type", "boolean"),
            "timeout", Map.of("type", "integer")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.task_id() == null || input.task_id().isBlank()) {
            return PermissionResult.deny("task_id must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        var taskService = context.services().tasks();
        var taskId = input.task_id().trim();
        boolean block = input.block();
        int timeout = input.timeout();

        var taskOpt = taskService.get(taskId);
        if (taskOpt.isEmpty()) {
            return new Output("not_ready", null);
        }

        var task = taskOpt.get();

        if (!block) {
            var taskOutput = new TaskOutput(
                task.id().toString(),
                task.status(),
                task.description(),
                task.output()
            );
            if (isDone(task.status())) {
                return new Output("success", taskOutput);
            } else {
                return new Output("not_ready", taskOutput);
            }
        }

        long deadline = System.currentTimeMillis() + timeout;
        while (true) {
            var current = taskService.get(taskId);
            if (current.isEmpty()) {
                return new Output("not_ready", null);
            }
            var rec = current.get();
            if (isDone(rec.status())) {
                return new Output("success", new TaskOutput(
                    rec.id().toString(),
                    rec.status(),
                    rec.description(),
                    rec.output()
                ));
            }
            if (System.currentTimeMillis() >= deadline) {
                return new Output("timeout", new TaskOutput(
                    rec.id().toString(),
                    rec.status(),
                    rec.description(),
                    rec.output()
                ));
            }
            Thread.sleep(500);
        }
    }

    private boolean isDone(String status) {
        return status != null && DONE_STATUSES.contains(status.toLowerCase());
    }

    public record Input(String task_id, boolean block, int timeout) {
        public Input(String task_id, boolean block, int timeout) {
            this.task_id = task_id;
            this.block = block;
            this.timeout = timeout <= 0 ? 30000 : Math.min(timeout, 600000);
        }
    }

    public record Output(String retrieval_status, TaskOutput task) {
    }

    public record TaskOutput(String task_id, String status, String description, String output) {
    }
}
