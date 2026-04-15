package com.coderhino.query;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.coderhino.context.ContextCollector;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class QueryEngineMessageLifecycleTest {

    private BootstrapState bootstrapState;

    @BeforeEach
    void setUp() {
        var appState = new AppStateBuilder().build();
        bootstrapState = new BootstrapState(appState);
    }

    @Test
    void executeInsertsUserMessageWhenNotAlreadyPresent() {
        var engine = buildEngine("Hello from model");

        assertEquals(0, bootstrapState.get().messages().size());

        engine.execute(bootstrapState, "hi");

        var messages = bootstrapState.get().messages();
        assertEquals(2, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertEquals("hi", messages.get(0).content());
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertEquals("Hello from model", messages.get(1).content());
    }

    @Test
    void executeDoesNotDuplicateUserMessageWhenAlreadyPresent() {
        bootstrapState.addMessage(new Message.UserMessage("hi"));
        assertEquals(1, bootstrapState.get().messages().size());

        var engine = buildEngine("Response");

        engine.execute(bootstrapState, "hi");

        var messages = bootstrapState.get().messages();
        assertEquals(2, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertEquals("hi", messages.get(0).content());
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertEquals("Response", messages.get(1).content());
    }

    @Test
    void respondUsesVisibleUserMessageWhenProvided() {
        var engine = buildEngine("Visible response");

        engine.respond(bootstrapState, "expanded hidden prompt", "/opsx-propose fix-refresh");

        var messages = bootstrapState.get().messages();
        assertEquals(2, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertEquals("/opsx-propose fix-refresh", messages.get(0).content());
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertEquals("Visible response", messages.get(1).content());
    }

    @Test
    void executeProducesExactlyOneAssistantMessage() {
        var engine = buildEngine("Only one");

        engine.execute(bootstrapState, "prompt");

        var messages = bootstrapState.get().messages();
        long assistantCount = messages.stream()
            .filter(m -> m instanceof Message.AssistantMessage)
            .count();
        assertEquals(1, assistantCount, "Should have exactly one assistant message");
    }

    @Test
    void executeWithPreAddedUserMessageProducesExactlyOneAssistantMessage() {
        bootstrapState.addMessage(new Message.UserMessage("prompt"));
        var engine = buildEngine("Single assistant");

        engine.execute(bootstrapState, "prompt");

        var messages = bootstrapState.get().messages();
        long assistantCount = messages.stream()
            .filter(m -> m instanceof Message.AssistantMessage)
            .count();
        assertEquals(1, assistantCount, "Should have exactly one assistant message even when user message was pre-added");
    }

    @Test
    void multiTurnExecutionKeepsCompleteHistory() {
        var engine = buildEngine("First reply");

        engine.execute(bootstrapState, "turn 1");
        assertEquals(2, bootstrapState.get().messages().size());

        engine = buildEngine("Second reply");
        engine.execute(bootstrapState, "turn 2");

        var messages = bootstrapState.get().messages();
        assertEquals(4, messages.size());
        assertEquals("turn 1", messages.get(0).content());
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertEquals("turn 2", messages.get(2).content());
        assertInstanceOf(Message.AssistantMessage.class, messages.get(3));
    }

    @Test
    void executeLogsRunStartAndTerminalOutcome() {
        var appender = attachLogs();
        try {
            var engine = buildEngine("Logged reply");

            engine.execute(bootstrapState, "hi");

            var sessionId = bootstrapState.get().sessionRuntime().sessionId().toString();
            assertEquals(1, appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("Query execution started for session " + sessionId))
                .count());
            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("userInput=len=2")));
            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Query execution completed for session " + sessionId)
                    && event.getFormattedMessage().contains("stopReason=END_TURN")
                    && event.getFormattedMessage().contains("iterations=1")));
        } finally {
            detachLogs(appender);
        }
    }

    private QueryEngine buildEngine(String replyText) {
        var modelClient = (ModelClient) (state, request) -> new ModelResponse.AssistantReply(replyText);
        return new QueryEngine(
            new ToolRegistry(List.of()),
            modelClient,
            new PermissionChecker(),
            new ContextCollector(),
            ServiceRegistry.createDefault()
        );
    }

    private static class AppStateBuilder {
        private boolean verbose = false;
        private String model = "test-model";
        private String cwd = System.getProperty("user.dir");
        private boolean interactive = true;
        private boolean running = true;
        private PermissionMode permissionMode = PermissionMode.BYPASS;
        private double totalCostUsd = 0.0;
        private SessionRuntime sessionRuntime = SessionRuntime.create();
        private List<Message> messages = new ArrayList<>();

        com.coderhino.state.AppState build() {
            return new com.coderhino.state.AppState(
                verbose, model, cwd, interactive, running, permissionMode,
                totalCostUsd, sessionRuntime, messages
            );
        }
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        var logger = (Logger) LoggerFactory.getLogger(QueryEngine.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogs(ListAppender<ILoggingEvent> appender) {
        var logger = (Logger) LoggerFactory.getLogger(QueryEngine.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
