package com.coderhino.services.config;

import com.coderhino.services.mcp.McpServerDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class McpConfigLoader {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<McpServerDefinition> load(Path cwd) {
        var configPath = cwd.resolve(".mcp.json");
        if (!Files.exists(configPath)) {
            return List.of();
        }

        try {
            var root = objectMapper.readTree(Files.readString(configPath));
            var serversNode = root.path("mcpServers");
            if (!serversNode.isObject()) {
                return List.of();
            }

            var servers = new ArrayList<McpServerDefinition>();
            Iterator<Map.Entry<String, JsonNode>> fields = serversNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                var value = entry.getValue();
                var command = value.path("command").asText("");
                if (command.isBlank()) {
                    continue;
                }
                var args = new ArrayList<String>();
                if (value.path("args").isArray()) {
                    value.path("args").forEach(node -> args.add(node.asText()));
                }
                Map<String, String> env = Map.of();
                if (value.path("env").isObject()) {
                    env = objectMapper.convertValue(value.path("env"), new TypeReference<Map<String, String>>() {});
                }
                var enabled = !value.has("enabled") || value.path("enabled").asBoolean(true);
                var initializeTimeoutMs = value.path("initializeTimeoutMs").asLong(30_000L);
                servers.add(new McpServerDefinition(entry.getKey(), command, args, env, enabled, initializeTimeoutMs));
            }
            return servers;
        } catch (IOException exception) {
            return List.of();
        }
    }
}
