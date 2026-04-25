package com.coderhino.services.config;

import com.coderhino.services.mcp.McpServerDefinition;
import com.coderhino.tools.runtime.CommandMcpConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class McpConfigWriter implements CommandMcpConfigService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void addServer(Path cwd, McpServerDefinition definition) throws IOException {
        var root = loadRoot(cwd);
        var serversNode = getServersNode(root);
        serversNode.set(definition.name(), toServerNode(definition));
        root.set("mcpServers", serversNode);
        writeRoot(cwd, root);
    }

    @Override
    public void setServerEnabled(Path cwd, String serverName, boolean enabled) throws IOException {
        var root = loadRoot(cwd);
        var serversNode = getServersNode(root);
        var serverNode = serversNode.has(serverName) && serversNode.get(serverName).isObject()
            ? (ObjectNode) serversNode.get(serverName)
            : objectMapper.createObjectNode();
        serverNode.put("enabled", enabled);
        serversNode.set(serverName, serverNode);
        root.set("mcpServers", serversNode);
        writeRoot(cwd, root);
    }

    private ObjectNode loadRoot(Path cwd) throws IOException {
        var configPath = cwd.resolve(".mcp.json");
        if (Files.exists(configPath)) {
            return (ObjectNode) objectMapper.readTree(Files.readString(configPath));
        }
        return objectMapper.createObjectNode();
    }

    private ObjectNode getServersNode(ObjectNode root) {
        return root.has("mcpServers") && root.get("mcpServers").isObject()
            ? (ObjectNode) root.get("mcpServers")
            : objectMapper.createObjectNode();
    }

    private ObjectNode toServerNode(McpServerDefinition definition) {
        var serverNode = objectMapper.createObjectNode();
        serverNode.put("command", definition.command());
        ArrayNode argsNode = objectMapper.createArrayNode();
        definition.arguments().forEach(argsNode::add);
        serverNode.set("args", argsNode);
        serverNode.set("env", objectMapper.valueToTree(definition.environment()));
        serverNode.put("enabled", definition.enabled());
        return serverNode;
    }

    private void writeRoot(Path cwd, ObjectNode root) throws IOException {
        var configPath = cwd.resolve(".mcp.json");
        Files.writeString(configPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }
}
