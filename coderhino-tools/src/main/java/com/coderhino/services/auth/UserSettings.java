package com.coderhino.services.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserSettings {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean loggedIn;
    private String username;
    private String accountTier;
    private String readTtsBackend;

    public UserSettings() {
    }

    public UserSettings(boolean loggedIn, String username, String accountTier) {
        this(loggedIn, username, accountTier, null);
    }

    public UserSettings(boolean loggedIn, String username, String accountTier, String readTtsBackend) {
        this.loggedIn = loggedIn;
        this.username = username;
        this.accountTier = accountTier;
        this.readTtsBackend = readTtsBackend;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccountTier() {
        return accountTier;
    }

    public void setAccountTier(String accountTier) {
        this.accountTier = accountTier;
    }

    public String getReadTtsBackend() {
        return readTtsBackend;
    }

    public void setReadTtsBackend(String readTtsBackend) {
        this.readTtsBackend = readTtsBackend;
    }

    public void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("loggedIn", loggedIn);
            if (username != null) data.put("username", username);
            if (accountTier != null) data.put("accountTier", accountTier);
            if (readTtsBackend != null) data.put("readTtsBackend", readTtsBackend);
            Files.writeString(configDir.resolve("user-settings.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save user settings: " + e.getMessage(), e);
        }
    }

    public static UserSettings load(Path configDir) {
        Path settingsFile = configDir.resolve("user-settings.json");
        if (!Files.exists(settingsFile)) {
            return new UserSettings(false, null, null);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = MAPPER.readValue(Files.readString(settingsFile), LinkedHashMap.class);
            boolean loggedIn = Boolean.TRUE.equals(data.get("loggedIn"));
            String username = (String) data.get("username");
            String accountTier = (String) data.get("accountTier");
            String readTtsBackend = (String) data.get("readTtsBackend");
            return new UserSettings(loggedIn, username, accountTier, readTtsBackend);
        } catch (IOException e) {
            return new UserSettings(false, null, null, null);
        }
    }
}
