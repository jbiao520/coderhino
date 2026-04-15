package com.coderhino.web.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SettingsPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SettingsPersistenceService.class);
    private static final String SETTINGS_DIR = ".coderhino";
    private static final String SETTINGS_FILE = "web-settings.json";

    private final ObjectMapper objectMapper;
    private final Path settingsFile;

    public SettingsPersistenceService() {
        this(Path.of("").toAbsolutePath().normalize().resolve(SETTINGS_DIR).resolve(SETTINGS_FILE));
    }

    public SettingsPersistenceService(Path settingsFile) {
        this.settingsFile = settingsFile;
        this.objectMapper = new ObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public WebSettings load() {
        if (!Files.exists(settingsFile)) {
            return new WebSettings();
        }
        try {
            return objectMapper.readValue(settingsFile.toFile(), WebSettings.class);
        } catch (IOException e) {
            log.warn("Failed to load settings from {}: {}", settingsFile, e.getMessage());
            return new WebSettings();
        }
    }

    public void save(WebSettings settings) {
        try {
            var dir = settingsFile.getParent();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), settings);
        } catch (IOException e) {
            log.error("Failed to save settings to {}: {}", settingsFile, e.getMessage());
            throw new RuntimeException("Cannot save settings: " + settingsFile, e);
        }
    }
}
