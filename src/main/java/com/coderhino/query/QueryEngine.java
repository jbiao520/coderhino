package com.coderhino.query;

import com.coderhino.context.ContextCollector;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class QueryEngine {
    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 200;
    private static final Logger log = LoggerFactory.getLogger(QueryEngine.class);

    private final ContextCollector contextCollector;
    private final ConversationHistory conversationHistory;
    private final PromptAssembler promptAssembler;
    private final ResponsePersistence responsePersistence;
    private final ToolLoopOrchestrator toolLoopOrchestrator;
    final String customSystemPrompt;
    final String appendSystemPrompt;

    public QueryEngine(ToolRegistry toolRegistry) {
        this(toolRegistry, createDefaultModelClient());
    }

    private static ModelClient createDefaultModelClient() {
        var resolver = new AgentConfigResolver();
        var config = resolver.resolve(); // may throw IllegalStateException if API key is missing
        var httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        return new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            config.getBaseUrl(),
            config.getApiKey(),
            config.getModel(),
            config.getApiType(),
            config.getContextWindow()
        );
    }

    public QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient) {
        this(toolRegistry, modelClient, new PermissionChecker(), new ContextCollector(), ServiceRegistry.createDefault());
    }

    public QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, ServiceRegistry.createDefault(), new ObjectMapper());
    }

    public QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector, ServiceRegistry serviceRegistry) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, serviceRegistry, new ObjectMapper());
    }

    public QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, String customSystemPrompt, String appendSystemPrompt) {
        this(
            toolRegistry,
            modelClient,
            new PermissionChecker(),
            new ContextCollector(),
            ServiceRegistry.createDefault(),
            new ObjectMapper(),
            DEFAULT_MAX_TOOL_ITERATIONS,
            0.0,
            null,
            customSystemPrompt,
            appendSystemPrompt
        );
    }

    public QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker,
                       ContextCollector contextCollector, ServiceRegistry serviceRegistry,
                       String customSystemPrompt, String appendSystemPrompt) {
        this(
            toolRegistry,
            modelClient,
            permissionChecker,
            contextCollector,
            serviceRegistry,
            new ObjectMapper(),
            DEFAULT_MAX_TOOL_ITERATIONS,
            0.0,
            null,
            customSystemPrompt,
            appendSystemPrompt
        );
    }

    QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector, ServiceRegistry serviceRegistry, ObjectMapper objectMapper) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, serviceRegistry, objectMapper, DEFAULT_MAX_TOOL_ITERATIONS);
    }

    QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector, ServiceRegistry serviceRegistry, ObjectMapper objectMapper, int maxToolIterations) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, serviceRegistry, objectMapper, maxToolIterations, 0.0);
    }

    QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector, ServiceRegistry serviceRegistry, ObjectMapper objectMapper, int maxToolIterations, double maxBudgetUsd) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, serviceRegistry, objectMapper, maxToolIterations, maxBudgetUsd, null);
    }

    public QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector, ServiceRegistry serviceRegistry, SubAgentContext subAgentContext) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, serviceRegistry, new ObjectMapper(), DEFAULT_MAX_TOOL_ITERATIONS, 0.0, subAgentContext);
    }

    QueryEngine(ToolRegistry toolRegistry, ModelClient modelClient, PermissionChecker permissionChecker, ContextCollector contextCollector, ServiceRegistry serviceRegistry, ObjectMapper objectMapper, int maxToolIterations, double maxBudgetUsd, SubAgentContext subAgentContext) {
        this(toolRegistry, modelClient, permissionChecker, contextCollector, serviceRegistry, objectMapper, maxToolIterations, maxBudgetUsd, subAgentContext, null, null);
    }

    QueryEngine(
        ToolRegistry toolRegistry,
        ModelClient modelClient,
        PermissionChecker permissionChecker,
        ContextCollector contextCollector,
        ServiceRegistry serviceRegistry,
        ObjectMapper objectMapper,
        int maxToolIterations,
        double maxBudgetUsd,
        SubAgentContext subAgentContext,
        String customSystemPrompt,
        String appendSystemPrompt
    ) {
        var usageAccumulator = new UsageAccumulator(serviceRegistry.costTracker());
        var stopReasonResolver = new StopReasonResolver();
        var budgetEnforcer = new BudgetEnforcer(maxBudgetUsd);
        var responsePersistence = new ResponsePersistence();
        this.contextCollector = contextCollector;
        this.conversationHistory = new ConversationHistory();
        this.promptAssembler = new PromptAssembler();
        this.responsePersistence = responsePersistence;
        this.customSystemPrompt = customSystemPrompt;
        this.appendSystemPrompt = appendSystemPrompt;
        this.toolLoopOrchestrator = new ToolLoopOrchestrator(
            toolRegistry,
            modelClient,
            permissionChecker,
            serviceRegistry,
            objectMapper,
            maxToolIterations,
            usageAccumulator,
            stopReasonResolver,
            budgetEnforcer,
            responsePersistence,
            subAgentContext
        );
    }

    public Message.AssistantMessage respond(BootstrapState bootstrapState, String userInput) {
        var result = execute(bootstrapState, userInput);
        return new Message.AssistantMessage(result.text());
    }

    public Message.AssistantMessage respond(BootstrapState bootstrapState, String userInput, String visibleUserInput) {
        var result = execute(bootstrapState, userInput, visibleUserInput, NoOpQueryEventSink.INSTANCE);
        return new Message.AssistantMessage(result.text());
    }

    public QueryResult execute(BootstrapState bootstrapState, String userInput) {
        return execute(bootstrapState, userInput, NoOpQueryEventSink.INSTANCE);
    }

    public QueryResult execute(BootstrapState bootstrapState, String userInput, QueryEventSink sink) {
        return execute(bootstrapState, userInput, userInput, sink);
    }

    public QueryResult execute(BootstrapState bootstrapState, String userInput, String visibleUserInput, QueryEventSink sink) {
        var state = bootstrapState.get();
        var sessionId = QueryLogFormatter.sessionId(state);
        var cwd = QueryLogFormatter.cwd(state);
        var currentTurn = new ConversationHistory.CurrentTurn(visibleUserInput, userInput);
        log.info(
            "Query execution started for session {} cwd={} messageCount={} userInput={}",
            sessionId,
            cwd,
            state.messages().size(),
            QueryLogFormatter.summarizeUserInput(userInput)
        );

        try {
            bootstrapState.update(current -> current.withCurrentUsage(null));
            ensureLatestUserMessage(bootstrapState, visibleUserInput);
            var assistantMessagesBeforeRun = countAssistantMessages(bootstrapState);
            var history = buildHistory(bootstrapState, currentTurn);
            var contextSnapshot = contextCollector.collect(Path.of(bootstrapState.get().cwd()));
            var assembled = promptAssembler.assemble(contextSnapshot, customSystemPrompt, appendSystemPrompt);
            var request = new QueryRequest(List.copyOf(history), assembled.systemPrompt(), customSystemPrompt, appendSystemPrompt, toolLoopOrchestrator.toolSchemas());
            var result = toolLoopOrchestrator.run(bootstrapState, request, sink);
            persistTerminalAssistantMessage(bootstrapState, result, assistantMessagesBeforeRun);
            log.info(
                "Query execution completed for session {} stopReason={} iterations={} usage={}",
                sessionId,
                result.stopReason(),
                result.iterationsUsed(),
                QueryLogFormatter.summarizeUsage(result.usage())
            );
            return result;
        } catch (RuntimeException exception) {
            log.error("Query execution failed for session {} cwd={}", sessionId, cwd, exception);
            throw exception;
        }
    }

    void ensureLatestUserMessage(BootstrapState bootstrapState, String userInput) {
        var messages = bootstrapState.get().messages();
        boolean alreadyPresent = !messages.isEmpty()
            && messages.get(messages.size() - 1) instanceof Message.UserMessage userMessage
            && userMessage.content().equals(userInput);
        if (!alreadyPresent) {
            bootstrapState.addMessage(new Message.UserMessage(userInput));
        }
    }

    ArrayList<Message> buildHistory(BootstrapState bootstrapState, ConversationHistory.CurrentTurn currentTurn) {
        return conversationHistory.build(bootstrapState, currentTurn);
    }

    private int countAssistantMessages(BootstrapState bootstrapState) {
        return (int) bootstrapState.get().messages().stream()
            .filter(Message.AssistantMessage.class::isInstance)
            .count();
    }

    private void persistTerminalAssistantMessage(BootstrapState bootstrapState, QueryResult result, int assistantMessagesBeforeRun) {
        if (result.text() == null || result.text().isBlank()) {
            return;
        }
        if (countAssistantMessages(bootstrapState) > assistantMessagesBeforeRun) {
            return;
        }
        responsePersistence.persist(bootstrapState, new Message.AssistantMessage(result.text()));
    }
}
