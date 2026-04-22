package com.coderhino.services.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class McpSdkSession implements McpSession {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Field TRANSPORT_PROCESS_FIELD = transportProcessField();

    private final ObjectMapper objectMapper;
    private final StdioClientTransport transport;
    private final McpSyncClient client;

    McpSdkSession(McpServerDefinition definition, ObjectMapper objectMapper, long requestTimeoutMillis, Consumer<String> stderrConsumer) {
        this.objectMapper = objectMapper;

        var serverParameters = ServerParameters.builder(definition.command())
            .args(definition.arguments())
            .env(definition.environment())
            .build();
        this.transport = new StdioClientTransport(serverParameters, new JacksonMcpJsonMapper(objectMapper));
        this.transport.setStdErrorHandler(stderrConsumer == null ? ignored -> {
        } : stderrConsumer);

        this.client = McpClient.sync(transport)
            .requestTimeout(Duration.ofMillis(requestTimeoutMillis))
            .initializationTimeout(Duration.ofMillis(definition.initializeTimeoutMs()))
            .capabilities(McpSchema.ClientCapabilities.builder().build())
            .clientInfo(new McpSchema.Implementation("coderhino", "1.0.0-SNAPSHOT"))
            .build();
    }

    @Override
    public void initialize() {
        if (!client.isInitialized()) {
            client.initialize();
        }
    }

    @Override
    public List<McpToolDescriptor> listTools() {
        initialize();
        return client.listTools().tools().stream()
            .map(this::toToolDescriptor)
            .toList();
    }

    @Override
    public List<McpResourceDescriptor> listResources() {
        initialize();
        return client.listResources().resources().stream()
            .map(resource -> new McpResourceDescriptor(
                resource.uri(),
                defaultString(resource.name()),
                defaultString(resource.mimeType()),
                defaultString(resource.description())
            ))
            .toList();
    }

    @Override
    public String readResource(String uri) throws IOException {
        initialize();
        var result = client.readResource(new McpSchema.ReadResourceRequest(uri));
        if (result.contents() == null || result.contents().isEmpty()) {
            return objectMapper.writeValueAsString(result);
        }

        var fragments = new ArrayList<String>();
        for (var item : result.contents()) {
            if (item instanceof McpSchema.TextResourceContents text && text.text() != null) {
                fragments.add(text.text());
            } else if (item instanceof McpSchema.BlobResourceContents blob && blob.uri() != null) {
                fragments.add(blob.uri());
            } else {
                fragments.add(objectMapper.writeValueAsString(item));
            }
        }
        return McpFailureTranslator.renderMessages(fragments);
    }

    @Override
    public String callTool(String toolName, JsonNode arguments) throws IOException {
        initialize();
        var argumentMap = objectMapper.convertValue(
            arguments == null ? objectMapper.createObjectNode() : arguments,
            MAP_TYPE
        );
        var result = client.callTool(new McpSchema.CallToolRequest(toolName, argumentMap));
        if (result.content() == null || result.content().isEmpty()) {
            if (result.structuredContent() != null) {
                return objectMapper.writeValueAsString(result.structuredContent());
            }
            return objectMapper.writeValueAsString(result);
        }

        var fragments = new ArrayList<String>();
        for (var item : result.content()) {
            if (item instanceof McpSchema.TextContent text && text.text() != null) {
                fragments.add(text.text());
            } else {
                fragments.add(objectMapper.writeValueAsString(item));
            }
        }
        return McpFailureTranslator.renderMessages(fragments);
    }

    @Override
    public boolean ping() {
        initialize();
        client.ping();
        return true;
    }

    @Override
    public void subscribeResource(String uri) {
        initialize();
        client.subscribeResource(new McpSchema.SubscribeRequest(uri));
    }

    @Override
    public void unsubscribeResource(String uri) {
        initialize();
        client.unsubscribeResource(new McpSchema.UnsubscribeRequest(uri));
    }

    @Override
    public boolean hasStartedProcess() {
        return process() != null;
    }

    @Override
    public boolean isProcessAlive() {
        var process = process();
        return process != null && process.isAlive();
    }

    @Override
    public Long processId() {
        var process = process();
        return process == null ? null : process.pid();
    }

    @Override
    public void close() {
        try {
            if (!client.closeGracefully()) {
                client.close();
            }
        } catch (RuntimeException exception) {
            client.close();
        }
    }

    private McpToolDescriptor toToolDescriptor(McpSchema.Tool tool) {
        var annotations = tool.annotations();
        return new McpToolDescriptor(
            tool.name(),
            defaultString(tool.description()),
            tool.inputSchema() == null ? Map.of() : sanitizeSchema(toMap(tool.inputSchema())),
            annotations != null && Boolean.TRUE.equals(annotations.readOnlyHint()),
            annotations != null && Boolean.TRUE.equals(annotations.destructiveHint())
        );
    }

    private LinkedHashMap<String, Object> toMap(McpSchema.JsonSchema schema) {
        return objectMapper.convertValue(schema, MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeSchema(Map<String, Object> schema) {
        var sanitized = new LinkedHashMap<String, Object>();
        for (var entry : schema.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sanitized = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || entry.getValue() == null) {
                    continue;
                }
                sanitized.put(key, sanitizeValue(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(this::sanitizeValue).toList();
        }
        return value;
    }

    private static Field transportProcessField() {
        try {
            var field = StdioClientTransport.class.getDeclaredField("process");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Unable to access MCP SDK stdio process field", exception);
        }
    }

    private Process process() {
        try {
            return (Process) TRANSPORT_PROCESS_FIELD.get(transport);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to inspect MCP SDK stdio process state", exception);
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
