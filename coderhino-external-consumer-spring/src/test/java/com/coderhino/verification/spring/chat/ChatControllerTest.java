package com.coderhino.verification.spring.chat;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatControllerTest {

    @Test
    void chatReturnsAgentResult() {
        var runner = new FakeChatAgentRunner(new CoderhinoAgent.AgentResult(
            "final reply",
            QueryResult.StopReason.END_TURN,
            2,
            new ModelResponse.Usage(1, 2, 0, 0),
            null,
            null
        ));
        var controller = new ChatController(runner);

        var response = controller.chat(new ChatRequest("hello"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        var body = assertInstanceOf(ChatResponse.class, response.getBody());
        assertEquals("final reply", body.finalText());
        assertEquals("END_TURN", body.stopReason());
        assertEquals(2, body.iterationCount());
        assertEquals(true, body.success());
        assertEquals("hello", runner.lastMessage);
    }

    @Test
    void chatRejectsBlankMessage() {
        var runner = new FakeChatAgentRunner(null);
        var controller = new ChatController(runner);

        var response = controller.chat(new ChatRequest("   "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("invalid_request", body.error());
        assertEquals("message is required", body.message());
        assertNull(runner.lastMessage);
    }

    @Test
    void chatRejectsMissingMessage() {
        var runner = new FakeChatAgentRunner(null);
        var controller = new ChatController(runner);

        var response = controller.chat(new ChatRequest(null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("invalid_request", body.error());
        assertEquals("message is required", body.message());
        assertNull(runner.lastMessage);
    }

    @Test
    void chatRejectsMissingBody() {
        var runner = new FakeChatAgentRunner(null);
        var controller = new ChatController(runner);

        var response = controller.chat(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("invalid_request", body.error());
        assertEquals("message is required", body.message());
        assertNull(runner.lastMessage);
    }

    @Test
    void chatReturnsStableErrorWhenRunnerFails() {
        var controller = new ChatController(message -> {
            throw new IllegalStateException("boom");
        });

        var response = controller.chat(new ChatRequest("hello"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("internal_error", body.error());
        assertEquals("chat request failed", body.message());
    }

    private static final class FakeChatAgentRunner implements ChatAgentRunner {
        private final CoderhinoAgent.AgentResult result;
        private String lastMessage;

        private FakeChatAgentRunner(CoderhinoAgent.AgentResult result) {
            this.result = result;
        }

        @Override
        public CoderhinoAgent.AgentResult run(String message) {
            this.lastMessage = message;
            return result;
        }
    }
}
