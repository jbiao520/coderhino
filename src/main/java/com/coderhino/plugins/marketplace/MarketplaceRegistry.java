package com.coderhino.plugins.marketplace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class MarketplaceRegistry {
    private final Path storageFile;
    private final ObjectMapper objectMapper;
    private final List<MarketplaceDefinition> definitions;

    public MarketplaceRegistry(Path storageFile, ObjectMapper objectMapper) {
        this.storageFile = storageFile;
        this.objectMapper = objectMapper;
        this.definitions = new ArrayList<>(load());
    }

    public MarketplaceRegistry() {
        this(defaultStorageFile(), new ObjectMapper());
    }

    public void add(MarketplaceDefinition def) {
        definitions.add(def);
        persist();
    }

    public void remove(String name) {
        definitions.removeIf(d -> d.name().equals(name));
        persist();
    }

    public List<MarketplaceDefinition> list() {
        return Collections.unmodifiableList(definitions);
    }

    public Optional<MarketplaceDefinition> findByName(String name) {
        return definitions.stream().filter(d -> d.name().equals(name)).findFirst();
    }

    private List<MarketplaceDefinition> load() {
        if (!Files.exists(storageFile)) return List.of();
        try {
            String json = Files.readString(storageFile);
            return objectMapper.readValue(json, new TypeReference<List<MarketplaceDefinition>>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storageFile.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(definitions);
            Files.writeString(storageFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[marketplace] Failed to persist registry: " + e.getMessage());
        }
    }

    private static Path defaultStorageFile() {
        return Path.of(System.getProperty("user.home"), ".coderhino", "marketplaces.json");
    }
}
