package com.coderhino.agent;

import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryEventSink;
import com.coderhino.query.QueryRequest;
import com.coderhino.query.QueryResult;
import com.coderhino.server.NoOpServerService;
import com.coderhino.services.analytics.NoOpAnalyticsService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;
import com.coderhino.services.cron.NoOpCronScheduler;
import com.coderhino.services.trigger.NoOpRemoteTriggerService;
import com.coderhino.state.BootstrapState;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.builtin.FileReadTool;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.ToolInputSchema;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoderhinoAgentTest {

    @Test
    void embeddedDefaultsExposeReadOnlyToolsOnly() {
        var agent = CoderhinoAgent.builder()
            .modelClient(new CapturingModelClient())
            .build();

        var toolNames = agent.config().toolRegistry().all().stream()
            .map(ToolDefinition::name)
            .toList();

        assertTrue(toolNames.contains("read_file"));
        assertTrue(toolNames.contains("glob"));
        assertTrue(toolNames.contains("grep"));
        assertEquals(List.of("read_file", "glob", "grep"), toolNames);
        assertFalse(toolNames.contains("bash"));
        assertFalse(toolNames.contains("write_file"));
        assertFalse(toolNames.contains("edit_file"));
        assertFalse(toolNames.contains("web_fetch"));
        assertFalse(toolNames.contains("web_search"));
        assertFalse(toolNames.contains("mcp"));
        assertFalse(toolNames.contains("lsp"));
    }

    @Test
    void explicitEmptyBuiltInToolsPublishesNoBuiltIns() {
        var modelClient = new CapturingModelClient();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .enabledBuiltInTools(List.of())
            .build();

        agent.run("hello");

        assertTrue(modelClient.request.tools().isEmpty());
    }

    @Test
    void explicitBuiltInToolsCanOptIntoNetworkTools() {
        var agent = CoderhinoAgent.builder()
            .modelClient(new CapturingModelClient())
            .enabledBuiltInTools(List.of("web_fetch"))
            .build();

        var toolNames = agent.config().toolRegistry().all().stream()
            .map(ToolDefinition::name)
            .toList();

        assertEquals(List.of("web_fetch"), toolNames);
    }

    @Test
    void facadeRunsWithManagedStateAndReturnsResult() {
        var modelClient = new CapturingModelClient();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .permissionMode(PermissionMode.BYPASS)
            .build();

        var result = agent.run("hello");

        assertEquals("ok", result.finalText());
        assertEquals(ModelResponse.Usage.class, result.usage().getClass());
        assertEquals(2, result.state().messages().size());
        assertNotNull(modelClient.request);
        assertTrue(result.isSuccess());
    }

    @Test
    void modelErrorReturnsErrorResultAndDoesNotPersistAssistantReply() {
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient((state, request) -> new ModelResponse.ModelError("provider failed"))
            .eventSink(sink)
            .build();

        var result = agent.run("hello");

        assertTrue(result.isError());
        assertEquals(QueryResult.StopReason.ERROR, result.stopReason());
        assertEquals("provider failed", sink.lastError);
        assertFalse(sink.completedCalled);
        assertEquals(1, result.state().messages().size());
    }

    @Test
    void customToolIsPublishedInSchemas() {
        var modelClient = new CapturingModelClient();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(new ToolRegistry(List.of()))
            .addTool(new EchoTool())
            .build();

        agent.run("use echo");

        assertEquals(List.of("host_echo"), modelClient.request.tools().stream().map(com.coderhino.query.ToolSchema::name).toList());
    }

    @Test
    void customToolCanBeExecuted() {
        var modelClient = new ToolThenReplyModelClient();
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(new ToolRegistry(List.of()))
            .addTool(new EchoTool())
            .eventSink(sink)
            .build();

        var result = agent.run("use echo");

        assertEquals("done", result.finalText());
        assertEquals("hello", sink.lastToolResult);
    }

    @Test
    void requestSpecificBootstrapStateDoesNotUpdateManagedState() {
        var agent = CoderhinoAgent.builder()
            .modelClient(new CapturingModelClient())
            .build();
        var requestState = new BootstrapState(agent.state().withMessages(List.of()));

        var result = agent.run(new CoderhinoAgent.AgentRequest("hello", "hello", null, requestState));

        assertEquals(2, result.state().messages().size());
        assertTrue(agent.state().messages().isEmpty());
    }

    @Test
    void embeddedDefaultFileToolsAreConfinedToWorkspace() throws Exception {
        var workspace = Files.createTempDirectory("coderhino-workspace");
        var secret = Files.createTempFile("coderhino-secret", ".txt");
        Files.writeString(workspace.resolve("allowed.txt"), "allowed");
        Files.writeString(secret, "secret");
        var modelClient = new SequentialToolModelClient(List.of(
            new ModelResponse.ToolRequest("read_file", Map.of("path", "allowed.txt"), "tool-1"),
            new ModelResponse.ToolRequest("read_file", Map.of("path", secret.toAbsolutePath().toString()), "tool-2")
        ));
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .cwd(workspace)
            .eventSink(sink)
            .build();

        agent.run("read files");

        assertTrue(sink.toolResults.get(0).contains("allowed"));
        assertTrue(sink.toolResults.get(1).contains("Path must stay within workspace"));
    }

    @Test
    void embeddedGrepAcceptsPublishedSnakeCaseArguments() throws Exception {
        var workspace = Files.createTempDirectory("coderhino-workspace");
        Files.writeString(workspace.resolve("secrets.txt"), "sk-ant-test-token");
        var modelClient = new SequentialToolModelClient(List.of(
            new ModelResponse.ToolRequest("grep", Map.of(
                "pattern", "sk-ant",
                "path", "secrets.txt",
                "output_mode", "content",
                "head_limit", 10,
                "case_insensitive", true
            ), "tool-1")
        ));
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .cwd(workspace)
            .eventSink(sink)
            .build();

        agent.run("find token");

        assertTrue(sink.lastToolResult.contains("sk-ant-test-token"));
        assertFalse(sink.lastToolResult.contains("Invalid input for tool grep"));
    }

    @Test
    void explicitHostRegistryIsNotWorkspaceConfined() throws Exception {
        var workspace = Files.createTempDirectory("coderhino-workspace");
        var outside = Files.createTempFile("coderhino-outside", ".txt");
        Files.writeString(outside, "outside");
        var modelClient = new SequentialToolModelClient(List.of(
            new ModelResponse.ToolRequest("read_file", Map.of("path", outside.toAbsolutePath().toString()), "tool-1")
        ));
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .cwd(workspace)
            .toolRegistry(new ToolRegistry(List.of(new FileReadTool())))
            .eventSink(sink)
            .build();

        agent.run("read outside");

        assertTrue(sink.lastToolResult.contains("outside"));
    }

    @Test
    void embeddedDefaultsUseNoOpServicesWithoutSideEffects() {
        var agent = CoderhinoAgent.builder()
            .modelClient(new CapturingModelClient())
            .build();

        var services = agent.config().serviceRegistry();
        assertTrue(services.serverService() instanceof NoOpServerService);
        assertFalse(services.serverService().isRunning());
        assertTrue(services.analytics() instanceof NoOpAnalyticsService);
        assertTrue(services.featureFlags() instanceof NoOpFeatureFlagService);
        assertTrue(services.cronScheduler() instanceof NoOpCronScheduler);
        assertTrue(services.remoteTriggerService() instanceof NoOpRemoteTriggerService);
        assertFalse(services.commandVoice().isEnabled());
        assertTrue(services.pluginService().list().isEmpty());
        assertTrue(services.skillService().list().isEmpty());
    }

    @Test
    void unknownToolRequestReturnsToolError() {
        var modelClient = new UnknownToolModelClient();
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(new ToolRegistry(List.of()))
            .maxToolIterations(1)
            .eventSink(sink)
            .build();

        agent.run("call missing tool");

        assertTrue(sink.lastToolResult.contains("Unknown tool: missing_tool"));
    }

    @Test
    void invalidCustomToolInputReturnsClearError() {
        var modelClient = new SequentialToolModelClient(List.of(
            new ModelResponse.ToolRequest("host_echo", Map.of("value", Map.of("nested", "wrong")), "tool-1")
        ));
        var sink = new CapturingQueryEventSink();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(new ToolRegistry(List.of()))
            .addTool(new EchoTool())
            .eventSink(sink)
            .build();

        agent.run("use echo");

        assertTrue(sink.lastToolResult.contains("Invalid input for tool host_echo"));
        assertTrue(sink.lastToolResult.contains("arguments did not match expected input structure"));
    }

    @Test
    void apiKeyCanPointToFile() throws Exception {
        Path apiKeyFile = Files.createTempFile("coderhino-api-key", ".txt");
        Files.writeString(apiKeyFile, " file-backed-key \n");

        var agent = CoderhinoAgent.builder()
            .apiKey(apiKeyFile.toString())
            .build();

        assertEquals("file-backed-key", extractApiKey(agent));
    }

    @Test
    void apiKeyFileCanUseHomeDirectoryShortcut() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        Path home = Files.createTempDirectory("coderhino-home");
        Files.writeString(home.resolve(".token.txt"), " home-file-backed-key \n");

        try {
            System.setProperty("user.home", home.toString());
            var agent = CoderhinoAgent.builder()
                .apiKey("~/.token.txt")
                .build();

            assertEquals("home-file-backed-key", extractApiKey(agent));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private static String extractApiKey(CoderhinoAgent agent) throws Exception {
        Object modelClient = agent.config().modelClient();
        var apiKeyField = modelClient.getClass().getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        return (String) apiKeyField.get(modelClient);
    }

    private static final class CapturingModelClient implements ModelClient {
        private QueryRequest request;

        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            this.request = request;
            return new ModelResponse.AssistantReply("ok", new ModelResponse.Usage(1, 2));
        }
    }

    private static final class UnknownToolModelClient implements ModelClient {
        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            return new ModelResponse.ToolRequest("missing_tool", Map.of(), "tool-1");
        }
    }

    private static final class ToolThenReplyModelClient implements ModelClient {
        private int calls;

        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            if (calls++ == 0) {
                return new ModelResponse.ToolRequest("host_echo", Map.of("value", "hello"), "tool-1");
            }
            return new ModelResponse.AssistantReply("done", new ModelResponse.Usage(1, 1));
        }
    }

    private static final class SequentialToolModelClient implements ModelClient {
        private final List<ModelResponse.ToolRequest> requests;
        private int calls;

        private SequentialToolModelClient(List<ModelResponse.ToolRequest> requests) {
            this.requests = requests;
        }

        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            if (calls < requests.size()) {
                return requests.get(calls++);
            }
            return new ModelResponse.AssistantReply("done");
        }
    }

    private static final class CapturingQueryEventSink implements QueryEventSink {
        private String lastToolResult = "";
        private final java.util.ArrayList<String> toolResults = new java.util.ArrayList<>();
        private String lastError = "";
        private boolean completedCalled;

        @Override public void onTextChunk(String chunk) {}
        @Override public void onStatus(String message) {}
        @Override public void onToolCall(String toolName, String toolUseId, String argumentsJson) {}
        @Override public void onToolResult(String toolName, String toolUseId, String result) { this.lastToolResult = result; this.toolResults.add(result); }
        @Override public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {}
        @Override public void onError(String error) { this.lastError = error; }
        @Override public void onCompleted(String finalText) { this.completedCalled = true; }
    }

    private static final class EchoTool implements ToolDefinition<EchoTool.Input, String> {
        record Input(String value) {
        }

        @Override
        public String name() {
            return "host_echo";
        }

        @Override
        public String description() {
            return "Echoes host input";
        }

        @Override
        public ToolInputSchema inputSchema() {
            return new ToolInputSchema("object", Map.of("value", Map.of("type", "string")));
        }

        @Override
        public String execute(Input input, com.coderhino.tools.ToolContext context) {
            return input.value();
        }
    }
}
