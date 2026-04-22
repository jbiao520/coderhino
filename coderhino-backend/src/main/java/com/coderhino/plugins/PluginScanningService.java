package com.coderhino.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PluginScanningService {
    private final FileSystemPluginService pluginService;

    public PluginScanningService(FileSystemPluginService pluginService) {
        this.pluginService = pluginService;
    }

    public List<PluginManifest> scanDirectory(Path rootDir) {
        if (!Files.isDirectory(rootDir)) {
            return List.of();
        }
        try (var stream = Files.list(rootDir)) {
            stream.filter(Files::isDirectory)
                  .filter(sub -> Files.exists(sub.resolve("plugin.json")))
                  .forEach(pluginService::loadFromDirectory);
        } catch (IOException e) {
            return List.of();
        }
        return pluginService.listManifests();
    }

    public List<PluginManifest> scanDefaultDirectory() {
        return scanDirectory(Path.of(System.getProperty("user.home"), ".claudecode-plugins"));
    }
}
