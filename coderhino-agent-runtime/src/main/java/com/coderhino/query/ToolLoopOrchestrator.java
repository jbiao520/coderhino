package com.coderhino.query;

import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.mcp.McpToolName;
import com.coderhino.services.summary.BashCommandFileParser;
import com.coderhino.services.summary.FileChange;
import com.coderhino.services.summary.FileChangeListener;
import com.coderhino.services.summary.FileOperation;
import com.coderhino.state.BootstrapState;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.runtime.ToolAgentExecutor;
import com.coderhino.tools.runtime.ToolBootstrapState;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class ToolLoopOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ToolLoopOrchestrator.class);
    private final ToolRegistry toolRegistry;
    private final ModelClient modelClient;
    private final PermissionChecker permissionChecker;
    private final ServiceRegistry serviceRegistry;
    private final ObjectMapper objectMapper;
    private final int maxToolIterations;
    private final UsageAccumulator usageAccumulator;
    private final StopReasonResolver stopReasonResolver;
    private final BudgetEnforcer budgetEnforcer;
    private final SubAgentContext subAgentContext;
    private final FileChangeListener fileChangeListener;
    private final ToolCommandRegistry commandRegistry;

    ToolLoopOrchestrator(
        ToolRegistry toolRegistry,
        ModelClient modelClient,
        PermissionChecker permissionChecker,
        ServiceRegistry serviceRegistry,
        ObjectMapper objectMapper,
        int maxToolIterations,
        UsageAccumulator usageAccumulator,
        StopReasonResolver stopReasonResolver,
        BudgetEnforcer budgetEnforcer,
        ResponsePersistence responsePersistence
    ) {
        this(toolRegistry, modelClient, permissionChecker, serviceRegistry, objectMapper,
            maxToolIterations, usageAccumulator, stopReasonResolver, budgetEnforcer, responsePersistence, null, NoOpFileChangeListener.INSTANCE, NoOpToolCommandRegistry.INSTANCE);
    }

    ToolLoopOrchestrator(
        ToolRegistry toolRegistry,
        ModelClient modelClient,
        PermissionChecker permissionChecker,
        ServiceRegistry serviceRegistry,
        ObjectMapper objectMapper,
        int maxToolIterations,
        UsageAccumulator usageAccumulator,
        StopReasonResolver stopReasonResolver,
        BudgetEnforcer budgetEnforcer,
        ResponsePersistence responsePersistence,
        SubAgentContext subAgentContext
    ) {
        this(toolRegistry, modelClient, permissionChecker, serviceRegistry, objectMapper,
            maxToolIterations, usageAccumulator, stopReasonResolver, budgetEnforcer, responsePersistence, subAgentContext, NoOpFileChangeListener.INSTANCE, NoOpToolCommandRegistry.INSTANCE);
    }

    ToolLoopOrchestrator(
        ToolRegistry toolRegistry,
        ModelClient modelClient,
        PermissionChecker permissionChecker,
        ServiceRegistry serviceRegistry,
        ObjectMapper objectMapper,
        int maxToolIterations,
        UsageAccumulator usageAccumulator,
        StopReasonResolver stopReasonResolver,
        BudgetEnforcer budgetEnforcer,
        ResponsePersistence responsePersistence,
        SubAgentContext subAgentContext,
        FileChangeListener fileChangeListener
    ) {
        this(toolRegistry, modelClient, permissionChecker, serviceRegistry, objectMapper,
            maxToolIterations, usageAccumulator, stopReasonResolver, budgetEnforcer, responsePersistence, subAgentContext, fileChangeListener, NoOpToolCommandRegistry.INSTANCE);
    }

    ToolLoopOrchestrator(
        ToolRegistry toolRegistry,
        ModelClient modelClient,
        PermissionChecker permissionChecker,
        ServiceRegistry serviceRegistry,
        ObjectMapper objectMapper,
        int maxToolIterations,
        UsageAccumulator usageAccumulator,
        StopReasonResolver stopReasonResolver,
        BudgetEnforcer budgetEnforcer,
        ResponsePersistence responsePersistence,
        SubAgentContext subAgentContext,
        FileChangeListener fileChangeListener,
        ToolCommandRegistry commandRegistry
    ) {
        this.toolRegistry = toolRegistry;
        this.modelClient = modelClient;
        this.permissionChecker = permissionChecker;
        this.serviceRegistry = serviceRegistry;
        this.objectMapper = objectMapper;
        this.maxToolIterations = maxToolIterations;
        this.usageAccumulator = usageAccumulator;
        this.stopReasonResolver = stopReasonResolver;
        this.budgetEnforcer = budgetEnforcer;
        this.subAgentContext = subAgentContext;
        this.fileChangeListener = fileChangeListener;
        this.commandRegistry = commandRegistry == null ? NoOpToolCommandRegistry.INSTANCE : commandRegistry;
    }

    QueryResult run(BootstrapState bootstrapState, QueryRequest request) {
        return run(bootstrapState, request, NoOpQueryEventSink.INSTANCE);
    }

    java.util.List<ToolSchema> toolSchemas() {
        return toolRegistry.toSchemas(serviceRegistry.mcp());
    }

    QueryResult run(BootstrapState bootstrapState, QueryRequest request, QueryEventSink sink) {
        var history = new java.util.ArrayList<>(request.messages());
        var currentRequest = request;
        var permissionMode = bootstrapState.get().permissionMode();
        var sessionId = QueryLogFormatter.sessionId(bootstrapState);

        for (int iteration = 0; iteration < maxToolIterations; iteration++) {
            var iterationNumber = iteration + 1;
            log.info(
                "Query iteration {} started for session {} messageCount={} toolSchemaCount={}",
                iterationNumber,
                sessionId,
                currentRequest.messages().size(),
                currentRequest.tools() == null ? 0 : currentRequest.tools().size()
            );
            ModelResponse response;
            var streamBridge = new QuerySinkModelStreamBridge(sink);
            try {
                response = modelClient.complete(bootstrapState, currentRequest, streamBridge);
            } catch (Exception e) {
                log.error("Model completion failed for session {} at iteration {}", sessionId, iterationNumber, e);
                sink.onError(e.getMessage());
                return stopReasonResolver.resolveError(e.getMessage(), iterationNumber, usageAccumulator.total());
            }

            usageAccumulator.add(response);
            usageAccumulator.setCurrentUsage(bootstrapState);
            usageAccumulator.applyToState(bootstrapState);
            sink.onUsage(usageAccumulator.total().inputTokens(), usageAccumulator.total().outputTokens(), usageAccumulator.total().cacheCreationTokens(), usageAccumulator.total().cacheReadTokens());

            if (response instanceof ModelResponse.ModelError modelError) {
                log.error(
                    "Model completion returned error for session {} at iteration {} error={}",
                    sessionId,
                    iterationNumber,
                    modelError.message()
                );
                sink.onError(modelError.message());
                return stopReasonResolver.resolveError(modelError.message(), iterationNumber, usageAccumulator.total());
            }

            if (budgetEnforcer.isBudgetExceeded(usageAccumulator)) {
                log.info(
                    "Query budget exceeded for session {} at iteration {} usage={}",
                    sessionId,
                    iterationNumber,
                    QueryLogFormatter.summarizeUsage(usageAccumulator.total())
                );
                var result = stopReasonResolver.resolveBudgetExceeded(iterationNumber, usageAccumulator.total());
                sink.onCompleted(result.text());
                return result;
            }

            if (response instanceof ModelResponse.AssistantReply assistantReply) {
                log.info(
                    "Assistant reply completed for session {} at iteration {} reply={}",
                    sessionId,
                    iterationNumber,
                    QueryLogFormatter.summarizeResult(assistantReply.text())
                );
                if (!streamBridge.streamedAssistantText()) {
                    sink.onTextChunk(assistantReply.text());
                }
                var assistantMessage = new Message.AssistantMessage(assistantReply.text());
                history.add(assistantMessage);
                currentRequest = withMessages(currentRequest, history);
                var result = stopReasonResolver.resolveEndTurn(assistantReply.text(), iterationNumber, usageAccumulator.total());
                sink.onCompleted(assistantReply.text());
                return result;
            }

            if (response instanceof ModelResponse.ToolRequest toolRequest) {
                log.info(
                    "Tool dispatch for session {} iteration={} tool={} toolUseId={} arguments={}",
                    sessionId,
                    iterationNumber,
                    toolRequest.toolName(),
                    toolRequest.toolUseId(),
                    QueryLogFormatter.summarizeArguments(toolRequest.arguments())
                );
                var assistantMessageId = UUID.randomUUID().toString();
                var argumentsJson = serializeToolArguments(toolRequest.arguments());
                var assistantToolUseMessage = new Message.AssistantToolUseMessage(
                    argumentsJson,
                    toolRequest.toolName(),
                    toolRequest.toolUseId(),
                    assistantMessageId
                );
                history.add(assistantToolUseMessage);
                currentRequest = withMessages(currentRequest, history);
                sink.onStatus("Running tool: " + toolRequest.toolName());
                sink.onToolCall(toolRequest.toolName(), toolRequest.toolUseId(), argumentsJson);
                usageAccumulator.recordToolUse();
                usageAccumulator.setCurrentUsage(bootstrapState);
                usageAccumulator.applyToState(bootstrapState);
                var toolResult = maybeHandleInteractiveQuestion(sink, toolRequest, assistantMessageId);
                if (toolResult == null) {
                    toolResult = executeToolRequest(bootstrapState, toolRequest, assistantMessageId, permissionMode);
                }
                history.add(toolResult);
                currentRequest = withMessages(currentRequest, history);
                sink.onToolResult(toolRequest.toolName(), toolRequest.toolUseId(), toolResult.content());
            }
        }

        log.info(
            "Tool iteration limit reached for session {} limit={} usage={}",
            sessionId,
            maxToolIterations,
            QueryLogFormatter.summarizeUsage(usageAccumulator.total())
        );
        var result = stopReasonResolver.resolveToolLimit(maxToolIterations, usageAccumulator.total());
        sink.onCompleted(result.text());
        return result;
    }

    private static final class QuerySinkModelStreamBridge implements ModelStreamEventSink {
        private final QueryEventSink sink;
        private boolean streamedAssistantText;

        private QuerySinkModelStreamBridge(QueryEventSink sink) {
            this.sink = sink;
        }

        @Override
        public void onTextDelta(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            streamedAssistantText = true;
            sink.onTextChunk(text);
        }

        @Override
        public void onStatus(String message) {
            sink.onStatus(message);
        }

        @Override
        public void onThinkingDelta(String thinking) {
            sink.onThinkingDelta(thinking);
        }

        @Override
        public void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
            sink.onToolInputDelta(toolName, toolUseId, partialJson);
        }

        @Override
        public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
            sink.onUsage(inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens);
        }

        private boolean streamedAssistantText() {
            return streamedAssistantText;
        }
    }

    private Message.ToolResultMessage executeToolRequest(
        BootstrapState bootstrapState,
        ModelResponse.ToolRequest toolRequest,
        String sourceAssistantMessageId,
        PermissionMode permissionMode
    ) {
        var toolContext = new ToolContext(
            new BootstrapStateAdapter(bootstrapState),
            permissionMode,
            serviceRegistry,
            subAgentContext,
            commandRegistry,
            new QueryEngineToolAgentExecutor(bootstrapState, permissionMode)
        );
        var sessionId = QueryLogFormatter.sessionId(bootstrapState);

        try {
            var tool = toolRegistry.find(toolRequest.toolName()).orElse(null);
            if (tool == null && McpToolName.parse(toolRequest.toolName()).isPresent()) {
                var result = executeMcpToolRequest(toolRequest.arguments(), toolRequest.toolName());
                log.info(
                    "Tool completed for session {} tool={} toolUseId={} result={}",
                    sessionId,
                    toolRequest.toolName(),
                    toolRequest.toolUseId(),
                    QueryLogFormatter.summarizeResult(result)
                );
                return new Message.ToolResultMessage(String.valueOf(result), toolRequest.toolName(), toolRequest.toolUseId(), sourceAssistantMessageId);
            }
            if (tool == null) {
                throw new IllegalArgumentException("Unknown tool: " + toolRequest.toolName());
            }

            var pathBefore = extractPath(toolRequest.toolName(), toolRequest.arguments(), toolContext);
            var existedBefore = pathBefore != null && Files.exists(pathBefore);
            Object result = executeRegisteredTool(tool, toolRequest.arguments(), toolContext, permissionMode);
            notifyFileChange(toolRequest.toolName(), toolRequest.arguments(), toolContext, pathBefore, existedBefore);
            var resultText = String.valueOf(result);
            log.info(
                "Tool completed for session {} tool={} toolUseId={} result={}",
                sessionId,
                toolRequest.toolName(),
                toolRequest.toolUseId(),
                QueryLogFormatter.summarizeResult(resultText)
            );
            return new Message.ToolResultMessage(resultText, toolRequest.toolName(), toolRequest.toolUseId(), sourceAssistantMessageId);
        } catch (Exception exception) {
            log.error(
                "Tool failed for session {} tool={} toolUseId={}",
                sessionId,
                toolRequest.toolName(),
                toolRequest.toolUseId(),
                exception
            );
            return new Message.ToolResultMessage("Tool failed: %s".formatted(exception.getMessage()), toolRequest.toolName(), toolRequest.toolUseId(), sourceAssistantMessageId);
        }
    }

    private Message.ToolResultMessage maybeHandleInteractiveQuestion(QueryEventSink sink,
                                                                     ModelResponse.ToolRequest toolRequest,
                                                                     String sourceAssistantMessageId) {
        if (!Objects.equals("ask_user_question", toolRequest.toolName())) {
            return null;
        }
        var question = stringArgument(toolRequest.arguments(), "question");
        if (question == null || question.isBlank()) {
            return null;
        }
        var answer = sink.onAskUserQuestion(toolRequest.toolUseId(), question, stringListArgument(toolRequest.arguments(), "choices"));
        if (answer == null) {
            answer = "";
        }
        return new Message.ToolResultMessage(answer, toolRequest.toolName(), toolRequest.toolUseId(), sourceAssistantMessageId);
    }

    private String stringArgument(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        var value = arguments.get(key);
        return value instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListArgument(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return List.of();
        }
        var value = arguments.get(key);
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }

    private String executeMcpToolRequest(Map<String, Object> arguments, String toolName) throws Exception {
        var resolved = serviceRegistry.mcp().resolveTool(toolName)
            .orElseThrow(() -> new IllegalArgumentException("Unknown MCP tool: " + toolName));
        var argumentsNode = arguments == null
            ? objectMapper.createObjectNode()
            : objectMapper.valueToTree(arguments);
        return serviceRegistry.mcp().callTool(resolved.serverName(), resolved.toolName(), argumentsNode)
            .orElseThrow(() -> new IllegalArgumentException("MCP server not found for tool: " + toolName));
    }

    private Object executeRegisteredTool(ToolDefinition<?, ?> tool, Map<String, Object> arguments, ToolContext toolContext, PermissionMode permissionMode) throws Exception {
        return executeRegisteredToolUnchecked(tool, materializeInput(tool, arguments), toolContext, permissionMode);
    }

    @SuppressWarnings("unchecked")
    private <I, O> O executeRegisteredToolUnchecked(ToolDefinition<?, ?> tool, Object input, ToolContext toolContext, PermissionMode permissionMode) throws Exception {
        var typedTool = (ToolDefinition<I, O>) tool;
        var typedInput = (I) input;
        var validation = typedTool.validate(typedInput, toolContext);
        return (O) enforceAndExecute(validation, permissionMode, () -> typedTool.execute(typedInput, toolContext));
    }

    private Object materializeInput(ToolDefinition<?, ?> tool, Map<String, Object> arguments) {
        for (Class<?> nestedClass : tool.getClass().getDeclaredClasses()) {
            if (nestedClass.isRecord() && nestedClass.getSimpleName().equals("Input")) {
                try {
                    return objectMapper.convertValue(arguments, nestedClass);
                } catch (IllegalArgumentException exception) {
                    throw invalidToolInput(tool.name(), exception);
                }
            }
        }
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        throw new IllegalArgumentException("Unsupported tool input type for " + tool.name());
    }

    private IllegalArgumentException invalidToolInput(String toolName, IllegalArgumentException exception) {
        var message = "Invalid input for tool %s: arguments did not match expected input structure".formatted(toolName);
        var cause = exception.getCause();
        if (cause instanceof UnrecognizedPropertyException unrecognized && unrecognized.getPropertyName() != null) {
            message += " (unexpected field: %s)".formatted(unrecognized.getPropertyName());
        }
        return new IllegalArgumentException(message, exception);
    }

    private String serializeToolArguments(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception exception) {
            return String.valueOf(arguments);
        }
    }

    private Object enforceAndExecute(PermissionResult permissionResult, PermissionMode permissionMode, CheckedSupplier supplier) throws Exception {
        var resolved = permissionChecker.resolve(permissionMode, permissionResult);
        if (resolved instanceof PermissionResult.Deny deny) {
            throw new IllegalStateException(deny.reason());
        }
        if (resolved instanceof PermissionResult.Ask ask) {
            throw new IllegalStateException(ask.reason());
        }
        return supplier.get();
    }

    QueryRequest withMessages(QueryRequest request, List<Message> messages) {
        return new QueryRequest(List.copyOf(messages), request.systemPrompt(), request.customSystemPrompt(), request.appendSystemPrompt(), request.tools());
    }

    private Path extractPath(String toolName, Map<String, Object> arguments, ToolContext toolContext) {
        if (!"write_file".equals(toolName) && !"edit_file".equals(toolName)) return null;
        return extractPath(arguments, toolContext);
    }

    private void notifyFileChange(String toolName, Map<String, Object> arguments, ToolContext toolContext, Path resolvedPath, boolean existedBefore) {
        var sessionUuid = toolContext.bootstrapState().sessionId();

        switch (toolName) {
            case "write_file" -> {
                if (resolvedPath != null) {
                    var operation = existedBefore ? FileOperation.MODIFIED : FileOperation.CREATED;
                    fileChangeListener.onFileChange(sessionUuid, new FileChange(resolvedPath, operation, java.time.Instant.now(), toolName));
                }
            }
            case "edit_file" -> {
                if (resolvedPath != null) {
                    fileChangeListener.onFileChange(sessionUuid, new FileChange(resolvedPath, FileOperation.MODIFIED, java.time.Instant.now(), toolName));
                }
            }
            case "bash" -> {
                var command = arguments != null ? (String) arguments.get("command") : null;
                if (command != null) {
                    var cwd = Path.of(toolContext.bootstrapState().cwd());
                    for (var parsed : BashCommandFileParser.parse(command, cwd)) {
                        fileChangeListener.onFileChange(sessionUuid, parsed);
                    }
                }
            }
            default -> {}
        }
    }

    private Path extractPath(Map<String, Object> arguments, ToolContext toolContext) {
        if (arguments == null) return null;
        var rawPath = (String) arguments.get("path");
        if (rawPath == null || rawPath.isBlank()) return null;
        var path = Path.of(rawPath);
        if (path.isAbsolute()) return path.normalize();
        return Path.of(toolContext.bootstrapState().cwd()).resolve(path).normalize();
    }

    private final class QueryEngineToolAgentExecutor implements ToolAgentExecutor {
        private final BootstrapState bootstrapState;
        private final PermissionMode fallbackPermissionMode;

        private QueryEngineToolAgentExecutor(BootstrapState bootstrapState, PermissionMode fallbackPermissionMode) {
            this.bootstrapState = bootstrapState;
            this.fallbackPermissionMode = fallbackPermissionMode;
        }

        @Override
        public ToolAgentExecutor.SyncResult executeSync(ToolAgentExecutor.Request request) {
            var subAgentState = new BootstrapState(bootstrapState.get()
                .withPermissionMode(request.subAgentContext() != null
                    ? request.subAgentContext().permissionMode()
                    : fallbackPermissionMode));
            var engine = new QueryEngine(
                toolRegistry,
                modelClient,
                permissionChecker,
                new com.coderhino.context.ContextCollector(),
                serviceRegistry,
                request.subAgentContext(),
                commandRegistry
            );
            var result = engine.execute(subAgentState, request.prompt());
            return new ToolAgentExecutor.SyncResult(result.text(), result.stopReason().name().toLowerCase());
        }

        @Override
        public ToolAgentExecutor.AsyncResult executeAsync(ToolAgentExecutor.Request request) {
            var task = serviceRegistry.tasks().submit(request.description(), () -> executeSync(request).text());
            return new ToolAgentExecutor.AsyncResult(task.id().toString(), request.description(), task.status());
        }
    }

    private enum NoOpToolCommandRegistry implements ToolCommandRegistry {
        INSTANCE;

        @Override
        public java.util.Optional<com.coderhino.tools.runtime.ToolCommand> find(String name) {
            return java.util.Optional.empty();
        }
    }

    private record BootstrapStateAdapter(BootstrapState delegate) implements ToolBootstrapState {
        @Override
        public String cwd() {
            return delegate.get().cwd();
        }

        @Override
        public UUID sessionId() {
            return delegate.get().sessionRuntime().sessionId();
        }

        @Override
        public void updatePermissionMode(PermissionMode permissionMode) {
            delegate.update(current -> current.withPermissionMode(permissionMode));
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier {
        Object get() throws Exception;
    }
}
