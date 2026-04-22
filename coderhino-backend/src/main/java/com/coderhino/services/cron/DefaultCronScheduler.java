package com.coderhino.services.cron;

import com.coderhino.services.analytics.FeatureFlag;
import com.coderhino.services.analytics.FeatureFlagService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class DefaultCronScheduler implements CronScheduler {

    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<String, CronJobEntry> jobs;
    private final FeatureFlagService featureFlagService;

    public DefaultCronScheduler() {
        this(new NoOpFeatureFlagService());
    }

    public DefaultCronScheduler(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService != null ? featureFlagService : new NoOpFeatureFlagService();
        this.executor = Executors.newScheduledThreadPool(4, r -> {
            var t = new Thread(r, "cron-worker");
            t.setDaemon(true);
            return t;
        });
        this.jobs = new ConcurrentHashMap<>();
    }

    private boolean isKairosCronEnabled() {
        return featureFlagService.isEnabled(FeatureFlag.KAIROS)
                && featureFlagService.isEnabled(FeatureFlag.PROACTIVE);
    }

    @Override
    public String schedule(String expression, String description, Runnable job) {
        if (!isKairosCronEnabled()) {
            return "no-op-" + UUID.randomUUID();
        }

        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
        if (job == null) {
            throw new IllegalArgumentException("job must not be null");
        }

        String jobId = UUID.randomUUID().toString();
        long intervalMs = parseIntervalMs(expression);
        Instant now = Instant.now();
        Instant nextRun = now.plusMillis(intervalMs);

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                job, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        CronJobInfo info = new CronJobInfo(
                jobId,
                expression,
                description != null ? description : "",
                now,
                nextRun,
                true
        );
        jobs.put(jobId, new CronJobEntry(info, future));
        return jobId;
    }

    public String scheduleWithJitter(Runnable task, long intervalMs, double jitterFraction) {
        if (!isKairosCronEnabled()) {
            return "no-op-" + UUID.randomUUID();
        }

        long jitterMs = (long) (intervalMs * jitterFraction * (Math.random() * 2 - 1));
        long actualInterval = Math.max(100, intervalMs + jitterMs);

        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant nextRun = now.plusMillis(actualInterval);

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                task, actualInterval, actualInterval, TimeUnit.MILLISECONDS);

        CronJobInfo info = new CronJobInfo(
                jobId,
                "@every " + actualInterval + "ms",
                "jitter-scheduled",
                now,
                nextRun,
                true
        );
        jobs.put(jobId, new CronJobEntry(info, future));
        return jobId;
    }

    public CronJitterConfig getCronJitterConfig() {
        return new CronJitterConfig(60_000L, 0.10);
    }

    @Override
    public boolean cancel(String jobId) {
        var entry = jobs.remove(jobId);
        if (entry == null) {
            return false;
        }
        entry.future().cancel(false);
        return true;
    }

    @Override
    public Optional<CronJobInfo> getJob(String jobId) {
        var entry = jobs.get(jobId);
        if (entry == null) {
            return Optional.empty();
        }
        boolean active = !entry.future().isCancelled() && !entry.future().isDone();
        var info = entry.info();
        if (active != info.active()) {
            var updated = new CronJobInfo(
                    info.jobId(), info.expression(), info.description(),
                    info.registeredAt(), info.nextRun(), active);
            jobs.put(jobId, new CronJobEntry(updated, entry.future()));
            return Optional.of(updated);
        }
        return Optional.of(info);
    }

    @Override
    public List<CronJobInfo> listJobs() {
        List<CronJobInfo> result = new ArrayList<>();
        jobs.values().forEach(e -> result.add(e.info()));
        return Collections.unmodifiableList(result);
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
        jobs.clear();
    }

    public static long parseIntervalMs(String expression) {
        if (expression == null || expression.isBlank()) {
            return 60_000L;
        }
        String trimmed = expression.strip().toLowerCase();

        if (trimmed.matches("\\d+ms")) {
            return Long.parseLong(trimmed.replace("ms", ""));
        }
        if (trimmed.matches("\\d+s")) {
            return Long.parseLong(trimmed.replace("s", "")) * 1_000L;
        }
        if (trimmed.matches("\\d+m")) {
            return Long.parseLong(trimmed.replace("m", "")) * 60_000L;
        }
        if (trimmed.matches("\\d+h")) {
            return Long.parseLong(trimmed.replace("h", "")) * 3_600_000L;
        }
        if (trimmed.matches("\\d+")) {
            return Long.parseLong(trimmed) * 1_000L;
        }

        if (trimmed.startsWith("@every ")) {
            String rest = trimmed.substring(7).strip();
            return parseIntervalMs(rest);
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 5) {
            String minutePart = parts[0];
            if (!minutePart.equals("*")) {
                try {
                    long minuteVal = Long.parseLong(minutePart);
                    return minuteVal * 60_000L;
                } catch (NumberFormatException ignored) {
                }
            }
            return 60_000L;
        }

        return 60_000L;
    }

    private record CronJobEntry(CronJobInfo info, Future<?> future) {
    }
}
