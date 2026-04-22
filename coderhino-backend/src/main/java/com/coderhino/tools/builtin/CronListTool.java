package com.coderhino.tools.builtin;

import com.coderhino.services.cron.CronJobInfo;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CronListTool implements ToolDefinition<CronListTool.Input, CronListTool.Output> {

    @Override
    public String name() {
        return "cron_list";
    }

    @Override
    public String description() {
        return "List all scheduled cron jobs with their IDs, expressions, descriptions, and status.";
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
    public PermissionResult validate(Input input, ToolContext context) {
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        List<CronJobInfo> jobs = context.services().cronScheduler().listJobs();
        List<JobSummary> summaries = jobs.stream()
            .map(info -> new JobSummary(
                info.jobId(),
                info.expression(),
                info.description(),
                info.nextRun().toString(),
                info.active()
            ))
            .collect(Collectors.toList());
        return new Output(summaries);
    }

    public record Input() {
    }

    public record JobSummary(String jobId, String expression, String description, String nextRun, boolean active) {
    }

    public record Output(List<JobSummary> jobs) {
    }
}
