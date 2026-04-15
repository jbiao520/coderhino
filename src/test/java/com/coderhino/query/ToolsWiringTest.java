package com.coderhino.query;

import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsWiringTest {

    private BootstrapState bootstrapState;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void orchestratorToolSchemasReturnsRegistryTools() {
        var registry = ToolRegistry.createDefault();
        var serviceRegistry = ServiceRegistry.createDefault();
        var orchestrator = new ToolLoopOrchestrator(
            registry,
            (state, request) -> new ModelResponse.AssistantReply("ok"),
            new PermissionChecker(),
            serviceRegistry, objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var schemas = orchestrator.toolSchemas();
        assertNotNull(schemas);
        assertEquals(registry.toSchemas(serviceRegistry.mcp()).size(), schemas.size());
    }

    @Test
    void withMessagesPreservesToolsAcrossIterations() {
        var tools = List.of(
            new ToolSchema("bash", "Run a bash command", Map.of("type", "object", "properties", Map.of()))
        );
        var request = new QueryRequest(
            List.of(new Message.UserMessage("hi")),
            "system", null, null, tools
        );

        var newMessages = List.<Message>of(new Message.UserMessage("hi"), new Message.AssistantMessage("done"));

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(),
            (state, req) -> new ModelResponse.AssistantReply("ok"),
            new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var updated = orchestrator.withMessages(request, newMessages);

        assertEquals(tools, updated.tools(), "tools should be preserved across withMessages calls");
    }

    @Test
    void withMessagesPreservesNullTools() {
        var request = new QueryRequest(
            List.of(new Message.UserMessage("hi")),
            "system", null, null, null
        );

        var newMessages = List.<Message>of(new Message.UserMessage("hi"), new Message.AssistantMessage("done"));

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(),
            (state, req) -> new ModelResponse.AssistantReply("ok"),
            new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence()
        );

        var updated = orchestrator.withMessages(request, newMessages);
        assertNull(updated.tools(), "null tools should remain null");
    }

    @Test
    void queryEngineFlowsToolsIntoOrchestrator() {
        var capturingClient = new ModelClient() {
            QueryRequest lastRequest;

            @Override
            public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
                this.lastRequest = request;
                return new ModelResponse.AssistantReply("done");
            }
        };

        var engine = new QueryEngine(
            ToolRegistry.createDefault(),
            capturingClient,
            new PermissionChecker(),
            new com.coderhino.context.ContextCollector(),
            ServiceRegistry.createDefault()
        );

        engine.execute(bootstrapState, "test prompt");

        assertNotNull(capturingClient.lastRequest.tools(), "QueryEngine should flow tools into request");
        assertFalse(capturingClient.lastRequest.tools().isEmpty(), "tools list should not be empty");
    }

    @Test
    void queryEnginePreservesAppendPromptAcrossToolLoopIterations() {
        var appendPrompt = "web formatting contract";
        var callCount = new AtomicInteger();
        var capturedRequests = new java.util.ArrayList<QueryRequest>();

        var modelClient = (ModelClient) (state, request) -> {
            capturedRequests.add(request);
            if (callCount.getAndIncrement() == 0) {
                return new ModelResponse.ToolRequest("synthetic_output", Map.of("content", "done"), "tool-1");
            }
            return new ModelResponse.AssistantReply("final answer");
        };

        var engine = new QueryEngine(
            ToolRegistry.createDefault(),
            modelClient,
            null,
            appendPrompt
        );

        engine.execute(bootstrapState, "test prompt");

        assertEquals(2, capturedRequests.size(), "tool loop should call model twice");
        assertEquals(appendPrompt, capturedRequests.get(0).appendSystemPrompt());
        assertEquals(appendPrompt, capturedRequests.get(1).appendSystemPrompt());
        assertSame(capturedRequests.get(0).tools(), capturedRequests.get(1).tools(), "tools list should be reused across turns");
        assertTrue(
            capturedRequests.get(1).messages().stream().anyMatch(message -> message instanceof Message.ToolResultMessage),
            "follow-up request should include the tool result"
        );
    }
}
