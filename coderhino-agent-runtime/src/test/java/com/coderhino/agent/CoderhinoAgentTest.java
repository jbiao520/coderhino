package com.coderhino.agent;

import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryEventSink;
import com.coderhino.query.QueryRequest;
import com.coderhino.server.NoOpServerService;
import com.coderhino.services.analytics.NoOpAnalyticsService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;
import com.coderhino.services.cron.NoOpCronScheduler;
import com.coderhino.services.trigger.NoOpRemoteTriggerService;
import com.coderhino.state.BootstrapState;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.ToolInputSchema;
import org.junit.jupiter.api.Test;

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
        assertTrue(toolNames.contains("grep"));
        assertFalse(toolNames.contains("bash"));
        assertFalse(toolNames.contains("write_file"));
        assertFalse(toolNames.contains("edit_file"));
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

    private static final class CapturingQueryEventSink implements QueryEventSink {
        private String lastToolResult = "";

        @Override public void onTextChunk(String chunk) {}
        @Override public void onStatus(String message) {}
        @Override public void onToolCall(String toolName, String toolUseId, String argumentsJson) {}
        @Override public void onToolResult(String toolName, String toolUseId, String result) { this.lastToolResult = result; }
        @Override public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {}
        @Override public void onError(String error) {}
        @Override public void onCompleted(String finalText) {}
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
