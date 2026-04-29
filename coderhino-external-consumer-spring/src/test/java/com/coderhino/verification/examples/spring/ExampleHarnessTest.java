package com.coderhino.verification.examples.spring;

import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryRequest;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExampleHarnessTest {

    @Test
    void fakeModelClientReturnsDeterministicAssistantReplyAndCapturesRequests() {
        var client = DeterministicFakeModelClient.replying("fixture reply", new ModelResponse.Usage(3, 2));
        var state = bootstrapState();
        var request = new QueryRequest(List.of(), "system", null, null, List.of());

        var response = client.complete(state, request);

        assertThat(response).isEqualTo(new ModelResponse.AssistantReply("fixture reply", new ModelResponse.Usage(3, 2)));
        assertThat(client.requestCount()).isEqualTo(1);
        assertThat(client.lastRequest()).isSameAs(request);
    }

    @Test
    void fakeModelClientCanFailDeterministically() {
        var failure = new IllegalStateException("boom");
        var client = DeterministicFakeModelClient.failing(failure);

        assertThatThrownBy(() -> client.complete(bootstrapState(), new QueryRequest(List.of(), "system", null, null, List.of())))
            .isSameAs(failure);
        assertThat(client.requestCount()).isEqualTo(1);
    }

    @Test
    void recordingEventSinkCapturesAllSupportedCallbacks() {
        var sink = new RecordingQueryEventSink();
        sink.answerNextQuestionWith("yes");

        sink.onTextChunk("hello");
        sink.onThinkingDelta("think");
        sink.onToolInputDelta("read_file", "tool-1", "{\"path\":\"README.md\"}");
        var answer = sink.onAskUserQuestion("tool-2", "Continue?", List.of("yes", "no"));
        sink.onStatus("running");
        sink.onToolCall("read_file", "tool-1", "{\"path\":\"README.md\"}");
        sink.onToolResult("read_file", "tool-1", "contents");
        sink.onUsage(5, 2, 1, 0);
        sink.onError("none");
        sink.onCompleted("done");

        assertThat(answer).isEqualTo("yes");
        assertThat(sink.textChunks()).containsExactly("hello");
        assertThat(sink.thinkingDeltas()).containsExactly("think");
        assertThat(sink.toolInputDeltas()).containsExactly(new RecordingQueryEventSink.ToolInputDeltaRecord("read_file", "tool-1", "{\"path\":\"README.md\"}"));
        assertThat(sink.questions()).containsExactly(new RecordingQueryEventSink.AskUserQuestionRecord("tool-2", "Continue?", List.of("yes", "no")));
        assertThat(sink.statuses()).containsExactly(new RecordingQueryEventSink.StatusRecord("running"));
        assertThat(sink.toolCalls()).containsExactly(new RecordingQueryEventSink.ToolCallRecord("read_file", "tool-1", "{\"path\":\"README.md\"}"));
        assertThat(sink.toolResults()).containsExactly(new RecordingQueryEventSink.ToolResultRecord("read_file", "tool-1", "contents"));
        assertThat(sink.usages()).containsExactly(new RecordingQueryEventSink.UsageRecord(5, 2, 1, 0));
        assertThat(sink.errors()).containsExactly("none");
        assertThat(sink.completedText()).isEqualTo("done");
    }

    private static BootstrapState bootstrapState() {
        return new BootstrapState(new AppState(
            false,
            "test-model",
            ".",
            false,
            true,
            com.coderhino.types.PermissionMode.BYPASS,
            0.0,
            SessionRuntime.create(),
            List.of()
        ));
    }
}
