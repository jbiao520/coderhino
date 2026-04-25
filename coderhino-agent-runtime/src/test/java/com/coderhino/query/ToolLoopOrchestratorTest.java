package com.coderhino.query;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.mcp.McpSession;
import com.coderhino.services.mcp.McpServerDefinition;
import com.coderhino.services.mcp.McpResourceDescriptor;
import com.coderhino.services.mcp.McpToolDescriptor;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.builtin.TodoWriteTool;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolLoopOrchestratorTest {

    private BootstrapState bootstrapState;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearTodos() {
        TodoWriteTool.clearTodos();
    }

    @BeforeEach
    void setUp() {
        var appState = new AppState(
            false, "test-model", System.getProperty("user.dir"),
            true, true, PermissionMode.BYPASS,
            0.0, SessionRuntime.create(), List.of()
        );
        bootstrapState = new BootstrapState(appState);
    }

    @Test
    void budgetExceededPathEmitsSinkOnCompleted() {
        var capturingSink = new CapturingQueryEventSink();
        var modelClient = (ModelClient) (state, request) -> new ModelResponse.AssistantReply(
            "reply",
            new ModelResponse.Usage(999_999_999L, 999_999_999L)
        );

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0.001), new ResponsePersistence()
        );

        var request = new QueryRequest(List.of(), "system", null, null, null);
        var result = orchestrator.run(bootstrapState, request, capturingSink);

        assertTrue(result.isError());
        assertTrue(capturingSink.completedCalled);
        assertEquals("Query engine stopped: budget limit exceeded.", capturingSink.lastCompletedText);
    }

    @Test
    void toolIterationLimitPathEmitsSinkOnCompleted() {
        var capturingSink = new CapturingQueryEventSink();
        var modelClient = (ModelClient) (state, request) -> new ModelResponse.ToolRequest(
            "glob", Map.of("pattern", "*.txt"), "tool-use-1"
        );

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            1, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var request = new QueryRequest(List.of(), "system", null, null, null);
        var result = orchestrator.run(bootstrapState, request, capturingSink);

        assertTrue(result.isToolLimitReached());
        assertTrue(capturingSink.completedCalled);
        assertEquals("Query engine stopped after reaching the tool iteration limit.", capturingSink.lastCompletedText);
    }

    @Test
    void toolExecutionUpdatesToolUseCountsInState() {
        var modelCallCount = new AtomicInteger();
        var modelClient = (ModelClient) (state, request) -> {
            if (modelCallCount.getAndIncrement() == 0) {
                return new ModelResponse.ToolRequest(
                    "synthetic_output",
                    Map.of("content", "tool output"),
                    "tool-use-1",
                    new ModelResponse.Usage(10, 5, 3, 2)
                );
            }
            return new ModelResponse.AssistantReply(
                "final",
                new ModelResponse.Usage(4, 6, 1, 2)
            );
        };

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var request = new QueryRequest(List.of(), "system", null, null, null);
        var result = orchestrator.run(bootstrapState, request, NoOpQueryEventSink.INSTANCE);

        assertEquals("final", result.text());
        assertEquals(1, bootstrapState.get().totalToolUses());
        assertNotNull(bootstrapState.get().currentUsage());
        assertEquals(1, bootstrapState.get().currentUsage().toolUses());
        assertEquals(14, bootstrapState.get().totalInputTokens());
        assertEquals(11, bootstrapState.get().totalOutputTokens());
        assertEquals(4, bootstrapState.get().totalCacheReadTokens());
        assertEquals(4, bootstrapState.get().totalCacheWriteTokens());
        assertEquals(33, bootstrapState.get().currentUsage().contextLength());
    }

    @Test
    void toolSchemasIncludeDynamicMcpTools() throws Exception {
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("filesystem", "noop", List.of(), Map.of(), true, 30_000L));
        setDiscoveredTools(mcp, "filesystem", List.of(
            new com.coderhino.services.mcp.McpToolDescriptor(
                "read_file",
                "Read file from MCP",
                Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))),
                true,
                false
            )
        ));

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), (state, request) -> new ModelResponse.AssistantReply("done"), new PermissionChecker(),
            new ServiceRegistry(mcp, new LspClientManager(), new TaskService(Files.createTempFile("tasks", ".json"))), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var schemas = orchestrator.toolSchemas();

        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("mcp__filesystem__read_file")));
        assertTrue(schemas.stream().anyMatch(schema -> schema.name().equals("mcp")));
    }

    @Test
    void firstClassMcpToolExecutesThroughConnectionManager() throws Exception {
        var harness = McpHarness.create("filesystem", "read_file", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))), "mcp-ok");
        try {
            var serviceRegistry = new ServiceRegistry(harness.manager(), new LspClientManager(), new TaskService(Files.createTempFile("tasks", ".json")));
            var modelCallCount = new AtomicInteger();
            var modelClient = (ModelClient) (state, request) -> {
                if (modelCallCount.getAndIncrement() == 0) {
                    return new ModelResponse.ToolRequest("mcp__filesystem__read_file", Map.of("path", "README.md"), "tool-use-1");
                }
                return new ModelResponse.AssistantReply("final");
            };

            var orchestrator = new ToolLoopOrchestrator(
                ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
                serviceRegistry, objectMapper,
                10, new UsageAccumulator(), new StopReasonResolver(),
                new BudgetEnforcer(0), new ResponsePersistence()
            );

            var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

            assertEquals("final", result.text());
            assertTrue(harness.requests().stream().anyMatch(body -> body.contains("\"method\":\"tools/call\"") && body.contains("\"name\":\"read_file\"")));
        } finally {
            harness.close();
        }
    }

    @Test
    void unknownFirstClassMcpToolReturnsClearFailure() throws Exception {
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("filesystem", "noop", List.of(), Map.of(), true, 30_000L));
        setDiscoveredTools(mcp, "filesystem", List.of());

        var serviceRegistry = new ServiceRegistry(mcp, new LspClientManager(), new TaskService(Files.createTempFile("tasks", ".json")));
        var modelCallCount = new AtomicInteger();
        var modelClient = (ModelClient) (state, request) -> {
            if (modelCallCount.getAndIncrement() == 0) {
                return new ModelResponse.ToolRequest("mcp__filesystem__missing_tool", Map.of(), "tool-use-1");
            }
            return new ModelResponse.AssistantReply(request.messages().get(request.messages().size() - 1).content());
        };

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            serviceRegistry, objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

        assertTrue(result.text().contains("Tool failed: Unknown MCP tool: mcp__filesystem__missing_tool"));
    }

    @Test
    void genericMcpToolPathRemainsAvailable() throws Exception {
        var harness = McpHarness.create("filesystem", "read_file", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))), "generic-ok");
        try {
            var serviceRegistry = new ServiceRegistry(harness.manager(), new LspClientManager(), new TaskService(Files.createTempFile("tasks", ".json")));
            var modelCallCount = new AtomicInteger();
            var modelClient = (ModelClient) (state, request) -> {
                if (modelCallCount.getAndIncrement() == 0) {
                    return new ModelResponse.ToolRequest("mcp", Map.of(
                        "serverName", "filesystem",
                        "toolName", "read_file",
                        "arguments", Map.of("path", "README.md")
                    ), "tool-use-1");
                }
                return new ModelResponse.AssistantReply(request.messages().get(request.messages().size() - 1).content());
            };

            var orchestrator = new ToolLoopOrchestrator(
                ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
                serviceRegistry, objectMapper,
                10, new UsageAccumulator(), new StopReasonResolver(),
                new BudgetEnforcer(0), new ResponsePersistence()
            );

            var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

            assertTrue(result.text().contains("generic-ok"));
            assertTrue(harness.requests().stream().anyMatch(body -> body.contains("\"method\":\"tools/call\"") && body.contains("\"name\":\"read_file\"")));
        } finally {
            harness.close();
        }
    }

    @Test
    void logsToolDispatchAndCompletionBoundaries() {
        var appender = attachLogs();
        try {
            var modelCallCount = new AtomicInteger();
            var modelClient = (ModelClient) (state, request) -> {
                if (modelCallCount.getAndIncrement() == 0) {
                    return new ModelResponse.ToolRequest("synthetic_output", Map.of("content", "tool output"), "tool-use-1");
                }
                return new ModelResponse.AssistantReply("final");
            };

            var orchestrator = new ToolLoopOrchestrator(
                ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
                ServiceRegistry.createDefault(), objectMapper,
                10, new UsageAccumulator(), new StopReasonResolver(),
                new BudgetEnforcer(0), new ResponsePersistence()
            );

            var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

            assertEquals("final", result.text());
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Query iteration 1 started for session")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Tool dispatch for session")
                && event.getFormattedMessage().contains("tool=synthetic_output")
                && event.getFormattedMessage().contains("toolUseId=tool-use-1")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Tool completed for session")
                && event.getFormattedMessage().contains("tool=synthetic_output")
                && event.getFormattedMessage().contains("toolUseId=tool-use-1")));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void logsToolFailuresWithExceptionContext() {
        var appender = attachLogs();
        try {
            var modelCallCount = new AtomicInteger();
            var modelClient = (ModelClient) (state, request) -> {
                if (modelCallCount.getAndIncrement() == 0) {
                    return new ModelResponse.ToolRequest("missing_tool", Map.of(), "tool-use-err");
                }
                return new ModelResponse.AssistantReply(request.messages().get(request.messages().size() - 1).content());
            };

            var orchestrator = new ToolLoopOrchestrator(
                ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
                ServiceRegistry.createDefault(), objectMapper,
                10, new UsageAccumulator(), new StopReasonResolver(),
                new BudgetEnforcer(0), new ResponsePersistence()
            );

            var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

            assertTrue(result.text().contains("Tool failed: Unknown tool: missing_tool"));
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel().toString().equals("ERROR")
                && event.getFormattedMessage().contains("Tool failed for session")
                && event.getFormattedMessage().contains("tool=missing_tool")
                && event.getFormattedMessage().contains("toolUseId=tool-use-err")
                && event.getThrowableProxy() != null));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void malformedRegisteredToolInputReturnsBoundedToolFailure() {
        var modelCallCount = new AtomicInteger();
        var modelClient = (ModelClient) (state, request) -> {
            if (modelCallCount.getAndIncrement() == 0) {
                return new ModelResponse.ToolRequest(
                    "write_file",
                    Map.of("Scenario", "bad", "content", "hello"),
                    "tool-use-bad-input"
                );
            }
            return new ModelResponse.AssistantReply(request.messages().get(request.messages().size() - 1).content());
        };

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

        assertTrue(result.text().contains("Tool failed: Invalid input for tool write_file: arguments did not match expected input structure"));
        assertTrue(result.text().contains("unexpected field: Scenario"));
        assertFalse(result.text().contains("Unrecognized field \"Scenario\""));
    }

    @Test
    void structuredTodoWriteRequestExecutesSuccessfully() {
        var modelCallCount = new AtomicInteger();
        var modelClient = (ModelClient) (state, request) -> {
            if (modelCallCount.getAndIncrement() == 0) {
                return new ModelResponse.ToolRequest(
                    "todo_write",
                    Map.of("todos", List.of(Map.of("content", "Fix parser", "status", "in_progress", "priority", "high"))),
                    "tool-use-todo"
                );
            }
            return new ModelResponse.AssistantReply(request.messages().get(request.messages().size() - 1).content());
        };

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), NoOpQueryEventSink.INSTANCE);

        assertTrue(result.text().contains("Fix parser"));
        assertEquals(
            List.of(new TodoWriteTool.TodoItem("Fix parser", "in_progress", "high")),
            TodoWriteTool.getTodos()
        );
    }

    @Test
    void forwardsModelStreamEventsBeforeTerminalAssistantReplyWithoutDuplicatingText() {
        var capturingSink = new CapturingQueryEventSink();
        var modelClient = new StreamingAssistantModelClient();

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), capturingSink);

        assertEquals("hello", result.text());
        assertEquals(List.of("he", "llo"), capturingSink.textChunks);
        assertEquals(List.of("plan"), capturingSink.thinkingDeltas);
        assertEquals(List.of("glob|tool-1|{\"pattern\":"), capturingSink.toolInputDeltas);
        assertEquals("hello", capturingSink.lastCompletedText);
    }

    @SuppressWarnings("unchecked")
    private static void setDiscoveredTools(McpConnectionManager manager, String serverName, List<com.coderhino.services.mcp.McpToolDescriptor> tools) throws Exception {
        var field = McpConnectionManager.class.getDeclaredField("discoveredTools");
        field.setAccessible(true);
        var discoveredTools = (Map<String, List<com.coderhino.services.mcp.McpToolDescriptor>>) field.get(manager);
        discoveredTools.put(serverName, List.copyOf(tools));
    }

    @SuppressWarnings("unchecked")
    private static void setSession(McpConnectionManager manager, String serverName, McpSession session) throws Exception {
        var field = McpConnectionManager.class.getDeclaredField("sessions");
        field.setAccessible(true);
        var sessions = (Map<String, McpSession>) field.get(manager);
        sessions.put(serverName, session);
    }

    private static final class McpHarness implements AutoCloseable {
        private final McpConnectionManager manager;
        private final List<String> requests;
        private final StubMcpSession session;

        private McpHarness(McpConnectionManager manager, List<String> requests, StubMcpSession session) {
            this.manager = manager;
            this.requests = requests;
            this.session = session;
        }

        static McpHarness create(String serverName, String toolName, Map<String, Object> inputSchema, String toolResponse) throws Exception {
            var manager = new McpConnectionManager();
            manager.register(new McpServerDefinition(serverName, "noop", List.of(), Map.of(), true, 30_000L));

            var requests = new ArrayList<String>();
            var session = new StubMcpSession(toolName, toolResponse, requests);
            setSession(manager, serverName, session);
            setDiscoveredTools(manager, serverName, List.of(
                new McpToolDescriptor(toolName, "MCP tool", inputSchema, true, false)
            ));

            return new McpHarness(manager, requests, session);
        }

        McpConnectionManager manager() {
            return manager;
        }

        List<String> requests() {
            return requests;
        }

        @Override
        public void close() throws Exception {
            session.close();
        }
    }

    private static final class StubMcpSession implements McpSession {
        private final String toolName;
        private final String toolResponse;
        private final List<String> requests;
        private boolean initialized;
        private boolean closed;

        private StubMcpSession(String toolName, String toolResponse, List<String> requests) {
            this.toolName = toolName;
            this.toolResponse = toolResponse;
            this.requests = requests;
        }

        @Override
        public void initialize() {
            initialized = true;
            requests.add("{\"method\":\"initialize\"}");
        }

        @Override
        public List<McpToolDescriptor> listTools() {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"tools/list\"}");
            return List.of(new McpToolDescriptor(toolName, "MCP tool", Map.of(), true, false));
        }

        @Override
        public List<McpResourceDescriptor> listResources() {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"resources/list\"}");
            return List.of();
        }

        @Override
        public String readResource(String uri) {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"resources/read\",\"uri\":\"" + uri + "\"}");
            return uri;
        }

        @Override
        public String callTool(String toolName, com.fasterxml.jackson.databind.JsonNode arguments) {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"tools/call\",\"name\":\"" + toolName + "\",\"arguments\":" + arguments + "}");
            return toolResponse;
        }

        @Override
        public boolean ping() {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"ping\"}");
            return true;
        }

        @Override
        public void subscribeResource(String uri) {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"resources/subscribe\",\"uri\":\"" + uri + "\"}");
        }

        @Override
        public void unsubscribeResource(String uri) {
            if (!initialized) {
                initialize();
            }
            requests.add("{\"method\":\"resources/unsubscribe\",\"uri\":\"" + uri + "\"}");
        }

        @Override
        public boolean hasStartedProcess() {
            return !closed;
        }

        @Override
        public boolean isProcessAlive() {
            return !closed;
        }

        @Override
        public Long processId() {
            return 42L;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CapturingQueryEventSink implements QueryEventSink {
        boolean completedCalled = false;
        String lastCompletedText = null;
        List<String> textChunks = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        List<String> thinkingDeltas = new ArrayList<>();
        List<String> toolInputDeltas = new ArrayList<>();

        @Override public void onTextChunk(String chunk) {
            textChunks.add(chunk);
        }
        @Override public void onThinkingDelta(String thinking) {
            thinkingDeltas.add(thinking);
        }
        @Override public void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
            toolInputDeltas.add(toolName + "|" + toolUseId + "|" + partialJson);
        }
        @Override public void onStatus(String message) {
            statuses.add(message);
        }
        @Override public void onToolCall(String toolName, String toolUseId, String argumentsJson) {}
        @Override public void onToolResult(String toolName, String toolUseId, String result) {}
        @Override public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {}
        @Override public void onError(String error) {}
        @Override public void onCompleted(String finalText) {
            this.completedCalled = true;
            this.lastCompletedText = finalText;
        }
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        var logger = (Logger) LoggerFactory.getLogger(ToolLoopOrchestrator.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogs(ListAppender<ILoggingEvent> appender) {
        var logger = (Logger) LoggerFactory.getLogger(ToolLoopOrchestrator.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private static final class StreamingAssistantModelClient implements ModelClient {
        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            return new ModelResponse.AssistantReply("hello");
        }

        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request, ModelStreamEventSink streamSink) {
            streamSink.onStatus("Retrying LLM request: attempt 2 of 5 after service overloaded");
            streamSink.onThinkingDelta("plan");
            streamSink.onTextDelta("he");
            streamSink.onTextDelta("llo");
            streamSink.onToolInputDelta("glob", "tool-1", "{\"pattern\":");
            return new ModelResponse.AssistantReply("hello");
        }
    }

    @Test
    void modelStreamBridgeForwardsRetryStatusEvents() {
        var capturingSink = new CapturingQueryEventSink();
        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), new StreamingAssistantModelClient(), new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var result = orchestrator.run(bootstrapState, new QueryRequest(List.of(), "system", null, null, null), capturingSink);

        assertEquals("hello", result.text());
        assertEquals(List.of("Retrying LLM request: attempt 2 of 5 after service overloaded"), capturingSink.statuses);
    }
}
