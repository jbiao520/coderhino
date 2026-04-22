package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class CronDeleteTool implements ToolDefinition<CronDeleteTool.Input, CronDeleteTool.Output> {

    @Override
    public String name() {
        return "cron_delete";
    }

    @Override
    public String description() {
        return "Cancel a scheduled cron job by its ID.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "id", Map.of("type", "string", "description", "The ID of the scheduled job to cancel")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        var job = context.services().cronScheduler().getJob(input.id());
        if (job.isEmpty()) {
            return PermissionResult.deny(String.format("No scheduled job with id '%s'", input.id()));
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        boolean cancelled = context.services().cronScheduler().cancel(input.id());
        return new Output(input.id(), cancelled);
    }

    public record Input(String id) {
    }

    public record Output(String id, boolean cancelled) {
    }
}
