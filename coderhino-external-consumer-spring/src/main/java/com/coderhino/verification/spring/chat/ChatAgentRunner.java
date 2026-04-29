package com.coderhino.verification.spring.chat;

import com.coderhino.agent.CoderhinoAgent;

public interface ChatAgentRunner {

    CoderhinoAgent.AgentResult run(String message);
}
