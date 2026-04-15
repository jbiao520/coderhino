package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class SleepTool implements ToolDefinition<SleepTool.Input, String> {
    @Override
    public String name() {
        return "sleep";
    }

    @Override
    public String description() {
        return "Wait for a specified number of milliseconds, then return a confirmation string";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "durationMs", Map.of("type", "integer", "description", "Number of milliseconds to sleep")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.durationMs() == null || input.durationMs() < 0) {
            return PermissionResult.deny("durationMs must be a non-negative integer.");
        }
        if (input.durationMs() > 60_000) {
            return PermissionResult.deny("durationMs must not exceed 60000 (60 seconds).");
        }
        return PermissionResult.allow();
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        Thread.sleep(input.durationMs());
        return "Slept " + input.durationMs() + "ms";
    }

    public record Input(Integer durationMs) {
    }
}
