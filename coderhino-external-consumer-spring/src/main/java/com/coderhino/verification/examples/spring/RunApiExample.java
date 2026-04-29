package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryResult;

import java.util.Objects;

public final class RunApiExample {
    public static final String STRING_INPUT = "Summarize how this Spring host wired CoderhinoAgent.";
    public static final String REQUEST_INPUT = "Describe the host-owned agent request flow.";
    public static final String REQUEST_VISIBLE_INPUT = "Visible host request for deterministic Spring verification.";

    private RunApiExample() {
    }

    public static CoderhinoAgent.AgentResult runWithString(CoderhinoAgent agent) {
        return Objects.requireNonNull(agent, "agent").run(STRING_INPUT);
    }

    public static CoderhinoAgent.AgentRequest defaultRequest() {
        return request(REQUEST_INPUT, REQUEST_VISIBLE_INPUT);
    }

    public static CoderhinoAgent.AgentRequest request(String input, String visibleInput) {
        return new CoderhinoAgent.AgentRequest(input, visibleInput, null, null);
    }

    public static CoderhinoAgent.AgentResult runWithRequest(CoderhinoAgent agent) {
        return runWithRequest(agent, defaultRequest());
    }

    public static CoderhinoAgent.AgentResult runWithRequest(CoderhinoAgent agent, CoderhinoAgent.AgentRequest request) {
        return Objects.requireNonNull(agent, "agent").run(Objects.requireNonNull(request, "request"));
    }

    public static RunObservation observe(CoderhinoAgent.AgentRequest request, CoderhinoAgent.AgentResult result) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
        return new RunObservation(
            request.visibleInput(),
            result.finalText(),
            result.isSuccess(),
            result.isError(),
            result.stopReason(),
            result.iterationCount(),
            result.usage()
        );
    }

    public record RunObservation(
        String visibleInput,
        String finalText,
        boolean success,
        boolean error,
        QueryResult.StopReason stopReason,
        int iterationCount,
        ModelResponse.Usage usage
    ) {
    }
}
