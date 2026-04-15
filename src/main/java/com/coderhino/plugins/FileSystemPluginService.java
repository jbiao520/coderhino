package com.coderhino.plugins;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FileSystemPluginService implements PluginService {

    private final Path pluginsDir;
    private final ObjectMapper objectMapper;
    private final com.coderhino.services.analytics.AnalyticsService analyticsService;
    private final ConcurrentHashMap<String, PluginDescriptor> loaded = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PluginManifest> loadedManifests = new ConcurrentHashMap<>();

    public FileSystemPluginService() {
        this(defaultPluginsDir(), new ObjectMapper());
    }

    public FileSystemPluginService(Path pluginsDir) {
        this(pluginsDir, new ObjectMapper());
    }

    public FileSystemPluginService(Path pluginsDir, ObjectMapper objectMapper) {
        this(pluginsDir, objectMapper, null);
    }

    public FileSystemPluginService(Path pluginsDir, ObjectMapper objectMapper, com.coderhino.services.analytics.AnalyticsService analyticsService) {
        this.pluginsDir = pluginsDir;
        this.objectMapper = objectMapper;
        this.analyticsService = analyticsService;
    }

    @Override
    public void load(PluginDescriptor plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        loaded.put(plugin.id(), plugin);
        persist(plugin);
    }

    @Override
    public void unload(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        loaded.remove(id);
        loadedManifests.remove(id);
        Path file = pluginsDir.resolve(sanitize(id) + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    @Override
    public List<PluginDescriptor> list() {
        if (!Files.isDirectory(pluginsDir)) {
            return Collections.emptyList();
        }
        List<PluginDescriptor> result = new ArrayList<>();
        try (var stream = Files.list(pluginsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> readDescriptor(p).ifPresent(result::add));
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<PluginDescriptor> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        PluginDescriptor inMemory = loaded.get(id);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        Path file = pluginsDir.resolve(sanitize(id) + ".json");
        return readDescriptor(file);
    }

    public void loadFromDirectory(Path pluginDir) {
        PluginManifestValidator.ValidationResult result =
                new PluginManifestValidator(objectMapper).validate(pluginDir);
        if (!result.isValid()) {
            System.err.println("Plugin validation failed for " + pluginDir + ": " + result.errors());
            if (analyticsService != null) analyticsService.trackEvent("plugin_load_failed", pluginDir.toString());
            return;
        }
        PluginManifest manifest = result.manifest();
        loadedManifests.put(manifest.getId(), manifest);
        loaded.put(manifest.getId(), new PluginDescriptor(
                manifest.getId(),
                manifest.getName(),
                manifest.getVersion(),
                manifest.getDescription()
        ));
        if (analyticsService != null) analyticsService.trackEvent("plugin_enabled", manifest.getId());
    }

    public List<PluginManifest> listManifests() {
        return List.copyOf(loadedManifests.values());
    }

    public Optional<PluginManifest> findManifestById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        PluginManifest m = loadedManifests.get(id);
        return m != null ? Optional.of(m) : Optional.empty();
    }

    public Path pluginsDir() {
        return pluginsDir;
    }

    private void persist(PluginDescriptor plugin) {
        try {
            Files.createDirectories(pluginsDir);
            Map<String, String> data = Map.of(
                "id", plugin.id(),
                "name", nullToEmpty(plugin.name()),
                "version", nullToEmpty(plugin.version()),
                "description", nullToEmpty(plugin.description())
            );
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(
                pluginsDir.resolve(sanitize(plugin.id()) + ".json"),
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist plugin: " + e.getMessage(), e);
        }
    }

    private Optional<PluginDescriptor> readDescriptor(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> data = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            String id = data.getOrDefault("id", "");
            String name = data.getOrDefault("name", "");
            String version = data.getOrDefault("version", "");
            String description = data.getOrDefault("description", "");
            return Optional.of(new PluginDescriptor(id, name, version, description));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Path defaultPluginsDir() {
        return Path.of(System.getProperty("user.home"), ".coderhino", "plugins");
    }
}
