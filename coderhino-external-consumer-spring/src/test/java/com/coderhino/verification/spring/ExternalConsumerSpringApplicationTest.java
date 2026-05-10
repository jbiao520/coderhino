package com.coderhino.verification.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentCredentialProvider;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.query.ModelClient;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.coderhino.verification.examples.spring.DeterministicFakeModelClient;
import com.coderhino.agent.spring.CoderhinoAgentProperties;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {
        ExternalConsumerSpringApplication.class,
        ExternalConsumerSpringApplicationTest.FakeModelClientConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
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
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DeterministicFakeModelClient fakeModelClient;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private ToolCommandRegistry toolCommandRegistry;

    @Autowired
    private CoderhinoAgentCredentialProvider credentialProvider;

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
        assertThat(webApplicationContext).isNotNull();
        assertThat(fakeModelClient.requestCount()).isZero();
        assertThat(fakeModelClient.lastRequest()).isNull();
        assertThat(credentialProvider.apiKey()).isEqualTo("test-key-from-file");
        assertThat(agent.config().toolRegistry()).isSameAs(toolRegistry);
        assertThat(agent.config().toolRegistry().all())
            .extracting(ToolDefinition::name)
            .containsExactlyElementsOf(ToolRegistry.createDefault().all().stream().map(ToolDefinition::name).toList());
        assertThat(agent.config().commandRegistry()).isSameAs(toolCommandRegistry);
        assertThat(commandRegistry.all())
            .extracting(com.coderhino.commands.CommandDefinition::name)
            .containsExactlyElementsOf(
                CommandRegistry.createDefault(Path.of("").toAbsolutePath().normalize()).all().stream()
                    .map(com.coderhino.commands.CommandDefinition::name)
                    .toList()
            );
        assertThat(commandRegistry.find("help")).isPresent();
        assertThat(commandRegistry.find("status")).isPresent();
        assertThat(commandRegistry.find("commit")).isPresent();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelClientConfiguration {
        @Bean
        DeterministicFakeModelClient fakeModelClient() {
            return DeterministicFakeModelClient.replying("startup probe reply");
        }
    }
}
