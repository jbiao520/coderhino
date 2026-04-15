package com.coderhino.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class McpConfigPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(McpConfigPersistenceService.class);

    private final ObjectMapper objectMapper;
    private final Path configFile;

    public McpConfigPersistenceService() {
        this(Path.of("").toAbsolutePath().normalize().resolve(".mcp.json"));
    }

    public McpConfigPersistenceService(Path configFile) {
        this.configFile = configFile;
        this.objectMapper = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public McpConfigDocument load() {
        if (!Files.exists(configFile)) {
            return new McpConfigDocument(defaultContent());
        }

        try {
            var content = Files.readString(configFile);
            return new McpConfigDocument(content == null || content.isBlank() ? defaultContent() : content);
        } catch (IOException e) {
            log.warn("Failed to load MCP config from {}: {}", configFile, e.getMessage());
            return new McpConfigDocument(defaultContent());
        }
    }

    public McpConfigDocument save(McpConfigDocument document) {
        if (document == null || document.getContent() == null || document.getContent().isBlank()) {
            throw new IllegalArgumentException("MCP config content is required");
        }

        final var root = parseRoot(document.getContent());

        try {
            var normalized = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            var dir = configFile.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Files.writeString(configFile, normalized);
            return new McpConfigDocument(normalized);
        } catch (IOException e) {
            log.error("Failed to save MCP config to {}: {}", configFile, e.getMessage());
            throw new RuntimeException("Cannot save MCP config: " + configFile, e);
        }
    }

    private ObjectNode parseRoot(String content) {
        try {
            var root = objectMapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("MCP config must be a JSON object.");
            }
            return (ObjectNode) root;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("MCP config must be valid JSON.", e);
        }
    }

    private String defaultContent() {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("mcpServers", objectMapper.createObjectNode());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (IOException e) {
            throw new RuntimeException("Cannot build default MCP config", e);
        }
    }
}
