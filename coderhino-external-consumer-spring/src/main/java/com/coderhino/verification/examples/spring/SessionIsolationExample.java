package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.QueryResult;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.types.PermissionMode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SessionIsolationExample {
    public static final String REQUEST_INPUT = "Describe why this run uses request-owned BootstrapState.";
    public static final String REQUEST_VISIBLE_INPUT = "Session-isolated Spring request visible to the host.";

    private SessionIsolationExample() {
    }

    public static BootstrapState newSessionState(CoderhinoAgent agent) {
        Objects.requireNonNull(agent, "agent");
        return new BootstrapState(new AppState(
            false,
            agent.config().model(),
            agent.config().cwd().toString(),
            false,
            true,
            agent.config().permissionMode() == null ? PermissionMode.DEFAULT : agent.config().permissionMode(),
            0.0,
            SessionRuntime.create(),
            List.of()
        ));
    }

    public static CoderhinoAgent.AgentRequest isolatedRequest(BootstrapState requestState) {
        return new CoderhinoAgent.AgentRequest(
            REQUEST_INPUT,
            REQUEST_VISIBLE_INPUT,
            null,
            Objects.requireNonNull(requestState, "requestState")
        );
    }

    public static CoderhinoAgent.AgentResult run(CoderhinoAgent agent, BootstrapState requestState) {
        return Objects.requireNonNull(agent, "agent").run(isolatedRequest(requestState));
    }

    public static IsolationObservation observe(AppState managedBeforeRun, CoderhinoAgent agent, BootstrapState requestState, CoderhinoAgent.AgentResult result) {
        Objects.requireNonNull(managedBeforeRun, "managedBeforeRun");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(requestState, "requestState");
        Objects.requireNonNull(result, "result");

        var managedAfterRun = agent.state();
        var requestAfterRun = requestState.get();
        return new IsolationObservation(
            managedBeforeRun.sessionRuntime().sessionId(),
            managedAfterRun.sessionRuntime().sessionId(),
            requestAfterRun.sessionRuntime().sessionId(),
            managedBeforeRun.messages().size(),
            managedAfterRun.messages().size(),
            requestAfterRun.messages().size(),
            result.finalText(),
            result.stopReason(),
            managedAfterRun.equals(managedBeforeRun),
            !requestAfterRun.messages().isEmpty()
        );
    }

    public record IsolationObservation(
        UUID managedSessionIdBeforeRun,
        UUID managedSessionIdAfterRun,
        UUID requestSessionId,
        int managedMessageCountBeforeRun,
        int managedMessageCountAfterRun,
        int requestMessageCountAfterRun,
        String finalText,
        QueryResult.StopReason stopReason,
        boolean managedStateUnchanged,
        boolean requestStateCapturedMessages
    ) {
    }
}
