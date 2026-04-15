package com.coderhino.plugins;

import com.coderhino.services.analytics.AnalyticsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class PluginAutoUpdater {
    private final FileSystemPluginService pluginService;
    private final AnalyticsService analyticsService;
    private volatile boolean running = false;

    public PluginAutoUpdater(FileSystemPluginService pluginService, AnalyticsService analyticsService) {
        this.pluginService = pluginService;
        this.analyticsService = analyticsService;
    }

    public void startBackgroundCheck() {
        running = true;
        var thread = new Thread(this::runLoop);
        thread.setDaemon(true);
        thread.setName("plugin-autoupdater");
        thread.start();
    }

    public void stop() {
        running = false;
    }

    private void runLoop() {
        Map<String, Long> lastMtimes = new HashMap<>();
        for (var manifest : pluginService.listManifests()) {
            Path pluginJson = manifest.getPath() != null ? manifest.getPath().resolve("plugin.json") : null;
            if (pluginJson != null && Files.exists(pluginJson)) {
                try {
                    lastMtimes.put(manifest.getId(), Files.getLastModifiedTime(pluginJson).toMillis());
                } catch (Exception ignored) {}
            }
        }

        while (running) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!running) break;

            for (var manifest : pluginService.listManifests()) {
                Path pluginJson = manifest.getPath() != null ? manifest.getPath().resolve("plugin.json") : null;
                if (pluginJson == null || !Files.exists(pluginJson)) continue;
                try {
                    long currentMtime = Files.getLastModifiedTime(pluginJson).toMillis();
                    Long lastMtime = lastMtimes.get(manifest.getId());
                    if (lastMtime != null && currentMtime != lastMtime) {
                        System.out.println("[plugin-autoupdate] Plugin " + manifest.getId() + " has been updated on disk (restart to apply)");
                        if (analyticsService != null) {
                            analyticsService.trackEvent("plugin_update_available", manifest.getId());
                        }
                        lastMtimes.put(manifest.getId(), currentMtime);
                    } else if (lastMtime == null) {
                        lastMtimes.put(manifest.getId(), currentMtime);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
