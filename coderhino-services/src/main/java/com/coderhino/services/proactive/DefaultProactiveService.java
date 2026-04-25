package com.coderhino.services.proactive;

import com.coderhino.services.analytics.FeatureFlag;
import com.coderhino.services.analytics.FeatureFlagService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultProactiveService implements ProactiveService {

    public static final ThreadLocal<Map<String, String>> ATTRIBUTION_CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    private final AtomicBoolean enabled;
    private final ScheduledExecutorService scheduler;
    private final FeatureFlagService featureFlagService;
    private final Map<String, String> jobAgentMap;

    public DefaultProactiveService() {
        this(false);
    }

    public DefaultProactiveService(boolean initiallyEnabled) {
        this(initiallyEnabled, new NoOpFeatureFlagService());
    }

    public DefaultProactiveService(boolean initiallyEnabled, FeatureFlagService featureFlagService) {
        this.enabled = new AtomicBoolean(initiallyEnabled);
        this.featureFlagService = featureFlagService != null ? featureFlagService : new NoOpFeatureFlagService();
        this.jobAgentMap = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            var t = new Thread(r, "proactive-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void enable() {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public String scheduleWork(Runnable work, long delayMs) {
        String jobId = UUID.randomUUID().toString();
        if (work != null && delayMs >= 0) {
            if (!featureFlagService.isEnabled(FeatureFlag.PROACTIVE)) {
                return jobId;
            }
            scheduler.schedule(() -> {
                if (enabled.get()) {
                    Map<String, String> ctx = ATTRIBUTION_CONTEXT.get();
                    ctx.put("X-Attribution", "WORKLOAD_CRON");
                    try {
                        work.run();
                    } catch (Exception ignored) {
                    } finally {
                        ctx.remove("X-Attribution");
                    }
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
        return jobId;
    }

    public String scheduleTeammateWork(Runnable work, long delayMs, String agentId) {
        String jobId = UUID.randomUUID().toString();
        if (work != null && delayMs >= 0) {
            if (!featureFlagService.isEnabled(FeatureFlag.PROACTIVE)) {
                if (agentId != null) {
                    jobAgentMap.put(jobId, agentId);
                }
                return jobId;
            }
            if (agentId != null) {
                jobAgentMap.put(jobId, agentId);
            }
            scheduler.schedule(() -> {
                if (enabled.get()) {
                    Map<String, String> ctx = ATTRIBUTION_CONTEXT.get();
                    ctx.put("X-Attribution", "WORKLOAD_CRON");
                    try {
                        work.run();
                    } catch (Exception ignored) {
                    } finally {
                        ctx.remove("X-Attribution");
                    }
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
        return jobId;
    }

    public int cleanupOrphanJobs(Set<String> liveAgentIds) {
        int removed = 0;
        for (Map.Entry<String, String> entry : jobAgentMap.entrySet()) {
            String agentId = entry.getValue();
            if (!liveAgentIds.contains(agentId)) {
                System.err.println("[ProactiveService] WARNING: orphan job detected for agent " + agentId
                        + " (jobId=" + entry.getKey() + "); removing.");
                jobAgentMap.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    @Override
    public String invokeBrief(String briefDescription) {
        if (briefDescription == null || briefDescription.isBlank()) {
            return "brief:no-op";
        }
        String jobId = UUID.randomUUID().toString();
        return "brief:" + jobId + ":" + briefDescription.strip();
    }

    @Override
    public void shutdown() {
        enabled.set(false);
        scheduler.shutdownNow();
    }
}
