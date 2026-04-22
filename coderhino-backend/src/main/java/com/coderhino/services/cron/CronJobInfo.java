package com.coderhino.services.cron;

import java.time.Instant;

public record CronJobInfo(
        String jobId,
        String expression,
        String description,
        Instant registeredAt,
        Instant nextRun,
        boolean active
) {
}
