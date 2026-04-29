package com.coderhino.verification.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.ModelClient;
import com.coderhino.tools.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import com.coderhino.verification.examples.spring.DeterministicFakeModelClient;
import com.coderhino.agent.spring.CoderhinoAgentProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {
        ExternalConsumerSpringApplication.class,
        ExternalConsumerSpringApplicationTest.FakeModelClientConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ExternalConsumerSpringApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CoderhinoAgent agent;

    @Autowired
    private ModelClient modelClient;

    @Autowired
    private CoderhinoAgentProperties properties;

    @Autowired
    private DeterministicFakeModelClient fakeModelClient;

    @Test
    void contextCreatesAgentUsingLocalFakeModelClient() {
        assertThat(applicationContext.getBean(CoderhinoAgent.class)).isSameAs(agent);
        assertThat(agent.config().model()).isEqualTo("test-model-from-file");
        assertThat(agent.config().modelClient()).isSameAs(modelClient);
        assertThat(modelClient).isSameAs(fakeModelClient);
        assertThat(properties.getApiKey()).isEqualTo("test-key-from-file");
        assertThat(properties.getApiBaseUrl()).isEqualTo("https://api.openai.example/v1");
        assertThat(properties.getProviderApiType()).isEqualTo(CoderhinoAgentProperties.ProviderApiType.OPENAI);
        assertThat(properties.getContextWindow()).isEqualTo(64000L);
        assertThat(properties.getMaxOutputTokens()).isEqualTo(2048L);
        assertThat(fakeModelClient.requestCount()).isEqualTo(1);
        assertThat(fakeModelClient.lastRequest()).isNotNull();
        assertThat(agent.config().toolRegistry().all())
            .extracting(ToolDefinition::name)
            .containsExactly("read_file", "glob", "grep");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelClientConfiguration {
        @Bean
        DeterministicFakeModelClient fakeModelClient() {
            return DeterministicFakeModelClient.replying("startup probe reply");
        }
    }
}
