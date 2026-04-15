package com.coderhino.tools.builtin;

import com.coderhino.services.analytics.FeatureFlag;
import com.coderhino.services.cron.CronScheduler;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.time.Instant;
import java.util.Map;

public final class CronCreateTool implements ToolDefinition<CronCreateTool.Input, CronCreateTool.Output> {

    @Override
    public String name() {
        return "cron_create";
    }

    @Override
    public String description() {
        return "Schedule a recurring job with a cron expression or interval. Returns a jobId and nextRun timestamp.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "expression", Map.of("type", "string",
                "description", "Cron expression or interval (e.g. '5m', '30s', '0 * * * *', '@every 1h')"),
            "description", Map.of("type", "string",
                "description", "Human-readable description of the scheduled job")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.expression() == null || input.expression().isBlank()) {
            return PermissionResult.deny("expression must not be blank.");
        }
        if (input.description() == null || input.description().isBlank()) {
            return PermissionResult.deny("description must not be blank.");
        }
        if (!context.services().featureFlags().isEnabled(FeatureFlag.PROACTIVE)) {
            return PermissionResult.deny("PROACTIVE feature flag is not enabled.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        CronScheduler scheduler = context.services().cronScheduler();
        String jobId = scheduler.schedule(input.expression(), input.description(), () -> {});
        long intervalMs = com.coderhino.services.cron.DefaultCronScheduler.parseIntervalMs(input.expression());
        Instant nextRun = Instant.now().plusMillis(intervalMs);
        return new Output(jobId, input.expression(), input.description(), nextRun.toString(), "scheduled");
    }

    public record Input(String expression, String description) {
        public Input {
            if (expression != null) expression = expression.strip();
            if (description != null) description = description.strip();
        }
    }

    public record Output(String jobId, String expression, String description, String nextRun, String status) {
    }
}
