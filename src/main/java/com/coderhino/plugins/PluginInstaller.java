package com.coderhino.plugins;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PluginInstaller {
    private final FileSystemPluginService pluginService;
    private final PluginComponentLoader componentLoader;
    private final com.coderhino.services.analytics.AnalyticsService analyticsService;

    public PluginInstaller(FileSystemPluginService pluginService, PluginComponentLoader componentLoader) {
        this(pluginService, componentLoader, null);
    }

    public PluginInstaller(FileSystemPluginService pluginService, PluginComponentLoader componentLoader, com.coderhino.services.analytics.AnalyticsService analyticsService) {
        this.pluginService = pluginService;
        this.componentLoader = componentLoader;
        this.analyticsService = analyticsService;
    }

    public InstallResult installFromLocalPath(Path sourcePath) {
        if (!Files.isDirectory(sourcePath)) {
            return InstallResult.failure(List.of("Source path is not a directory: " + sourcePath));
        }
        if (!Files.exists(sourcePath.resolve("plugin.json"))) {
            return InstallResult.failure(List.of("No plugin.json found in: " + sourcePath));
        }
        var validator = new PluginManifestValidator();
        var result = validator.validate(sourcePath);
        if (!result.isValid()) {
            return InstallResult.failure(result.errors());
        }
        pluginService.loadFromDirectory(sourcePath);
        var manifestOpt = pluginService.findManifestById(result.manifest().getId());
        if (manifestOpt.isEmpty()) {
            return InstallResult.failure(List.of("Failed to load manifest after validation"));
        }
        componentLoader.loadComponents(manifestOpt.get());
        if (analyticsService != null) analyticsService.trackEvent("plugin_install", manifestOpt.get().getId());
        return InstallResult.success(manifestOpt.get());
    }

    public record InstallResult(boolean success, PluginManifest manifest, List<String> errors) {
        public static InstallResult success(PluginManifest manifest) {
            return new InstallResult(true, manifest, List.of());
        }
        public static InstallResult failure(List<String> errors) {
            return new InstallResult(false, null, errors);
        }
    }
}
