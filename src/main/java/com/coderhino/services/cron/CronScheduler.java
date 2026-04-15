package com.coderhino.services.cron;

import java.util.List;
import java.util.Optional;

public interface CronScheduler {

    String schedule(String expression, String description, Runnable job);

    boolean cancel(String jobId);

    Optional<CronJobInfo> getJob(String jobId);

    List<CronJobInfo> listJobs();

    void shutdown();

    default String serviceName() {
        return "cron-scheduler";
    }
}
