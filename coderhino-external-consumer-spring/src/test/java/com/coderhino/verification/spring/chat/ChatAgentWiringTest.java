package com.coderhino.verification.spring.chat;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration;
import com.coderhino.agent.spring.CoderhinoAgentCredentialProvider;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.query.AgentModelClient;
import com.coderhino.query.ModelClient;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.verification.spring.ExternalConsumerSpringApplication;
import com.coderhino.verification.examples.spring.DeterministicFakeModelClient;
import com.coderhino.verification.examples.spring.HardcodedCredentialProviderConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {
        ExternalConsumerSpringApplication.class,
        ChatAgentWiringTest.FakeModelClientConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class ChatAgentWiringTest {
    private static final String DETERMINISTIC_REPLY = "chat endpoint reply";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class));

    @Autowired
    private CoderhinoAgent agent;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private ToolCommandRegistry toolCommandRegistry;

    @Autowired
    private CoderhinoAgentCredentialProvider credentialProvider;

    @Autowired
    private DeterministicFakeModelClient fakeModelClient;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @LocalServerPort
    private int port;

    @Test
    void fullToolAndCommandRegistriesAreAvailable() {
        assertThat(port).isPositive();
        assertThat(agent.config().toolRegistry()).isSameAs(toolRegistry);
        assertThat(agent.config().commandRegistry()).isSameAs(toolCommandRegistry);
        assertThat(toolNames(toolRegistry)).containsExactlyElementsOf(toolNames(ToolRegistry.createDefault()));

        var expectedCommandRegistry = CommandRegistry.createDefault(Path.of("").toAbsolutePath().normalize());
        assertThat(commandNames(commandRegistry)).containsExactlyElementsOf(commandNames(expectedCommandRegistry));
        assertThat(commandNames(commandRegistry)).contains("help", "status", "commit");
        assertThat(toolCommandRegistry.find("help")).isPresent();
        assertThat(toolCommandRegistry.find("status")).isPresent();
        assertThat(toolCommandRegistry.find("commit")).isPresent();
        assertThat(toolCommandRegistry.find("queryOrder")).isNotPresent();
        assertThat(toolCommandRegistry.find("createMock")).isNotPresent();
        assertThat(credentialProvider.apiKey()).isEqualTo(HardcodedCredentialProviderConfiguration.EXAMPLE_API_KEY);
    }

    @Test
    void chatRouteIsMappedAndReturnsDeterministicReply() {
        var requestCountBefore = fakeModelClient.requestCount();

        var response = restTemplate.postForEntity("/chat", new ChatRequest("hello from route"), ChatResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().finalText()).isEqualTo(DETERMINISTIC_REPLY);
        assertThat(response.getBody().stopReason()).isEqualTo("END_TURN");
        assertThat(response.getBody().iterationCount()).isEqualTo(1);
        assertThat(response.getBody().success()).isTrue();
        assertThat(fakeModelClient.requestCount()).isEqualTo(requestCountBefore + 1);
        assertThat(fakeModelClient.lastRequest().messages())
            .anySatisfy(message -> assertThat(message.content()).isEqualTo("hello from route"));
        assertThat(handlerMapping.getHandlerMethods().keySet())
            .anySatisfy(info -> assertThat(info.getPatternValues()).contains("/chat"));
    }

    @Test
    void chatRouteReturnsStableJsonForMissingMessage() {
        var response = restTemplate.postForEntity("/chat", new ChatRequest(null), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("invalid_request");
        assertThat(response.getBody().message()).isEqualTo("message is required");
    }

    @Test
    void hardcodedCredentialProviderWinsOverPropertyValueForAutoConfiguredModelClient() {
        contextRunner
            .withUserConfiguration(ChatAgentConfiguration.class, HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration.class)
            .withPropertyValues(
                "coderhino.agent.api-key=file-should-not-win",
                "coderhino.agent.api-base-url=https://api.openai.example/v1",
                "coderhino.agent.provider-api-type=OPENAI",
                "coderhino.agent.model=test-model-from-file",
                "coderhino.agent.context-window=64000",
                "coderhino.agent.max-output-tokens=2048"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CoderhinoAgentCredentialProvider.class);
                assertThat(context.getBean(CoderhinoAgentCredentialProvider.class).apiKey())
                    .isEqualTo(HardcodedCredentialProviderConfiguration.EXAMPLE_API_KEY);

                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(context.getBean(ModelClient.class)).isInstanceOf(AgentModelClient.class);
                assertThat(modelClientApiKey(context.getBean(ModelClient.class)))
                    .isEqualTo(HardcodedCredentialProviderConfiguration.EXAMPLE_API_KEY);
            });
    }

    private static List<String> toolNames(ToolRegistry registry) {
        return registry.all().stream()
            .map(ToolDefinition::name)
            .toList();
    }

    private static List<String> commandNames(CommandRegistry registry) {
        return registry.all().stream()
            .map(com.coderhino.commands.CommandDefinition::name)
            .toList();
    }

    private static String modelClientApiKey(ModelClient modelClient) {
        try {
            Field apiKeyField = AgentModelClient.class.getDeclaredField("apiKey");
            apiKeyField.setAccessible(true);
            return (String) apiKeyField.get(modelClient);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect AgentModelClient apiKey", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelClientConfiguration {
        @Bean
        DeterministicFakeModelClient fakeModelClient() {
            return DeterministicFakeModelClient.replying(DETERMINISTIC_REPLY);
        }
    }
}
