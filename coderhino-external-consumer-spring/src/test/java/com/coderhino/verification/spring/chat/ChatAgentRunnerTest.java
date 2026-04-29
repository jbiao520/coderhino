package com.coderhino.verification.spring.chat;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryRequest;
import com.coderhino.state.BootstrapState;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatAgentRunnerTest {

    @Test
    void runUsesFreshRequestOwnedStatePerInvocation() {
        var modelClient = new CapturingModelClient();
        var agent = CoderhinoAgent.builder()
            .modelClient(modelClient)
            .permissionMode(PermissionMode.BYPASS)
            .build();
        var runner = new CoderhinoChatAgentRunner(agent);

        var first = runner.run("alpha");
        var second = runner.run("beta");

        assertEquals(2, modelClient.requests.size());
        assertEquals(2, modelClient.bootstrapStates.size());
        assertNotSame(agent.bootstrapState(), modelClient.bootstrapStates.get(0));
        assertNotSame(agent.bootstrapState(), modelClient.bootstrapStates.get(1));
        assertNotSame(modelClient.bootstrapStates.get(0), modelClient.bootstrapStates.get(1));
        assertTrue(agent.state().messages().isEmpty());

        assertSame(modelClient.bootstrapStates.get(0), first.bootstrapState());
        assertSame(modelClient.bootstrapStates.get(1), second.bootstrapState());

        assertRequestAndResult(modelClient.requests.get(0), first, "alpha", "reply-1");
        assertRequestAndResult(modelClient.requests.get(1), second, "beta", "reply-2");
    }

    private static void assertRequestAndResult(QueryRequest request,
                                               CoderhinoAgent.AgentResult result,
                                               String expectedInput,
                                               String expectedReply) {
        assertEquals(1, request.messages().size());
        var requestMessage = assertInstanceOf(Message.UserMessage.class, request.messages().get(0));
        assertEquals(expectedInput, requestMessage.content());

        assertTrue(result.isSuccess());
        assertEquals(2, result.state().messages().size());
        var userMessage = assertInstanceOf(Message.UserMessage.class, result.state().messages().get(0));
        assertEquals(expectedInput, userMessage.content());
        var assistantMessage = assertInstanceOf(Message.AssistantMessage.class, result.state().messages().get(1));
        assertEquals(expectedReply, assistantMessage.content());
    }

    private static final class CapturingModelClient implements ModelClient {
        private final List<BootstrapState> bootstrapStates = new ArrayList<>();
        private final List<QueryRequest> requests = new ArrayList<>();
        private int replies;

        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            bootstrapStates.add(bootstrapState);
            requests.add(request);
            replies++;
            return new ModelResponse.AssistantReply("reply-" + replies, new ModelResponse.Usage(1, replies));
        }
    }
}
