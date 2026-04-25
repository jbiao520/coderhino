package com.coderhino.services.cron;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class NoOpCronScheduler implements CronScheduler {

    @Override
    public String schedule(String expression, String description, Runnable job) {
        return "noop-job";
    }

    @Override
    public boolean cancel(String jobId) {
        return false;
    }

    @Override
    public Optional<CronJobInfo> getJob(String jobId) {
        return Optional.empty();
    }

    @Override
    public List<CronJobInfo> listJobs() {
        return Collections.emptyList();
    }

    @Override
    public void shutdown() {
    }
}
