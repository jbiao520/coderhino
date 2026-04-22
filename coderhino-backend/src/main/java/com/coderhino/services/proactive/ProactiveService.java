package com.coderhino.services.proactive;

/**
 * Service interface for proactive/KAIROS assistant-style runtime flows.
 * <p>
 * Proactive mode allows the assistant to schedule and execute work
 * autonomously based on timers, triggers, or brief-style invocations.
 * <p>
 * Feature-flag gated: {@code FeatureFlag.PROACTIVE} and {@code FeatureFlag.KAIROS}.
 */
public interface ProactiveService {

    /**
     * Enable proactive mode. When enabled, scheduled work may execute autonomously.
     */
    void enable();

    /**
     * Disable proactive mode. Pending scheduled work is cancelled.
     */
    void disable();

    /**
     * Whether proactive mode is currently enabled.
     */
    boolean isEnabled();

    /**
     * Schedule a unit of work to run after a fixed delay.
     *
     * @param work    the task to execute
     * @param delayMs delay in milliseconds before the task runs
     * @return a job ID that can be used to identify the scheduled work
     */
    String scheduleWork(Runnable work, long delayMs);

    /**
     * Invoke a brief-style flow: a short contextual task that runs immediately
     * and completes synchronously or asynchronously in the background.
     *
     * @param briefDescription short description of the work to perform
     * @return a summary of what was invoked
     */
    String invokeBrief(String briefDescription);

    /**
     * Shut down the proactive service, releasing all scheduled resources.
     */
    void shutdown();

    /**
     * Service name for diagnostics.
     */
    default String serviceName() {
        return "proactive-service";
    }
}
