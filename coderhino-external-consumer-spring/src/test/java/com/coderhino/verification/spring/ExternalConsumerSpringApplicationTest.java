package com.coderhino.verification.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.AgentModelClient;
import com.coderhino.query.ModelClient;
import com.coderhino.tools.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ExternalConsumerSpringApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "coderhino.agent.model=test-model",
        "coderhino.agent.api-key=test-key"
    }
)
class ExternalConsumerSpringApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CoderhinoAgent agent;

    @Autowired
    private ModelClient modelClient;

    @Test
    void contextCreatesAgentUsingRealModelClient() {
        assertThat(applicationContext.getBean(CoderhinoAgent.class)).isSameAs(agent);
        assertThat(agent.config().model()).isEqualTo("test-model");
        assertThat(agent.config().modelClient()).isSameAs(modelClient);
        assertThat(modelClient).isInstanceOf(AgentModelClient.class);
        assertThat(agent.config().toolRegistry().all())
            .extracting(ToolDefinition::name)
            .containsExactly("read_file", "glob", "grep");
    }
}
