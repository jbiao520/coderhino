package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.QueryEventSink;
import com.coderhino.query.QueryResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ObservedCoderhinoRun {
    public static final String CONFIGURED_SINK_INPUT = "Describe the configured QueryEventSink callback flow.";
    public static final String CONFIGURED_SINK_VISIBLE_INPUT = "Observe the auto-configured Spring sink callbacks.";
    public static final String REQUEST_SINK_INPUT = "Describe the request-level QueryEventSink override flow.";
    public static final String REQUEST_SINK_VISIBLE_INPUT = "Observe the request-level Spring sink override.";

    private ObservedCoderhinoRun() {
    }

    public static RecordingSink newRecorder() {
        return new RecordingSink();
    }

    public static CoderhinoAgent.AgentRequest configuredSinkRequest() {
        return request(CONFIGURED_SINK_INPUT, CONFIGURED_SINK_VISIBLE_INPUT, null);
    }

    public static CoderhinoAgent.AgentRequest requestSinkOverride(QueryEventSink requestSink) {
        return request(REQUEST_SINK_INPUT, REQUEST_SINK_VISIBLE_INPUT, Objects.requireNonNull(requestSink, "requestSink"));
    }

    public static CoderhinoAgent.AgentRequest request(String input, String visibleInput, QueryEventSink sink) {
        return new CoderhinoAgent.AgentRequest(input, visibleInput, sink, null);
    }

    public static CoderhinoAgent.AgentResult run(CoderhinoAgent agent) {
        return run(agent, configuredSinkRequest());
    }

    public static CoderhinoAgent.AgentResult run(CoderhinoAgent agent, CoderhinoAgent.AgentRequest request) {
        return Objects.requireNonNull(agent, "agent").run(Objects.requireNonNull(request, "request"));
    }

    public static Observation observe(CoderhinoAgent.AgentRequest request, CoderhinoAgent.AgentResult result, RecordingSink sink) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(sink, "sink");
        return new Observation(
            request.visibleInput(),
            result.finalText(),
            result.stopReason(),
            result.isSuccess(),
            result.isError(),
            result.iterationCount(),
            sink.textChunks(),
            sink.statuses(),
            sink.usages(),
            sink.errors(),
            sink.completedText()
        );
    }

    public record Observation(
        String visibleInput,
        String finalText,
        QueryResult.StopReason stopReason,
        boolean success,
        boolean error,
        int iterationCount,
        List<String> textChunks,
        List<String> statuses,
        List<UsageRecord> usages,
        List<String> errors,
        String completedText
    ) {
    }

    public static final class RecordingSink implements QueryEventSink {
        private final List<String> textChunks = new ArrayList<>();
        private final List<String> statuses = new ArrayList<>();
        private final List<UsageRecord> usages = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private String completedText;

        public List<String> textChunks() {
            return List.copyOf(textChunks);
        }

        public List<String> statuses() {
            return List.copyOf(statuses);
        }

        public List<UsageRecord> usages() {
            return List.copyOf(usages);
        }

        public List<String> errors() {
            return List.copyOf(errors);
        }

        public String completedText() {
            return completedText;
        }

        @Override
        public void onTextChunk(String chunk) {
            textChunks.add(chunk);
        }

        @Override
        public void onStatus(String message) {
            statuses.add(message);
        }

        @Override
        public void onToolCall(String toolName, String toolUseId, String argumentsJson) {
        }

        @Override
        public void onToolResult(String toolName, String toolUseId, String result) {
        }

        @Override
        public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
            usages.add(new UsageRecord(inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens));
        }

        @Override
        public void onError(String error) {
            errors.add(error);
        }

        @Override
        public void onCompleted(String finalText) {
            completedText = finalText;
        }
    }

    public record UsageRecord(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
    }
}
