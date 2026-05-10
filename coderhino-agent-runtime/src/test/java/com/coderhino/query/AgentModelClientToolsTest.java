package com.coderhino.query;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModelClientToolsTest {

    private AgentModelClient client;

    @BeforeEach
    void setUp() {
        client = new AgentModelClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );
    }

    @Test
    void buildPayloadIncludesToolsWhenPresent() {
        var tools = List.of(
            new ToolSchema("bash", "Run a bash command", Map.of("type", "object", "properties", Map.of("command", Map.of("type", "string"))))
        );
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, tools);
        var payload = client.buildPayload(request, false);

        assertTrue(payload.containsKey("tools"), "payload should contain tools key");
        @SuppressWarnings("unchecked")
        var toolsList = (List<Map<String, Object>>) payload.get("tools");
        assertEquals(1, toolsList.size());
        assertEquals("bash", toolsList.get(0).get("name"));
        assertEquals("Run a bash command", toolsList.get(0).get("description"));
        assertNotNull(toolsList.get(0).get("input_schema"));
    }

    @Test
    void buildPayloadOmitsToolsWhenNull() {
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null);
        var payload = client.buildPayload(request, false);

        assertFalse(payload.containsKey("tools"), "payload should not contain tools key when null");
    }

    @Test
    void buildPayloadOmitsToolsWhenEmpty() {
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, List.of());
        var payload = client.buildPayload(request, false);

        assertFalse(payload.containsKey("tools"), "payload should not contain tools key when empty");
    }

    @Test
    void buildPayloadToolSchemaFormat() {
        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", Map.of("pattern", Map.of("type", "string", "description", "glob pattern"))
        );
        var tools = List.of(new ToolSchema("glob", "Find files by pattern", inputSchema));
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("test")), "system", null, null, tools);
        var payload = client.buildPayload(request, false);

        @SuppressWarnings("unchecked")
        var toolsList = (List<Map<String, Object>>) payload.get("tools");
        var tool = toolsList.get(0);
        assertEquals("glob", tool.get("name"));
        assertEquals("Find files by pattern", tool.get("description"));
        assertEquals(inputSchema, tool.get("input_schema"));
    }

    @Test
    void buildPayloadUsesExpandedOutputBudget() {
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null);

        var payload = client.buildPayload(request, false);

        assertEquals(128000L, payload.get("max_tokens"));
    }

    @Test
    void buildPayloadUsesConfiguredMaxOutputTokens() {
        var configuredClient = new AgentModelClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet",
            ProviderApiType.CLAUDE_CODE,
            128000L,
            4096L
        );
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null);

        var payload = configuredClient.buildPayload(request, false);

        assertEquals(4096L, payload.get("max_tokens"));
    }

    @Test
    void configuredContextWindowDoesNotBecomeMaxOutputTokens() {
        var configuredClient = new AgentModelClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet",
            ProviderApiType.CLAUDE_CODE,
            64000L,
            2048L
        );
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null);

        var payload = configuredClient.buildPayload(request, false);

        assertEquals(2048L, payload.get("max_tokens"));
        assertFalse(payload.containsValue(64000L));
    }

    @Test
    void buildPayloadReplaysSignedThinkingBeforeToolUse() {
        List<com.coderhino.types.Message> messages = List.of(
            new com.coderhino.types.Message.UserMessage("hi"),
            new com.coderhino.types.Message.AssistantToolUseMessage("{\"pattern\":\"*.java\"}", "glob", "call-1", "assistant-1", "plan", "signed-plan"),
            new com.coderhino.types.Message.ToolResultMessage("AgentModelClient.java", "glob", "call-1")
        );

        var payload = client.buildPayload(new QueryRequest(messages, "system", null, null, null), false);

        @SuppressWarnings("unchecked")
        var agenticMessages = (List<Map<String, Object>>) payload.get("messages");
        assertEquals("assistant", agenticMessages.get(1).get("role"));
        @SuppressWarnings("unchecked")
        var content = (List<Map<String, Object>>) agenticMessages.get(1).get("content");
        assertEquals("thinking", content.get(0).get("type"));
        assertEquals("plan", content.get(0).get("thinking"));
        assertEquals("signed-plan", content.get(0).get("signature"));
        assertEquals("tool_use", content.get(1).get("type"));
        assertEquals("call-1", content.get(1).get("id"));
    }

    @Test
    void buildPayloadOmitsUnsignedThinkingBeforeToolUse() {
        List<com.coderhino.types.Message> messages = List.of(
            new com.coderhino.types.Message.UserMessage("hi"),
            new com.coderhino.types.Message.AssistantToolUseMessage("{\"pattern\":\"*.java\"}", "glob", "call-1", "assistant-1", "plan"),
            new com.coderhino.types.Message.ToolResultMessage("AgentModelClient.java", "glob", "call-1")
        );

        var payload = client.buildPayload(new QueryRequest(messages, "system", null, null, null), false);

        @SuppressWarnings("unchecked")
        var agenticMessages = (List<Map<String, Object>>) payload.get("messages");
        assertEquals("assistant", agenticMessages.get(1).get("role"));
        @SuppressWarnings("unchecked")
        var content = (List<Map<String, Object>>) agenticMessages.get(1).get("content");
        assertEquals(1, content.size());
        assertEquals("tool_use", content.get(0).get("type"));
        assertEquals("call-1", content.get(0).get("id"));
    }

    @Test
    void buildOpenAiPayloadMapsMessagesAndTools() {
        var openAiClient = newOpenAiClient();
        var tools = List.of(
            new ToolSchema("glob", "Find files", Map.of("type", "object", "properties", Map.of("pattern", Map.of("type", "string"))))
        );
        List<com.coderhino.types.Message> messages = List.of(
            new com.coderhino.types.Message.UserMessage("hi"),
            new com.coderhino.types.Message.AssistantMessage("hello"),
            new com.coderhino.types.Message.AssistantToolUseMessage("{\"pattern\":\"*.java\"}", "glob", "call-1"),
            new com.coderhino.types.Message.ToolResultMessage("AgentModelClient.java", "glob", "call-1")
        );
        var request = new QueryRequest(messages, "system prompt", null, null, tools);

        var payload = openAiClient.buildPayload(request, false);

        assertEquals("gpt-4o", payload.get("model"));
        assertEquals(false, payload.get("stream"));
        assertEquals(4096L, payload.get("max_tokens"));
        @SuppressWarnings("unchecked")
        var openAiMessages = (List<Map<String, Object>>) payload.get("messages");
        assertEquals("system", openAiMessages.get(0).get("role"));
        assertEquals("system prompt", openAiMessages.get(0).get("content"));
        assertEquals("user", openAiMessages.get(1).get("role"));
        assertEquals("hi", openAiMessages.get(1).get("content"));
        assertEquals("assistant", openAiMessages.get(2).get("role"));
        assertEquals("hello", openAiMessages.get(2).get("content"));
        assertEquals("assistant", openAiMessages.get(3).get("role"));
        @SuppressWarnings("unchecked")
        var toolCalls = (List<Map<String, Object>>) openAiMessages.get(3).get("tool_calls");
        assertEquals("call-1", toolCalls.get(0).get("id"));
        @SuppressWarnings("unchecked")
        var function = (Map<String, Object>) toolCalls.get(0).get("function");
        assertEquals("glob", function.get("name"));
        assertEquals("{\"pattern\":\"*.java\"}", function.get("arguments"));
        assertEquals("tool", openAiMessages.get(4).get("role"));
        assertEquals("call-1", openAiMessages.get(4).get("tool_call_id"));
        @SuppressWarnings("unchecked")
        var openAiTools = (List<Map<String, Object>>) payload.get("tools");
        assertEquals("function", openAiTools.get(0).get("type"));
        @SuppressWarnings("unchecked")
        var toolFunction = (Map<String, Object>) openAiTools.get(0).get("function");
        assertEquals("glob", toolFunction.get("name"));
        assertEquals("Find files", toolFunction.get("description"));
        assertNotNull(toolFunction.get("parameters"));
    }

    @Test
    void buildOpenAiPayloadOmitsEmptyTools() {
        var payload = newOpenAiClient().buildPayload(
            new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, List.of()),
            false
        );

        assertFalse(payload.containsKey("tools"));
    }

    @Test
    void completeOpenAiRequestUsesChatCompletionsUrlAndAuthorizationHeader() throws Exception {
        var http = new FakeHttpClient();
        http.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
            "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}",
            "",
            "data: [DONE]",
            ""
        )));
        var openAiClient = newOpenAiClient(http);

        var response = openAiClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("ok"));
        assertEquals(URI.create("https://api.openai.com/v1/chat/completions"), http.lastRequest.uri());
        assertEquals(Optional.of("Bearer test-key"), http.lastRequest.headers().firstValue("Authorization"));
        assertTrue(http.lastRequest.headers().firstValue("x-api-key").isEmpty());
    }

    @Test
    void modelClientFactoryCreatesOpenAiProvider() {
        var modelClient = ModelClientFactory.create(
            "model",
            "key",
            "https://api.openai.com",
            ProviderApiType.OPENAI
        );

        assertNotNull(modelClient);
    }

    @Test
    void modelClientFactoryRejectsMissingCredentials() {
        var exception = assertThrows(IllegalStateException.class, () -> ModelClientFactory.create(
            "model",
            "",
            "https://api.anthropic.com",
            ProviderApiType.CLAUDE_CODE
        ));

        assertTrue(exception.getMessage().contains("Model API credentials are required"));
    }

    @Test
    void terminalHttpFailureReturnsStructuredModelError() {
        var http = new FakeHttpClient();
        http.enqueueResponse(new FakeHttpResponse<>(400, Stream.of("bad request")));
        var configuredClient = new AgentModelClient(
            http,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );
        var request = new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null);

        var response = configuredClient.complete(newBootstrapState(), request);

        var error = assertInstanceOf(ModelResponse.ModelError.class, response);
        assertTrue(error.message().contains("Anthropic API error (400): bad request"));
    }

    @Test
    void parseResponseBodyExtractsCacheTokens() throws Exception {
        var json = """
            {
              "content": [{"type": "text", "text": "hello"}],
              "usage": {
                "input_tokens": 100,
                "output_tokens": 50,
                "cache_creation_input_tokens": 30,
                "cache_read_input_tokens": 20
              }
            }
            """;
        var response = client.parseResponseBody(json);
        var usage = response instanceof ModelResponse.AssistantReply reply ? reply.usage() : null;
        assertNotNull(usage);
        assertEquals(100, usage.inputTokens());
        assertEquals(50, usage.outputTokens());
        assertEquals(30, usage.cacheCreationTokens());
        assertEquals(20, usage.cacheReadTokens());
    }

    @Test
    void parseStreamBodyExtractsCacheTokens() throws Exception {
        var body = "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":200,\"cache_creation_input_tokens\":40,\"cache_read_input_tokens\":60}}}\n\n"
                 + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}\n\n"
                 + "event: message_delta\ndata: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":25}}\n\n"
                 + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n";
        var response = client.parseStreamBody(body);
        var usage = response instanceof ModelResponse.AssistantReply reply ? reply.usage() : null;
        assertNotNull(usage);
        assertEquals(200, usage.inputTokens());
        assertEquals(25, usage.outputTokens());
        assertEquals(40, usage.cacheCreationTokens());
        assertEquals(60, usage.cacheReadTokens());
    }

    @Test
    void parseStreamBodyDoesNotDoubleCountUsageWhenMessageStopRepeatsFields() throws Exception {
        var body = "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":200,\"cache_creation_input_tokens\":40,\"cache_read_input_tokens\":60}}}\n\n"
                 + "event: message_delta\ndata: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":25}}\n\n"
                 + "event: message_stop\ndata: {\"type\":\"message_stop\",\"usage\":{\"input_tokens\":999,\"output_tokens\":888,\"cache_creation_input_tokens\":777,\"cache_read_input_tokens\":666}}\n\n";
        var response = client.parseStreamBody(body);
        var usage = response instanceof ModelResponse.AssistantReply reply ? reply.usage() : null;
        assertNotNull(usage);
        assertEquals(200, usage.inputTokens());
        assertEquals(25, usage.outputTokens());
        assertEquals(40, usage.cacheCreationTokens());
        assertEquals(60, usage.cacheReadTokens());
    }

    @Test
    void parseResponseBodyWithNoCacheTokensReturnsZero() throws Exception {
        var json = """
            {
              "content": [{"type": "text", "text": "hello"}],
              "usage": {
                "input_tokens": 100,
                "output_tokens": 50
              }
            }
            """;
        var response = client.parseResponseBody(json);
        var usage = response instanceof ModelResponse.AssistantReply reply ? reply.usage() : null;
        assertNotNull(usage);
        assertEquals(0, usage.cacheCreationTokens());
        assertEquals(0, usage.cacheReadTokens());
    }

    @Test
    void parseOpenAiResponseBodyExtractsAssistantTextAndUsage() throws Exception {
        var json = """
            {
              "choices": [{"message": {"role": "assistant", "content": "hello openai"}}],
              "usage": {"prompt_tokens": 11, "completion_tokens": 7}
            }
            """;

        var response = newOpenAiClient().parseResponseBody(json);

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("hello openai"));
        var usage = ((ModelResponse.AssistantReply) response).usage();
        assertEquals(11, usage.inputTokens());
        assertEquals(7, usage.outputTokens());
        assertEquals(0, usage.cacheCreationTokens());
        assertEquals(0, usage.cacheReadTokens());
    }

    @Test
    void parseOpenAiResponseBodyExtractsToolCall() throws Exception {
        var json = """
            {
              "choices": [{"message": {"role": "assistant", "tool_calls": [{
                "id": "call-1",
                "type": "function",
                "function": {"name": "glob", "arguments": "{\\\"pattern\\\":\\\"*.java\\\"}"}
              }]}}],
              "usage": {"prompt_tokens": 3, "completion_tokens": 4}
            }
            """;

        var response = newOpenAiClient().parseResponseBody(json);

        assertTrue(response instanceof ModelResponse.ToolRequest toolRequest);
        assertEquals("glob", ((ModelResponse.ToolRequest) response).toolName());
        assertEquals("call-1", ((ModelResponse.ToolRequest) response).toolUseId());
        assertEquals(Map.of("pattern", "*.java"), ((ModelResponse.ToolRequest) response).arguments());
        assertEquals(3, ((ModelResponse.ToolRequest) response).usage().inputTokens());
        assertEquals(4, ((ModelResponse.ToolRequest) response).usage().outputTokens());
    }

    @Test
    void parseOpenAiResponseBodyPreservesMalformedToolArguments() throws Exception {
        var json = """
            {
              "choices": [{"message": {"role": "assistant", "tool_calls": [{
                "id": "call-1",
                "type": "function",
                "function": {"name": "glob", "arguments": "{bad-json"}
              }]}}]
            }
            """;

        var response = newOpenAiClient().parseResponseBody(json);

        assertTrue(response instanceof ModelResponse.ToolRequest);
        assertEquals(Map.of("raw", "{bad-json"), ((ModelResponse.ToolRequest) response).arguments());
    }

    @Test
    void openAiHttpFailureReturnsProviderLabeledModelError() {
        var http = new FakeHttpClient();
        http.enqueueResponse(new FakeHttpResponse<>(400, Stream.of("bad request")));
        var openAiClient = newOpenAiClient(http);

        var response = openAiClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

        var error = assertInstanceOf(ModelResponse.ModelError.class, response);
        assertTrue(error.message().contains("OpenAI API error (400): bad request"));
    }

    @Test
    void completeLogsStreamingFallbackAndPreservesReplyBehavior() {
        var appender = attachLogs();
        try {
            var httpClient = new FakeHttpClient();
            httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of("data: {not-json}", "")));
            httpClient.enqueueResponse(new FakeHttpResponse<>(200, "{" +
                "\"content\":[{\"type\":\"text\",\"text\":\"fallback ok\"}]," +
                "\"usage\":{\"input_tokens\":1,\"output_tokens\":2}" +
                "}"));
            var loggingClient = new AgentModelClient(
                httpClient,
                new ObjectMapper(),
                "https://api.anthropic.com",
                "test-key",
                "claude-3-sonnet"
            );

            var response = loggingClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

            assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("fallback ok"));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(
                "Anthropic streaming response processing failed on attempt 1 of 5. Falling back to non-streaming request.")));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void completeLogsTerminalRequestFailureAfterRetries() {
        var appender = attachLogs();
        try {
            var httpClient = new FakeHttpClient();
            httpClient.enqueueFailure(new IOException("boom-1 overloaded_error"));
            httpClient.enqueueFailure(new IOException("boom-2 overloaded_error"));
            httpClient.enqueueFailure(new IOException("boom-3 overloaded_error"));
            httpClient.enqueueFailure(new IOException("boom-4 overloaded_error"));
            httpClient.enqueueFailure(new IOException("boom-5 overloaded_error"));
            var loggingClient = new AgentModelClient(
                httpClient,
                new ObjectMapper(),
                "https://api.anthropic.com",
                "test-key",
                "claude-3-sonnet"
            );

            var response = loggingClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

            assertTrue(response instanceof ModelResponse.ModelError error && error.message().contains("Anthropic request failed: boom-5 overloaded_error"));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Anthropic request attempt 1 of 5 failed. Retrying.")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Anthropic request failed after 5 attempts")
                && event.getThrowableProxy() != null));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void completeRetriesOverloadedStatusUpToSuccessBeforeLimit() {
        var httpClient = new FakeHttpClient();
        httpClient.enqueueResponse(new FakeHttpResponse<>(529, Stream.of("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"cluster busy\"}}")));
        httpClient.enqueueResponse(new FakeHttpResponse<>(529, Stream.of("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"cluster busy\"}}")));
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1}}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"recovered\"}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\",\"usage\":{\"output_tokens\":1}}",
            ""
        )));
        var streamEvents = new CapturingModelStreamEventSink();
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );

        var response = loggingClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null), streamEvents);

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("recovered"));
        assertEquals(List.of(
            "Retrying LLM request: attempt 2 of 5 after service overloaded",
            "Retrying LLM request: attempt 3 of 5 after service overloaded"
        ), streamEvents.statuses);
    }

    @Test
    void completeRetriesRateLimitedStatusUpToSuccessBeforeLimit() {
        var httpClient = new FakeHttpClient();
        httpClient.enqueueResponse(new FakeHttpResponse<>(429, Stream.of(
            "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"too many requests\"}}"
        )));
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1}}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"recovered after rate limit\"}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\",\"usage\":{\"output_tokens\":1}}",
            ""
        )));
        var streamEvents = new CapturingModelStreamEventSink();
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );

        var response = loggingClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null), streamEvents);

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("recovered after rate limit"));
        assertEquals(List.of("Retrying LLM request: attempt 2 of 5 after rate limited"), streamEvents.statuses);
    }

    @Test
    void completeStopsRetryingAfterFifthOverloadedStatus() {
        var httpClient = new FakeHttpClient();
        for (int i = 0; i < 5; i++) {
            httpClient.enqueueResponse(new FakeHttpResponse<>(529, Stream.of("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"cluster busy\"}}")));
        }
        var streamEvents = new CapturingModelStreamEventSink();
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );

        var response = loggingClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null), streamEvents);

        assertTrue(response instanceof ModelResponse.ModelError error && error.message().contains("Anthropic API error (529)"));
        assertEquals(4, streamEvents.statuses.size());
        assertEquals("Retrying LLM request: attempt 5 of 5 after service overloaded", streamEvents.statuses.get(3));
    }

    @Test
    void completeRetriesTimedOutRequestsUpToSuccessBeforeLimit() throws Exception {
        var httpClient = new FakeHttpClient();
        httpClient.enqueueFailure(new HttpTimeoutException("request timed out"));
        httpClient.enqueueFailure(new IOException("upstream request timed out while waiting for response"));
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1}}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"recovered after timeout\"}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\",\"usage\":{\"output_tokens\":1}}",
            ""
        )));
        var streamEvents = new CapturingModelStreamEventSink();
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );

        var response = loggingClient.complete(
            newBootstrapState(),
            new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null),
            streamEvents
        );

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("recovered after timeout"));
        assertEquals(List.of(
            "Retrying LLM request: attempt 2 of 5 after transient request failure",
            "Retrying LLM request: attempt 3 of 5 after transient request failure"
        ), streamEvents.statuses);
    }

    @Test
    void completeStopsRetryingAfterFifthTimedOutRequest() throws Exception {
        var httpClient = new FakeHttpClient();
        for (int i = 0; i < 5; i++) {
            httpClient.enqueueFailure(new HttpTimeoutException("request timed out"));
        }
        var streamEvents = new CapturingModelStreamEventSink();
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );

        var response = loggingClient.complete(
            newBootstrapState(),
            new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null),
            streamEvents
        );

        assertTrue(response instanceof ModelResponse.ModelError error && error.message().contains("Anthropic request failed: request timed out"));
        assertEquals(4, streamEvents.statuses.size());
        assertEquals("Retrying LLM request: attempt 5 of 5 after transient request failure", streamEvents.statuses.get(3));
    }

    @Test
    void completeRecordsRawRequestAndStreamingResponseHistory() {
        var httpClient = new FakeHttpClient();
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1}}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}",
            "",
            "event: message_delta",
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":2}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\"}",
            ""
        )));
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );
        var bootstrapState = newBootstrapState();

        var response = loggingClient.complete(bootstrapState, new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("hello"));
        assertEquals(2, bootstrapState.get().sessionRuntime().rawAiHistory().size());
        assertEquals("request", bootstrapState.get().sessionRuntime().rawAiHistory().get(0).direction());
        assertTrue(bootstrapState.get().sessionRuntime().rawAiHistory().get(0).content().contains("\"messages\""));
        assertEquals("response", bootstrapState.get().sessionRuntime().rawAiHistory().get(1).direction());
        assertTrue(bootstrapState.get().sessionRuntime().rawAiHistory().get(1).content().contains("event: message_start"));
    }

    @Test
    void completeRecordsFallbackRequestAndResponseHistory() {
        var httpClient = new FakeHttpClient();
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of("data: {not-json}", "")));
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, "{" +
            "\"content\":[{\"type\":\"text\",\"text\":\"fallback ok\"}]," +
            "\"usage\":{\"input_tokens\":1,\"output_tokens\":2}" +
            "}"));
        var loggingClient = new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.anthropic.com",
            "test-key",
            "claude-3-sonnet"
        );
        var bootstrapState = newBootstrapState();

        var response = loggingClient.complete(bootstrapState, new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("fallback ok"));
        assertEquals(4, bootstrapState.get().sessionRuntime().rawAiHistory().size());
        assertEquals("request", bootstrapState.get().sessionRuntime().rawAiHistory().get(0).direction());
        assertEquals("response", bootstrapState.get().sessionRuntime().rawAiHistory().get(1).direction());
        assertEquals("request", bootstrapState.get().sessionRuntime().rawAiHistory().get(2).direction());
        assertEquals("response", bootstrapState.get().sessionRuntime().rawAiHistory().get(3).direction());
        assertTrue(bootstrapState.get().sessionRuntime().rawAiHistory().get(3).content().contains("fallback ok"));
    }

    @Test
    void processStreamLinesForwardsThinkingAndToolInputEvents() throws Exception {
        var streamEvents = new CapturingModelStreamEventSink();

        var response = client.processStreamLines(newBootstrapState(), Stream.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":9}}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"plan\"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"signature_delta\",\"signature\":\"signed-plan\"}}",
            "",
            "event: content_block_start",
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"glob\"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"pattern\\\":\"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"*.java\\\"}\"}}",
            "",
            "event: message_delta",
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":4}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\"}",
            ""
        ), streamEvents);

        assertTrue(response instanceof ModelResponse.ToolRequest toolRequest);
        assertEquals("glob", ((ModelResponse.ToolRequest) response).toolName());
        assertEquals(Map.of("pattern", "*.java"), ((ModelResponse.ToolRequest) response).arguments());
        assertEquals("plan", ((ModelResponse.ToolRequest) response).thinking());
        assertEquals("signed-plan", ((ModelResponse.ToolRequest) response).thinkingSignature());
        assertEquals(List.of("plan"), streamEvents.thinkingDeltas);
        assertEquals(List.of(new ToolInputDelta("glob", "tool-1", "{\"pattern\":"), new ToolInputDelta("glob", "tool-1", "\"*.java\"}")), streamEvents.toolInputDeltas);
        assertTrue(streamEvents.usageSnapshots.contains(new UsageSnapshot(9, 0, 0, 0)));
        assertTrue(streamEvents.usageSnapshots.contains(new UsageSnapshot(9, 4, 0, 0)));
    }

    @Test
    void processStreamLinesSkipsRecoverableIncompleteEventAndPreservesText() throws Exception {
        var bootstrapState = newBootstrapState();

        var response = client.processStreamLines(bootstrapState, Stream.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":3}}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello \"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello \"",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"world\"}}",
            "",
            "event: message_delta",
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":2}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\"}",
            ""
        ));

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("hello world"));
        assertEquals(1, bootstrapState.get().sessionRuntime().rawAiHistory().size());
        assertTrue(bootstrapState.get().sessionRuntime().rawAiHistory().get(0).content().contains("hello "));
    }

    @Test
    void processOpenAiStreamLinesForwardsTextDeltasAndUsage() throws Exception {
        var streamEvents = new CapturingModelStreamEventSink();

        var response = newOpenAiClient().processStreamLines(newBootstrapState(), Stream.of(
            "data: {\"choices\":[{\"delta\":{\"content\":\"hel\"}}]}",
            "",
            "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}",
            "",
            "data: [DONE]",
            ""
        ), streamEvents);

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("hello"));
        assertEquals(List.of("hel", "lo"), streamEvents.textDeltas);
        assertEquals(5, ((ModelResponse.AssistantReply) response).usage().inputTokens());
        assertEquals(2, ((ModelResponse.AssistantReply) response).usage().outputTokens());
        assertTrue(streamEvents.usageSnapshots.contains(new UsageSnapshot(5, 2, 0, 0)));
    }

    @Test
    void processOpenAiStreamLinesAccumulatesToolCallDeltas() throws Exception {
        var streamEvents = new CapturingModelStreamEventSink();

        var response = newOpenAiClient().processStreamLines(newBootstrapState(), Stream.of(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"glob\",\"arguments\":\"{\\\"pattern\\\":\"}}]}}]}",
            "",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"*.java\\\"}\"}}]}}]}",
            "",
            "data: [DONE]",
            ""
        ), streamEvents);

        assertTrue(response instanceof ModelResponse.ToolRequest);
        assertEquals("glob", ((ModelResponse.ToolRequest) response).toolName());
        assertEquals("call-1", ((ModelResponse.ToolRequest) response).toolUseId());
        assertEquals(Map.of("pattern", "*.java"), ((ModelResponse.ToolRequest) response).arguments());
        assertEquals(List.of(
            new ToolInputDelta("glob", "call-1", "{\"pattern\":"),
            new ToolInputDelta("glob", "call-1", "\"*.java\"}")
        ), streamEvents.toolInputDeltas);
    }

    @Test
    void completeOpenAiFallsBackWhenStreamingToolInputIsIncomplete() {
        var httpClient = new FakeHttpClient();
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"glob\",\"arguments\":\"{\\\"pattern\\\":\"}}]}}]}",
            "",
            "data: [DONE]",
            ""
        )));
        httpClient.enqueueResponse(new FakeHttpResponse<>(200, "{" +
            "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"fallback ok\"}}]," +
            "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}" +
            "}"));
        var openAiClient = newOpenAiClient(httpClient);

        var response = openAiClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("fallback ok"));
        assertEquals(2, httpClient.sentRequests.size());
        assertTrue(httpClient.sentBodies.get(0).contains("\"stream\":true"));
        assertTrue(httpClient.sentBodies.get(1).contains("\"stream\":false"));
    }

    @Test
    void processStreamLinesPreservesToolInputAcrossRecoverableIncompleteEvent() throws Exception {
        var streamEvents = new CapturingModelStreamEventSink();

        var response = client.processStreamLines(newBootstrapState(), Stream.of(
            "event: content_block_start",
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"glob\"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"pattern\\\":\"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ignored\"}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"*.java\\\"}\"}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\"}",
            ""
        ), streamEvents);

        assertTrue(response instanceof ModelResponse.ToolRequest);
        assertEquals("glob", ((ModelResponse.ToolRequest) response).toolName());
        assertEquals(Map.of("pattern", "*.java"), ((ModelResponse.ToolRequest) response).arguments());
        assertEquals(List.of(new ToolInputDelta("glob", "tool-1", "{\"pattern\":"), new ToolInputDelta("glob", "tool-1", "\"*.java\"}")), streamEvents.toolInputDeltas);
    }

    @Test
    void processStreamLinesPreservesWhitespaceOnlyToolInputChunks() throws Exception {
        var streamEvents = new CapturingModelStreamEventSink();
        var mapper = new ObjectMapper();
        var chunk1 = "{\"path\": \"src/main/resources/mocks/payment-response.json\", \"content\": \"{\\n";
        var chunk2 = "  ";
        var chunk3 = "\\\"paymentId\\\": \\\"5464533242342\\\",\\n";
        var chunk4 = "  ";
        var chunk5 = "}\"}";

        var response = client.processStreamLines(newBootstrapState(), Stream.of(
            "event: content_block_start",
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"write_file\"}}",
            "",
            "event: content_block_delta",
            "data: " + mapper.writeValueAsString(Map.of("type", "content_block_delta", "delta", Map.of("type", "input_json_delta", "partial_json", chunk1))),
            "",
            "event: content_block_delta",
            "data: " + mapper.writeValueAsString(Map.of("type", "content_block_delta", "delta", Map.of("type", "input_json_delta", "partial_json", chunk2))),
            "",
            "event: content_block_delta",
            "data: " + mapper.writeValueAsString(Map.of("type", "content_block_delta", "delta", Map.of("type", "input_json_delta", "partial_json", chunk3))),
            "",
            "event: content_block_delta",
            "data: " + mapper.writeValueAsString(Map.of("type", "content_block_delta", "delta", Map.of("type", "input_json_delta", "partial_json", chunk4))),
            "",
            "event: content_block_delta",
            "data: " + mapper.writeValueAsString(Map.of("type", "content_block_delta", "delta", Map.of("type", "input_json_delta", "partial_json", chunk5))),
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\"}",
            ""
        ), streamEvents);

        assertTrue(response instanceof ModelResponse.ToolRequest);
        assertEquals("write_file", ((ModelResponse.ToolRequest) response).toolName());
        assertEquals(
            Map.of(
                "path", "src/main/resources/mocks/payment-response.json",
                "content", "{\n  \"paymentId\": \"5464533242342\",\n  }"
            ),
            ((ModelResponse.ToolRequest) response).arguments()
        );
        assertEquals(
            List.of(
                new ToolInputDelta("write_file", "tool-1", chunk1),
                new ToolInputDelta("write_file", "tool-1", chunk2),
                new ToolInputDelta("write_file", "tool-1", chunk3),
                new ToolInputDelta("write_file", "tool-1", chunk4),
                new ToolInputDelta("write_file", "tool-1", chunk5)
            ),
            streamEvents.toolInputDeltas
        );
    }

    @Test
    void processStreamLinesReassemblesLargeWriteFileToolInputAcrossManyChunks() throws Exception {
        var mapper = new ObjectMapper();
        var streamEvents = new CapturingModelStreamEventSink();
        var content = "{\n"
            + "  \"paymentId\": \"5464533242342\",\n"
            + "  \"orderId\": \"123123\",\n"
            + "  \"amount\": 1247.50,\n"
            + "  \"currency\": \"USD\",\n"
            + "  \"paymentStatus\": \"COMPLETED\",\n"
            + "  \"bankResponse\": {\n"
            + "    \"status\": \"APPROVED\",\n"
            + "    \"responseMessage\": \"Transaction Approved\"\n"
            + "  }\n"
            + "}";
        var toolInputJson = mapper.writeValueAsString(Map.of(
            "path", "src/main/resources/mocks/payment-response.json",
            "content", content
        ));
        var chunks = List.of(
            toolInputJson.substring(0, 40),
            toolInputJson.substring(40, 120),
            toolInputJson.substring(120, 220),
            toolInputJson.substring(220)
        );

        var lines = new ArrayList<String>();
        lines.add("event: content_block_start");
        lines.add("data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"write_file\"}}");
        lines.add("");
        for (String chunk : chunks) {
            lines.add("event: content_block_delta");
            lines.add("data: " + mapper.writeValueAsString(Map.of(
                "type", "content_block_delta",
                "delta", Map.of("type", "input_json_delta", "partial_json", chunk)
            )));
            lines.add("");
        }
        lines.add("event: message_stop");
        lines.add("data: {\"type\":\"message_stop\"}");
        lines.add("");

        var response = client.processStreamLines(newBootstrapState(), lines.stream(), streamEvents);

        assertTrue(response instanceof ModelResponse.ToolRequest);
        assertEquals("write_file", ((ModelResponse.ToolRequest) response).toolName());
        assertEquals(
            Map.of(
                "path", "src/main/resources/mocks/payment-response.json",
                "content", content
            ),
            ((ModelResponse.ToolRequest) response).arguments()
        );
        assertEquals(
            List.of(
                new ToolInputDelta("write_file", "tool-1", chunks.get(0)),
                new ToolInputDelta("write_file", "tool-1", chunks.get(1)),
                new ToolInputDelta("write_file", "tool-1", chunks.get(2)),
                new ToolInputDelta("write_file", "tool-1", chunks.get(3))
            ),
            streamEvents.toolInputDeltas
        );
    }

    @Test
    void parseStreamBodySkipsRecoverableIncompleteEvent() throws Exception {
        var body = "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}\n\n"
            + "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"world\"}}\n\n"
            + "event: message_stop\n"
            + "data: {\"type\":\"message_stop\"}\n\n";

        var response = client.parseStreamBody(body);

        assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("world"));
    }

    @Test
    void completeFallsBackWhenFinalToolInputRemainsIncomplete() {
        var appender = attachLogs();
        try {
            var httpClient = new FakeHttpClient();
            httpClient.enqueueResponse(new FakeHttpResponse<>(200, Stream.of(
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"glob\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"pattern\\\":\"}}",
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}",
                ""
            )));
            httpClient.enqueueResponse(new FakeHttpResponse<>(200, "{" +
                "\"content\":[{\"type\":\"text\",\"text\":\"fallback ok\"}]," +
                "\"usage\":{\"input_tokens\":1,\"output_tokens\":2}" +
                "}"));
            var loggingClient = new AgentModelClient(
                httpClient,
                new ObjectMapper(),
                "https://api.anthropic.com",
                "test-key",
                "claude-3-sonnet"
            );

            var response = loggingClient.complete(newBootstrapState(), new QueryRequest(List.of(new com.coderhino.types.Message.UserMessage("hi")), "system", null, null, null));

            assertTrue(response instanceof ModelResponse.AssistantReply reply && reply.text().equals("fallback ok"));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(
                "Anthropic streaming response processing failed on attempt 1 of 5. Falling back to non-streaming request.")));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void processStreamLinesFailsWhenFinalToolInputRemainsIncomplete() {
        var mapper = new ObjectMapper();
        var chunk = "{\"path\":\"src/main/resources/mocks/payment-response.json\",\"content\":\"{\\n  \\\"paymentId\\\": \\\"5464533242342\\\"";
        var exception = assertInstanceOf(IllegalStateException.class, assertThrows(
            IllegalStateException.class,
            () -> client.processStreamLines(newBootstrapState(), Stream.of(
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"write_file\"}}",
                "",
                "event: content_block_delta",
                "data: " + mapper.writeValueAsString(Map.of("type", "content_block_delta", "delta", Map.of("type", "input_json_delta", "partial_json", chunk))),
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}",
                ""
            ))
        ));

        assertEquals(
            "Incomplete streamed tool input for tool write_file; model output may have been truncated by max_tokens",
            exception.getMessage()
        );
        assertInstanceOf(com.fasterxml.jackson.core.io.JsonEOFException.class, exception.getCause());
    }

    private static BootstrapState newBootstrapState() {
        return new BootstrapState(new AppState(
            false,
            "test-model",
            System.getProperty("user.dir"),
            true,
            true,
            PermissionMode.BYPASS,
            0.0,
            SessionRuntime.create(),
            List.of()
        ));
    }

    private static AgentModelClient newOpenAiClient() {
        return newOpenAiClient(HttpClient.newHttpClient());
    }

    private static AgentModelClient newOpenAiClient(HttpClient httpClient) {
        return new AgentModelClient(
            httpClient,
            new ObjectMapper(),
            "https://api.openai.com",
            "test-key",
            "gpt-4o",
            ProviderApiType.OPENAI,
            128000L,
            4096L
        );
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        var logger = (Logger) LoggerFactory.getLogger(AgentModelClient.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogs(ListAppender<ILoggingEvent> appender) {
        var logger = (Logger) LoggerFactory.getLogger(AgentModelClient.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private static final class CapturingModelStreamEventSink implements ModelStreamEventSink {
        private final List<String> textDeltas = new ArrayList<>();
        private final List<String> thinkingDeltas = new ArrayList<>();
        private final List<ToolInputDelta> toolInputDeltas = new ArrayList<>();
        private final List<UsageSnapshot> usageSnapshots = new ArrayList<>();
        private final List<String> statuses = new ArrayList<>();

        @Override
        public void onTextDelta(String text) {
            textDeltas.add(text);
        }

        @Override
        public void onThinkingDelta(String thinking) {
            thinkingDeltas.add(thinking);
        }

        @Override
        public void onStatus(String message) {
            statuses.add(message);
        }

        @Override
        public void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
            toolInputDeltas.add(new ToolInputDelta(toolName, toolUseId, partialJson));
        }

        @Override
        public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
            usageSnapshots.add(new UsageSnapshot(inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens));
        }
    }

    private record ToolInputDelta(String toolName, String toolUseId, String partialJson) {
    }

    private record UsageSnapshot(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ArrayDeque<Object> queuedResponses = new ArrayDeque<>();
        private final List<HttpRequest> sentRequests = new ArrayList<>();
        private final List<String> sentBodies = new ArrayList<>();
        private HttpRequest lastRequest;

        void enqueueResponse(HttpResponse<?> response) {
            queuedResponses.addLast(response);
        }

        void enqueueFailure(Exception exception) {
            queuedResponses.addLast(exception);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            lastRequest = request;
            sentRequests.add(request);
            sentBodies.add(readBody(request));
            var next = queuedResponses.removeFirst();
            if (next instanceof IOException ioException) {
                throw ioException;
            }
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (HttpResponse<T>) next;
        }

        private String readBody(HttpRequest request) {
            return request.bodyPublisher()
                .map(BodyCollector::collect)
                .orElse("");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private static final class BodyCollector implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
        private final StringBuilder body = new StringBuilder();
        private final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

        static String collect(HttpRequest.BodyPublisher publisher) {
            var collector = new BodyCollector();
            publisher.subscribe(collector);
            try {
                collector.done.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return collector.body.toString();
        }

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.nio.ByteBuffer item) {
            var bytes = new byte[item.remaining()];
            item.get(bytes);
            body.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }
    }

    private record FakeHttpResponse<T>(int statusCode, T body) implements HttpResponse<T> {
        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages")).build();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://api.anthropic.com/v1/messages");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
