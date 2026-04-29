package com.coderhino.verification.spring.chat;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.types.PermissionMode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CoderhinoChatAgentRunner implements ChatAgentRunner {

    private final CoderhinoAgent agent;

    public CoderhinoChatAgentRunner(CoderhinoAgent agent) {
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    @Override
    public CoderhinoAgent.AgentResult run(String message) {
        var requestState = newRequestState();
        return agent.run(new CoderhinoAgent.AgentRequest(message, message, null, requestState));
    }

    private BootstrapState newRequestState() {
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
}
