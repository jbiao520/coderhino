package com.coderhino.skills;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FileSystemSkillService implements SkillService {

    private final Path skillsDir;
    private final ObjectMapper objectMapper;
    private final Set<String> removedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public FileSystemSkillService() {
        this(defaultSkillsDir(), new ObjectMapper());
    }

    public FileSystemSkillService(Path skillsDir) {
        this(skillsDir, new ObjectMapper());
    }

    public FileSystemSkillService(Path skillsDir, ObjectMapper objectMapper) {
        this.skillsDir = skillsDir;
        this.objectMapper = objectMapper;
    }

    @Override
    public void remove(String name) {
        if (name != null && !name.isBlank()) {
            removedIds.add(name);
        }
    }

    @Override
    public String executeSkill(String id, String input) {
        return "Legacy JSON skill execution is no longer supported. Move this skill to markdown under .claude/skills or .opencode/skills.";
    }

    @Override
    public List<SkillDescriptor> list() {
        // Legacy JSON skill descriptors are retained only for plugin compatibility.
        if (!Files.isDirectory(skillsDir)) {
            return Collections.emptyList();
        }
        List<SkillDescriptor> result = new ArrayList<>();
        try (var stream = Files.list(skillsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> readDescriptor(p).filter(d -> !removedIds.contains(d.id())).ifPresent(result::add));
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<SkillDescriptor> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        if (removedIds.contains(id)) {
            return Optional.empty();
        }
        Path file = skillsDir.resolve(sanitize(id) + ".json");
        return readDescriptor(file);
    }

    public void saveSkill(SkillDescriptor descriptor) {
        // Plugin plumbing still writes legacy JSON descriptors, but runtime skill lookup now uses markdown discovery.
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        try {
            Files.createDirectories(skillsDir);
            Map<String, Object> data = Map.of(
                "id", descriptor.id(),
                "name", descriptor.name(),
                "description", nullToEmpty(descriptor.description()),
                "filePath", nullToEmpty(descriptor.filePath()),
                "steps", descriptor.steps() != null ? descriptor.steps() : List.of()
            );
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(
                skillsDir.resolve(sanitize(descriptor.id()) + ".json"),
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save skill: " + e.getMessage(), e);
        }
    }

    public boolean deleteSkill(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Path file = skillsDir.resolve(sanitize(id) + ".json");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    public Path skillsDir() {
        return skillsDir;
    }

    private Optional<SkillDescriptor> readDescriptor(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            String id = (String) data.getOrDefault("id", "");
            String name = (String) data.getOrDefault("name", "");
            String description = (String) data.getOrDefault("description", "");
            String filePath = (String) data.getOrDefault("filePath", "");
            @SuppressWarnings("unchecked")
            List<String> steps = (List<String>) data.getOrDefault("steps", List.of());
            return Optional.of(new SkillDescriptor(id, name, description, filePath, steps));
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

    private static Path defaultSkillsDir() {
        return Path.of(System.getProperty("user.home"), ".coderhino", "skills");
    }
}
