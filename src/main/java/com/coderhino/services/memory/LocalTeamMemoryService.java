package com.coderhino.services.memory;

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

public final class LocalTeamMemoryService implements TeamMemoryService {

    private final Path teamMemoryDir;
    private final ObjectMapper objectMapper;

    public LocalTeamMemoryService() {
        this(defaultTeamMemoryDir(), new ObjectMapper());
    }

    public LocalTeamMemoryService(Path teamMemoryDir) {
        this(teamMemoryDir, new ObjectMapper());
    }

    public LocalTeamMemoryService(Path teamMemoryDir, ObjectMapper objectMapper) {
        this.teamMemoryDir = teamMemoryDir;
        this.objectMapper = objectMapper;
    }

    @Override
    public void share(String sessionId, List<String> facts, String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (facts == null || facts.isEmpty()) {
            return;
        }
        List<String> existing = new ArrayList<>(recall(teamId));
        for (String fact : facts) {
            if (fact != null && !fact.isBlank() && !existing.contains(fact)) {
                existing.add(fact);
            }
        }
        persistFacts(teamId, existing);
    }

    @Override
    public List<String> recall(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return List.of();
        }
        Path file = teamFile(teamId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return List.of();
            }
            List<String> facts = objectMapper.readValue(json, new TypeReference<>() {});
            return facts == null ? List.of() : Collections.unmodifiableList(facts);
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void sync(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return;
        }
        List<String> current = recall(teamId);
        persistFacts(teamId, current);
    }

    public Path teamFile(String teamId) {
        return teamMemoryDir.resolve(teamId + ".json");
    }

    public Path teamMemoryDir() {
        return teamMemoryDir;
    }

    private void persistFacts(String teamId, List<String> facts) {
        Path file = teamFile(teamId);
        try {
            Files.createDirectories(teamMemoryDir);
            String json = objectMapper.writeValueAsString(facts);
            Files.writeString(
                    file,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to persist team memory for team %s: %s".formatted(teamId, e.getMessage()), e);
        }
    }

    private static Path defaultTeamMemoryDir() {
        return Path.of(System.getProperty("user.home"), ".coderhino", "team-memory");
    }
}
