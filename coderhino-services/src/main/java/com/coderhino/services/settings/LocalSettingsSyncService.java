package com.coderhino.services.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalSettingsSyncService implements SettingsSyncService {

    private final Path settingsFile;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> local;
    private final AtomicBoolean synced;

    public LocalSettingsSyncService() {
        this(defaultSettingsFile(), new ObjectMapper());
    }

    public LocalSettingsSyncService(Path settingsFile) {
        this(settingsFile, new ObjectMapper());
    }

    public LocalSettingsSyncService(Path settingsFile, ObjectMapper objectMapper) {
        this.settingsFile = settingsFile;
        this.objectMapper = objectMapper;
        this.local = new ConcurrentHashMap<>();
        this.synced = new AtomicBoolean(false);
    }

    @Override
    public void sync() {
        try {
            Files.createDirectories(settingsFile.getParent());
            if (Files.exists(settingsFile)) {
                String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
                if (!json.isBlank()) {
                    Map<String, Object> loaded = objectMapper.readValue(json, new TypeReference<>() {});
                    if (loaded != null) {
                        local.putAll(loaded);
                    }
                }
            }
            persist();
            synced.set(true);
        } catch (IOException e) {
            synced.set(false);
        }
    }

    @Override
    public Object getRemoteSetting(String key) {
        return local.get(key);
    }

    @Override
    public void setLocalSetting(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            local.remove(key);
        } else {
            local.put(key, value);
        }
        try {
            Files.createDirectories(settingsFile.getParent());
            persist();
            synced.set(true);
        } catch (IOException e) {
            synced.set(false);
        }
    }

    @Override
    public boolean isSynced() {
        return synced.get();
    }

    public Path settingsFile() {
        return settingsFile;
    }

    public Map<String, Object> localSnapshot() {
        return Map.copyOf(local);
    }

    private void persist() throws IOException {
        String json = objectMapper.writeValueAsString(local);
        Files.writeString(
                settingsFile,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static Path defaultSettingsFile() {
        return Path.of(System.getProperty("user.home"), ".coderhino", "settings-sync.json");
    }
}
