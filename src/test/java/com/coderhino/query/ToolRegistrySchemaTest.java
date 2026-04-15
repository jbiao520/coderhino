package com.coderhino.query;

import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.mcp.McpServerDefinition;
import com.coderhino.services.mcp.McpToolDescriptor;
import com.coderhino.tools.builtin.ToolSearchTool;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrySchemaTest {

    @org.junit.jupiter.api.Test
    void toSchemasReturnsSchemasForEnabledTools() {
        var registry = ToolRegistry.createDefault();
        var schemas = registry.toSchemas();

        assertFalse(schemas.isEmpty(), "Should have tool schemas");

        for (ToolSchema schema : schemas) {
            assertNotNull(schema.name(), "name should not be null");
            assertNotNull(schema.description(), "description should not be null");
            assertNotNull(schema.inputSchema(), "inputSchema should not be null");
            assertEquals("object", schema.inputSchema().get("type"));
            assertNotNull(schema.inputSchema().get("properties"));
        }
    }

    @org.junit.jupiter.api.Test
    void toSchemasExcludesDisabledTools() {
        var enabledTool = new StubTool("enabled-tool", "desc", ToolInputSchema.object(Map.of()));
        var disabledTool = new StubTool("disabled-tool", "desc", ToolInputSchema.object(Map.of())) {
            @Override
            public boolean isEnabled() {
                return false;
            }
        };
        var registry = new ToolRegistry(java.util.List.<ToolDefinition<?, ?>>of(enabledTool, disabledTool));
        var schemas = registry.toSchemas();

        assertEquals(1, schemas.size());
        assertEquals("enabled-tool", schemas.get(0).name());
    }

    @org.junit.jupiter.api.Test
    void toSchemasPreservesRegistrationOrder() {
        var tool1 = new StubTool("alpha", "first", ToolInputSchema.object(Map.of()));
        var tool2 = new StubTool("beta", "second", ToolInputSchema.object(Map.of()));
        var tool3 = new StubTool("gamma", "third", ToolInputSchema.object(Map.of()));
        var registry = new ToolRegistry(java.util.List.<ToolDefinition<?, ?>>of(tool1, tool2, tool3));
        var schemas = registry.toSchemas();

        assertEquals(List.of("alpha", "beta", "gamma"),
            schemas.stream().map(ToolSchema::name).toList());
    }

    @org.junit.jupiter.api.Test
    void toSchemasSerializesInputSchemaWithTypeAndProperties() {
        Map<String, Object> props = Map.of("path", (Object) Map.of("type", "string", "description", "file path"));
        var registry = new ToolRegistry(java.util.List.<ToolDefinition<?, ?>>of(
            new StubTool("tool", "desc", ToolInputSchema.object(props))
        ));
        var schemas = registry.toSchemas();

        assertEquals(1, schemas.size());
        var inputSchema = schemas.get(0).inputSchema();
        assertEquals("object", inputSchema.get("type"));
        assertEquals(props, inputSchema.get("properties"));
    }

    @org.junit.jupiter.api.Test
    void toSchemasIncludesDynamicMcpToolsAlongsideBuiltIns() throws Exception {
        var registry = ToolRegistry.createDefault();
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("codeRhino Slack", "noop", List.of(), Map.of(), true, 30_000L));
        setDiscoveredTools(mcp, "codeRhino Slack", List.of(
            new McpToolDescriptor(
                "send message",
                "Post a Slack message",
                Map.of("type", "object", "properties", Map.of("channel", Map.of("type", "string"))),
                true,
                false
            )
        ));

        var schemas = registry.toSchemas(mcp);

        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("mcp__codeRhino_Slack__send_message")));
        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("mcp")));
        var dynamic = schemas.stream()
            .filter(schema -> schema.name().equals("mcp__codeRhino_Slack__send_message"))
            .findFirst()
            .orElseThrow();
        assertEquals("Post a Slack message", dynamic.description());
        assertEquals("object", dynamic.inputSchema().get("type"));
    }

    @org.junit.jupiter.api.Test
    void toSchemasSkipsInvalidDynamicMcpToolsWithoutDroppingOthers() throws Exception {
        var registry = ToolRegistry.createDefault();
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("filesystem", "noop", List.of(), Map.of(), true, 30_000L));
        setDiscoveredTools(mcp, "filesystem", List.of(
            new McpToolDescriptor("read", "Read a file", Map.of("type", "object", "properties", Map.of()), true, false),
            new McpToolDescriptor("broken", "Broken schema", Map.of("type", "array"), false, false)
        ));

        var schemas = registry.toSchemas(mcp);

        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("mcp__filesystem__read")));
        assertNull(schemas.stream().filter(schema -> schema.name().equals("mcp__filesystem__broken")).findFirst().orElse(null));
        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("ListMcpResourcesTool")));
        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("ReadMcpResourceTool")));
    }

    @org.junit.jupiter.api.Test
    void filteredReturnsOnlyAllowedToolsInOriginalOrder() {
        var registry = ToolRegistry.createDefault().filtered(List.of("bash", "read_file", "todo_write"));

        assertEquals(
            List.of("bash", "read_file", "todo_write"),
            registry.all().stream().map(ToolDefinition::name).toList()
        );
    }

    @org.junit.jupiter.api.Test
    void filteredRebuildsToolSearchAgainstFilteredRegistry() throws Exception {
        var registry = ToolRegistry.createDefault().filtered(List.of("bash", "tool_search"));
        var searchTool = (ToolSearchTool) assertInstanceOf(ToolSearchTool.class, registry.find("tool_search").orElseThrow());
        var results = searchTool.execute(new ToolSearchTool.Input(null), null);

        assertEquals(List.of("bash: Execute a shell command in the working directory"), results);
    }

    @org.junit.jupiter.api.Test
    void filteredSkipsDynamicMcpSchemasWhenMcpToolIsNotAllowed() throws Exception {
        var registry = ToolRegistry.createDefault().filtered(List.of("bash"));
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("filesystem", "noop", List.of(), Map.of(), true, 30_000L));
        setDiscoveredTools(mcp, "filesystem", List.of(
            new McpToolDescriptor("read", "Read a file", Map.of("type", "object", "properties", Map.of()), true, false)
        ));

        var schemas = registry.toSchemas(mcp);

        assertEquals(List.of("bash"), schemas.stream().map(ToolSchema::name).toList());
    }

    @SuppressWarnings("unchecked")
    private static void setDiscoveredTools(McpConnectionManager manager, String serverName, List<McpToolDescriptor> tools) throws Exception {
        var field = McpConnectionManager.class.getDeclaredField("discoveredTools");
        field.setAccessible(true);
        var discoveredTools = (Map<String, List<McpToolDescriptor>>) field.get(manager);
        discoveredTools.put(serverName, List.copyOf(tools));
    }

    private static class StubTool implements ToolDefinition<Object, String> {
        private final String name;
        private final String description;
        private final ToolInputSchema schema;

        StubTool(String name, String description, ToolInputSchema schema) {
            this.name = name;
            this.description = description;
            this.schema = schema;
        }

        @Override public String name() { return name; }
        @Override public String description() { return description; }
        @Override public ToolInputSchema inputSchema() { return schema; }
        @Override public String execute(Object input, com.coderhino.tools.ToolContext context) { return "ok"; }
    }
}
