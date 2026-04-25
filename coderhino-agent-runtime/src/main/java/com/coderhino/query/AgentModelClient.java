package com.coderhino.query;

import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.types.Message;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class AgentModelClient implements ModelClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(360);
    private static final int MAX_ATTEMPTS = 5;
    private static final long DEFAULT_CONTEXT_WINDOW = 128000L;
    private static final Logger log = LoggerFactory.getLogger(AgentModelClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long contextWindow;

    public AgentModelClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        String baseUrl,
        String apiKey,
        String model,
        ProviderApiType apiType,
        long contextWindow
    ) {
        this(httpClient, objectMapper, baseUrl, apiKey, model, contextWindow);
    }

    public AgentModelClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey, String model) {
        this(httpClient, objectMapper, baseUrl, apiKey, model, DEFAULT_CONTEXT_WINDOW);
    }

    public AgentModelClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey, String model, long contextWindow) {
        if (httpClient == null) throw new IllegalArgumentException("httpClient must not be null");
        if (objectMapper == null) throw new IllegalArgumentException("objectMapper must not be null");
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl must not be null or blank");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey must not be null or blank");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be null or blank");
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.contextWindow = contextWindow > 0 ? contextWindow : DEFAULT_CONTEXT_WINDOW;
    }

    @Override
    public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
        return complete(bootstrapState, request, NoOpModelStreamEventSink.INSTANCE);
    }

    @Override
    public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request, ModelStreamEventSink streamSink) {
        Exception lastException = null;
        String lastErrorBody = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                var streamPayload = buildPayload(request, true);
                recordRawHistory(bootstrapState, "request", streamPayload);
                var streamResponse = sendStreamingRequest(streamPayload);
                if (streamResponse.statusCode() >= 400) {
                    var errorBody = streamResponse.body()
                        .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
                    recordRawHistory(bootstrapState, "response", errorBody);
                    lastErrorBody = errorBody;
                    var retryReason = retryReasonForStatus(streamResponse.statusCode(), errorBody);
                    if (retryReason != null && attempt < MAX_ATTEMPTS) {
                        emitRetryStatus(streamSink, attempt + 1, retryReason);
                        log.warn(
                            "Anthropic request returned retryable status {} on attempt {} of {}. Retrying.",
                            streamResponse.statusCode(),
                            attempt,
                            MAX_ATTEMPTS
                        );
                        continue;
                    }
                    log.error(
                        "Anthropic request returned error status {} on attempt {} of {} body={}",
                        streamResponse.statusCode(),
                        attempt,
                        MAX_ATTEMPTS,
                        QueryLogFormatter.summarizeContent(errorBody)
                    );
                    return new ModelResponse.AssistantReply(
                        "Anthropic API error (%d): %s".formatted(streamResponse.statusCode(), errorBody)
                    );
                }

                try {
                    return processStreamLines(bootstrapState, streamResponse.body(), streamSink);
                } catch (Exception streamException) {
                    log.warn(
                        "Anthropic streaming response processing failed on attempt {} of {}. Falling back to non-streaming request.",
                        attempt,
                        MAX_ATTEMPTS,
                        streamException
                    );
                    var fallbackPayload = buildPayload(request, false);
                    recordRawHistory(bootstrapState, "request", fallbackPayload);
                    var fallbackResponse = sendRequest(fallbackPayload);
                    recordRawHistory(bootstrapState, "response", fallbackResponse.body());
                    if (fallbackResponse.statusCode() >= 400) {
                        log.error(
                            "Anthropic fallback request returned error status {} on attempt {} of {} body={}",
                            fallbackResponse.statusCode(),
                            attempt,
                            MAX_ATTEMPTS,
                            QueryLogFormatter.summarizeContent(fallbackResponse.body())
                        );
                        return new ModelResponse.AssistantReply(
                            "Anthropic API error (%d): %s".formatted(fallbackResponse.statusCode(), fallbackResponse.body())
                        );
                    }
                    return parseResponseBody(fallbackResponse.body());
                }
            } catch (Exception exception) {
                lastException = exception;
                var retryReason = retryReasonForException(exception);
                if (retryReason != null && attempt < MAX_ATTEMPTS) {
                    emitRetryStatus(streamSink, attempt + 1, retryReason);
                    log.warn("Anthropic request attempt {} of {} failed. Retrying.", attempt, MAX_ATTEMPTS, exception);
                } else if (attempt < MAX_ATTEMPTS) {
                    log.warn("Anthropic request attempt {} of {} failed.", attempt, MAX_ATTEMPTS, exception);
                }
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
            }
        }

        if (lastException != null) {
            log.error("Anthropic request failed after {} attempts", MAX_ATTEMPTS, lastException);
            return new ModelResponse.AssistantReply("Anthropic request failed: %s".formatted(lastException.getMessage()));
        }
        if (lastErrorBody != null) {
            log.error(
                "Anthropic request failed after {} attempts body={}",
                MAX_ATTEMPTS,
                QueryLogFormatter.summarizeContent(lastErrorBody)
            );
            return new ModelResponse.AssistantReply("Anthropic request failed: %s".formatted(lastErrorBody));
        }
        return new ModelResponse.AssistantReply("Anthropic request failed.");
    }

    private HttpResponse<Stream<String>> sendStreamingRequest(Map<String, Object> payload) throws Exception {
        var httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .timeout(REQUEST_TIMEOUT)
            .header("content-type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
    }

    ModelResponse processStreamLines(BootstrapState bootstrapState, Stream<String> lines) throws Exception {
        return processStreamLines(bootstrapState, lines, NoOpModelStreamEventSink.INSTANCE);
    }

    ModelResponse processStreamLines(BootstrapState bootstrapState, Stream<String> lines, ModelStreamEventSink streamSink) throws Exception {
        var rawStream = new StringBuilder();
        var state = new StreamingParseState();
        String currentEvent = null;
        var currentDataParts = new ArrayList<String>();
        boolean streamComplete = false;

        try {
            for (String line : (Iterable<String>) lines::iterator) {
                if (rawStream.length() > 0) {
                    rawStream.append('\n');
                }
                rawStream.append(line);
                if (line.startsWith("event:")) {
                    currentEvent = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    currentDataParts.add(line.substring("data:".length()).trim());
                } else if (line.isBlank()) {
                    if (!currentDataParts.isEmpty()) {
                        streamComplete = processStreamEvent(currentEvent, currentDataParts, state, streamSink);
                        currentEvent = null;
                        currentDataParts.clear();
                        if (streamComplete) {
                            break;
                        }
                    }
                }
            }

            if (!streamComplete && !currentDataParts.isEmpty()) {
                processStreamEvent(currentEvent, currentDataParts, state, streamSink);
            }
        } finally {
            recordRawHistory(bootstrapState, "response", rawStream.toString());
        }

        return toModelResponse(state);
    }

    private HttpResponse<String> sendRequest(Map<String, Object> payload) throws Exception {
        var httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .timeout(REQUEST_TIMEOUT)
            .header("content-type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    ModelResponse parseResponseBody(String body) throws Exception {
        var root = objectMapper.readTree(body);
        var usage = extractUsage(root);
        var toolRequest = extractToolRequest(root);
        if (toolRequest != null) {
            return new ModelResponse.ToolRequest(toolRequest.toolName(), toolRequest.arguments(), toolRequest.toolUseId(), usage);
        }
        return new ModelResponse.AssistantReply(extractText(root), usage);
    }

    ModelResponse parseStreamBody(String body) throws Exception {
        return processStreamLines(null, Stream.of(body.split("\\R", -1)), NoOpModelStreamEventSink.INSTANCE);
    }

    private boolean processStreamEvent(String currentEvent, List<String> currentDataParts, StreamingParseState state, ModelStreamEventSink streamSink)
        throws Exception {
        if (currentDataParts.isEmpty()) {
            return false;
        }

        var data = String.join("\n", currentDataParts);
        if ("[DONE]".equals(data)) {
            return true;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (JsonEOFException exception) {
            log.debug(
                "Skipping incomplete Anthropic SSE event event={} data={}",
                currentEvent,
                QueryLogFormatter.summarizeContent(data),
                exception
            );
            return false;
        }

        var eventName = currentEvent != null ? currentEvent : node.path("type").asText("");
        switch (eventName) {
            case "message_start" -> {
                var msgNode = node.path("message");
                var usageNode = msgNode.isObject() ? msgNode.path("usage") : node.path("usage");
                if (usageNode.isObject()) {
                    if (usageNode.has("input_tokens")) {
                        state.inputTokens = usageNode.path("input_tokens").asLong(0);
                        state.sawInputTokens = true;
                        streamSink.onUsage(state.inputTokens, state.outputTokens, state.cacheCreationTokens, state.cacheReadTokens);
                    }
                    if (usageNode.has("cache_creation_input_tokens")) {
                        state.cacheCreationTokens = usageNode.path("cache_creation_input_tokens").asLong(0);
                        state.sawCacheCreationTokens = true;
                        streamSink.onUsage(state.inputTokens, state.outputTokens, state.cacheCreationTokens, state.cacheReadTokens);
                    }
                    if (usageNode.has("cache_read_input_tokens")) {
                        state.cacheReadTokens = usageNode.path("cache_read_input_tokens").asLong(0);
                        state.sawCacheReadTokens = true;
                        streamSink.onUsage(state.inputTokens, state.outputTokens, state.cacheCreationTokens, state.cacheReadTokens);
                    }
                }
            }
            case "content_block_start" -> {
                var contentBlock = node.path("content_block");
                var blockType = contentBlock.path("type").asText("");
                if ("tool_use".equals(blockType)) {
                    state.toolName = contentBlock.path("name").asText(null);
                    state.toolUseId = contentBlock.path("id").asText(null);
                    state.toolInputJson.setLength(0);
                    if (contentBlock.path("input").isObject()) {
                        state.toolArguments = objectMapper.convertValue(contentBlock.path("input"), new TypeReference<>() {});
                        if (!contentBlock.path("input").isEmpty()) {
                            state.toolInputJson.append(objectMapper.writeValueAsString(contentBlock.path("input")));
                        }
                    } else {
                        state.toolArguments = new HashMap<>();
                    }
                }
            }
            case "content_block_delta" -> processContentBlockDelta(node.path("delta"), state, streamSink);
            case "content_block_stop" -> {
            }
            case "message_delta" -> {
                var usageNode = node.path("usage");
                if (usageNode.isObject() && usageNode.has("output_tokens")) {
                    state.outputTokens = usageNode.path("output_tokens").asLong(0);
                    state.sawOutputTokens = true;
                    streamSink.onUsage(state.inputTokens, state.outputTokens, state.cacheCreationTokens, state.cacheReadTokens);
                }
            }
            case "message_stop" -> {
                applyMessageStopUsage(node.path("usage"), state, streamSink);
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private void processContentBlockDelta(JsonNode delta, StreamingParseState state, ModelStreamEventSink streamSink) {
        var deltaType = delta.path("type").asText("");
        switch (deltaType) {
            case "text_delta" -> appendTextDelta(delta, state, streamSink);
            case "input_json_delta" -> appendToolInputDelta(delta, state, streamSink);
            case "thinking_delta" -> {
                if (delta.has("thinking")) {
                    var thinking = delta.path("thinking").asText();
                    if (!thinking.isEmpty()) {
                        streamSink.onThinkingDelta(thinking);
                    }
                }
            }
            default -> {
                appendTextDelta(delta, state, streamSink);
                appendToolInputDelta(delta, state, streamSink);
            }
        }
    }

    private void appendTextDelta(JsonNode delta, StreamingParseState state, ModelStreamEventSink streamSink) {
        if (!delta.has("text")) {
            return;
        }
        var chunk = delta.path("text").asText();
        state.text.append(chunk);
        if (!chunk.isEmpty()) {
            streamSink.onTextDelta(chunk);
        }
    }

    private void appendToolInputDelta(JsonNode delta, StreamingParseState state, ModelStreamEventSink streamSink) {
        if (!delta.has("partial_json") || state.toolName == null) {
            return;
        }
        var partial = delta.path("partial_json").asText("");
        state.toolInputJson.append(partial);
        streamSink.onToolInputDelta(state.toolName, state.toolUseId, partial);
    }

    private void applyMessageStopUsage(JsonNode usageNode, StreamingParseState state, ModelStreamEventSink streamSink) {
        if (!usageNode.isObject()) {
            return;
        }
        if (!state.sawInputTokens && usageNode.has("input_tokens")) {
            state.inputTokens = usageNode.path("input_tokens").asLong(0);
            state.sawInputTokens = true;
        }
        if (!state.sawOutputTokens && usageNode.has("output_tokens")) {
            state.outputTokens = usageNode.path("output_tokens").asLong(0);
            state.sawOutputTokens = true;
        }
        if (!state.sawCacheCreationTokens && usageNode.has("cache_creation_input_tokens")) {
            state.cacheCreationTokens = usageNode.path("cache_creation_input_tokens").asLong(0);
            state.sawCacheCreationTokens = true;
        }
        if (!state.sawCacheReadTokens && usageNode.has("cache_read_input_tokens")) {
            state.cacheReadTokens = usageNode.path("cache_read_input_tokens").asLong(0);
            state.sawCacheReadTokens = true;
        }
        streamSink.onUsage(state.inputTokens, state.outputTokens, state.cacheCreationTokens, state.cacheReadTokens);
    }

    private ModelResponse toModelResponse(StreamingParseState state) throws Exception {
        var usage = new ModelResponse.Usage(state.inputTokens, state.outputTokens, state.cacheCreationTokens, state.cacheReadTokens);
        if (state.toolName != null && !state.toolName.isBlank()) {
            state.toolArguments = materializeFinalToolArguments(state);
            return new ModelResponse.ToolRequest(state.toolName, state.toolArguments, state.toolUseId, usage);
        }
        return new ModelResponse.AssistantReply(state.text.toString(), usage);
    }

    private Map<String, Object> materializeFinalToolArguments(StreamingParseState state) throws Exception {
        if (state.toolInputJson.length() == 0) {
            return state.toolArguments;
        }
        try {
            return objectMapper.readValue(state.toolInputJson.toString(), new TypeReference<>() {});
        } catch (JsonEOFException exception) {
            throw new IllegalStateException(
                "Incomplete streamed tool input for tool %s; model output may have been truncated by max_tokens"
                    .formatted(state.toolName),
                exception
            );
        }
    }

    private static final class StreamingParseState {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder toolInputJson = new StringBuilder();
        private String toolName;
        private String toolUseId;
        private Map<String, Object> toolArguments = Map.of();
        private long inputTokens;
        private long outputTokens;
        private long cacheCreationTokens;
        private long cacheReadTokens;
        private boolean sawInputTokens;
        private boolean sawOutputTokens;
        private boolean sawCacheCreationTokens;
        private boolean sawCacheReadTokens;
    }

    Map<String, Object> buildPayload(QueryRequest request) {
        return buildPayload(request, true);
    }

    Map<String, Object> buildPayload(QueryRequest request, boolean stream) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("model", model);
        payload.put("max_tokens", contextWindow);
        payload.put("stream", stream);

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            payload.put("system", request.systemPrompt());
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream()
                .map(t -> {
                    var toolMap = new LinkedHashMap<String, Object>();
                    toolMap.put("name", t.name());
                    toolMap.put("description", t.description());
                    toolMap.put("input_schema", t.inputSchema());
                    return toolMap;
                })
                .toList());
        }

        payload.put("messages", toAgenticMessages(request.messages()));
        return payload;
    }

    private List<Map<String, Object>> toAgenticMessages(List<Message> history) {
        var result = new ArrayList<Map<String, Object>>();
        var resolvedToolUseIds = resolvedToolUseIds(history);
        for (Message message : history) {
            if (message instanceof Message.UserMessage userMessage) {
                appendUserContentBlock(result, Map.of("type", "text", "text", userMessage.content()));
            } else if (message instanceof Message.AssistantMessage assistantMessage) {
                result.add(message("assistant", assistantMessage.content()));
            } else if (message instanceof Message.AssistantToolUseMessage toolUseMessage) {
                if (toolUseMessage.toolUseId() == null || toolUseMessage.toolUseId().isBlank() || resolvedToolUseIds.contains(toolUseMessage.toolUseId())) {
                    result.add(assistantToolUseMessage(toolUseMessage));
                }
            } else if (message instanceof Message.ToolResultMessage toolResultMessage) {
                appendUserContentBlock(result, toolResultContentBlock(toolResultMessage));
            }
        }
        return result;
    }

    private Set<String> resolvedToolUseIds(List<Message> history) {
        var resolved = new HashSet<String>();
        for (Message message : history) {
            if (message instanceof Message.ToolResultMessage toolResultMessage
                && toolResultMessage.toolUseId() != null
                && !toolResultMessage.toolUseId().isBlank()) {
                resolved.add(toolResultMessage.toolUseId());
            }
        }
        return resolved;
    }

    private Map<String, Object> message(String role, String text) {
        return Map.of(
            "role", role,
            "content", List.of(Map.of("type", "text", "text", text))
        );
    }

    private void appendUserContentBlock(List<Map<String, Object>> messages, Map<String, Object> contentBlock) {
        if (!messages.isEmpty()) {
            var lastMessage = messages.get(messages.size() - 1);
            if ("user".equals(lastMessage.get("role"))) {
                @SuppressWarnings("unchecked")
                var content = (List<Object>) lastMessage.get("content");
                content.add(contentBlock);
                return;
            }
        }
        var content = new ArrayList<Object>();
        content.add(contentBlock);
        messages.add(new LinkedHashMap<>(Map.of(
            "role", "user",
            "content", content
        )));
    }

    private Map<String, Object> toolResultContentBlock(Message.ToolResultMessage toolResultMessage) {
        var block = new LinkedHashMap<String, Object>();
        block.put("type", "tool_result");
        if (toolResultMessage.toolUseId() != null && !toolResultMessage.toolUseId().isBlank()) {
            block.put("tool_use_id", toolResultMessage.toolUseId());
        }
        block.put("content", toolResultMessage.content());
        return block;
    }

    private Map<String, Object> assistantToolUseMessage(Message.AssistantToolUseMessage toolUseMessage) {
        var block = new LinkedHashMap<String, Object>();
        block.put("type", "tool_use");
        if (toolUseMessage.toolUseId() != null && !toolUseMessage.toolUseId().isBlank()) {
            block.put("id", toolUseMessage.toolUseId());
        }
        block.put("name", toolUseMessage.toolName());
        block.put("input", parseAssistantToolUseInput(toolUseMessage.content()));
        return Map.of(
            "role", "assistant",
            "content", List.of(block)
        );
    }

    private Object parseAssistantToolUseInput(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of("raw", content);
        }
    }

    private String extractText(JsonNode root) {
        var content = root.path("content");
        if (!content.isArray() || content.isEmpty()) {
            return root.toPrettyString();
        }

        var fragments = new ArrayList<String>();
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText())) {
                fragments.add(item.path("text").asText());
            }
        }
        return fragments.isEmpty() ? root.toPrettyString() : String.join(System.lineSeparator(), fragments);
    }

    private ModelResponse.ToolRequest extractToolRequest(JsonNode root) {
        var content = root.path("content");
        if (!content.isArray()) {
            return null;
        }

        for (JsonNode item : content) {
            if (!"tool_use".equals(item.path("type").asText())) {
                continue;
            }

            var toolName = item.path("name").asText(null);
            if (toolName == null || toolName.isBlank()) {
                continue;
            }
            var toolUseId = item.path("id").asText(null);

            Map<String, Object> arguments = Map.of();
            var inputNode = item.path("input");
            if (inputNode.isObject()) {
                arguments = objectMapper.convertValue(inputNode, new TypeReference<>() {});
            }
            return new ModelResponse.ToolRequest(toolName, arguments, toolUseId);
        }

        return null;
    }

    private ModelResponse.Usage extractUsage(JsonNode root) {
        var usageNode = root.path("usage");
        if (!usageNode.isObject()) {
            return new ModelResponse.Usage(0, 0);
        }
        return new ModelResponse.Usage(
            usageNode.path("input_tokens").asLong(usageNode.path("inputTokens").asLong(0)),
            usageNode.path("output_tokens").asLong(usageNode.path("outputTokens").asLong(0)),
            usageNode.path("cache_creation_input_tokens").asLong(0),
            usageNode.path("cache_read_input_tokens").asLong(0)
        );
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String retryReasonForStatus(int statusCode, String errorBody) {
        if (!isRetryableStatus(statusCode)) {
            return null;
        }
        var normalizedBody = errorBody == null ? "" : errorBody.toLowerCase();
        if (statusCode == 429 || normalizedBody.contains("rate_limit_error") || normalizedBody.contains("rate limit")) {
            return "rate limited";
        }
        if (statusCode == 529 || normalizedBody.contains("overloaded_error") || normalizedBody.contains("overloaded")) {
            return "service overloaded";
        }
        return "transient HTTP " + statusCode;
    }

    private String retryReasonForException(Exception exception) {
        if (!RetryHandler.isRetryable(exception)) {
            return null;
        }
        var message = exception.getMessage();
        var normalized = message == null ? "" : message.toLowerCase();
        if (normalized.contains("529") || normalized.contains("overloaded_error") || normalized.contains("overloaded")) {
            return "service overloaded";
        }
        if (normalized.contains("429") || normalized.contains("rate limit")) {
            return "rate limited";
        }
        return "transient request failure";
    }

    private void emitRetryStatus(ModelStreamEventSink streamSink, int nextAttempt, String reason) {
        streamSink.onStatus(ModelStreamEventSink.RETRY_STATUS_PREFIX + "attempt " + nextAttempt + " of " + MAX_ATTEMPTS + " after " + reason);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504 || statusCode == 529;
    }

    private void recordRawHistory(BootstrapState bootstrapState, String direction, Object rawContent) {
        if (bootstrapState == null || rawContent == null) {
            return;
        }
        bootstrapState.update(current -> current.withSessionRuntime(
            current.sessionRuntime().appendRawAiHistory(
                new SessionRuntime.RawAiHistoryEntry(Instant.now(), direction, stringifyRawContent(rawContent))
            )
        ));
    }

    private String stringifyRawContent(Object rawContent) {
        if (rawContent instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rawContent);
        } catch (Exception exception) {
            return String.valueOf(rawContent);
        }
    }
}
