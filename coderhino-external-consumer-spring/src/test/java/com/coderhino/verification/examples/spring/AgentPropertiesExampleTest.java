package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration;
import com.coderhino.agent.spring.CoderhinoAgentProperties;
import com.coderhino.query.ModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPropertiesExampleTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class));

    @Test
    void propertyMatrixCoversPublicSpringConsumerProperties() {
        assertThat(AgentPropertiesExample.PROPERTY_MATRIX)
            .extracting(AgentPropertiesExample.PropertySpec::key)
            .containsExactly(
                AgentPropertiesExample.MODEL,
                AgentPropertiesExample.CWD,
                AgentPropertiesExample.PERMISSION_MODE,
                AgentPropertiesExample.ENABLED_TOOLS,
                AgentPropertiesExample.CUSTOM_SYSTEM_PROMPT,
                AgentPropertiesExample.APPEND_SYSTEM_PROMPT,
                AgentPropertiesExample.MAX_TOOL_ITERATIONS,
                AgentPropertiesExample.MAX_BUDGET_USD,
                AgentPropertiesExample.EMBEDDED_INTEGRATIONS_ENABLED,
                AgentPropertiesExample.API_KEY,
                AgentPropertiesExample.API_BASE_URL,
                AgentPropertiesExample.PROVIDER_API_TYPE,
                AgentPropertiesExample.CONTEXT_WINDOW,
                AgentPropertiesExample.MAX_OUTPUT_TOKENS
            );

        assertThat(AgentPropertiesExample.PROPERTY_MATRIX)
            .filteredOn(spec -> spec.key().equals(AgentPropertiesExample.EMBEDDED_INTEGRATIONS_ENABLED))
            .singleElement()
            .extracting(AgentPropertiesExample.PropertySpec::wiringStatus, AgentPropertiesExample.PropertySpec::autoConfigurationBehavior)
            .containsExactly("present_not_wired", "Present and bindable on CoderhinoAgentProperties, but not consumed by current auto-configuration");

        assertThat(AgentPropertiesExample.PROPERTY_MATRIX)
            .filteredOn(spec -> spec.key().equals(AgentPropertiesExample.API_BASE_URL))
            .singleElement()
            .extracting(AgentPropertiesExample.PropertySpec::defaultValue, AgentPropertiesExample.PropertySpec::autoConfigurationBehavior)
            .containsExactly("provider default when omitted", "Applied to the default ModelClient; explicit values override provider-aware defaults");
    }

    @Test
    void applicationPropertiesSnippetDocumentsPrimaryLiveConfigurationKeys() {
        assertThat(AgentPropertiesExample.APPLICATION_PROPERTIES_SNIPPET)
            .contains("coderhino.agent.provider-api-type=OPENAI")
            .contains("coderhino.agent.model=gpt-4.1-mini")
            .contains("coderhino.agent.context-window=65536")
            .contains("coderhino.agent.max-output-tokens=4096")
            .contains("coderhino.agent.api-base-url=https://api.openai.com/v1")
            .contains("coderhino.agent.api-key=replace-with-your-api-key");

        assertThat(AgentPropertiesExample.recommendedConfiguration())
            .containsEntry(AgentPropertiesExample.API_KEY, "replace-with-your-api-key")
            .containsEntry(AgentPropertiesExample.API_BASE_URL, "https://api.openai.com/v1")
            .containsEntry(AgentPropertiesExample.PROVIDER_API_TYPE, CoderhinoAgentProperties.ProviderApiType.OPENAI.name())
            .containsEntry(AgentPropertiesExample.MODEL, "gpt-4.1-mini")
            .containsEntry(AgentPropertiesExample.CONTEXT_WINDOW, "65536")
            .containsEntry(AgentPropertiesExample.MAX_OUTPUT_TOKENS, "4096");
    }

    @Test
    void credentialPrecedenceDocumentsSupportedSpringResolutionOrder() {
        assertThat(AgentPropertiesExample.CREDENTIAL_PRECEDENCE)
            .extracting(
                AgentPropertiesExample.CredentialPrecedenceSpec::order,
                AgentPropertiesExample.CredentialPrecedenceSpec::source
            )
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "Custom ModelClient bean"),
                org.assertj.core.groups.Tuple.tuple(2, "CoderhinoAgentCredentialProvider bean"),
                org.assertj.core.groups.Tuple.tuple(3, AgentPropertiesExample.API_KEY + " or " + AgentPropertiesExample.ENV_CODERHINO_AGENT_API_KEY),
                org.assertj.core.groups.Tuple.tuple(4, AgentPropertiesExample.ENV_ANTHROPIC_API_KEY),
                org.assertj.core.groups.Tuple.tuple(5, "Fail fast")
            );
    }

    @Test
    void credentialProviderExampleStaysSpringIdiomatic() {
        assertThat(AgentPropertiesExample.CREDENTIAL_PROVIDER_EXAMPLE)
            .contains("@Configuration(proxyBeanMethods = false)")
            .contains("@Bean")
            .contains("CoderhinoAgentCredentialProvider")
            .contains("ExternalSecretService")
            .contains("lookupCoderhinoApiKey");
    }

    @Test
    void bindsRecipePropertiesIntoPropertiesBeanAndAgent() {
        contextRunner
            .withUserConfiguration(FakeModelClientConfig.class)
            .withInitializer(context -> TestPropertySourceUtils.addPropertiesFilesToEnvironment(context, "classpath:application-test.properties"))
            .withPropertyValues(
                AgentPropertiesExample.CWD + "=./coderhino-external-consumer-spring",
                AgentPropertiesExample.PERMISSION_MODE + "=BYPASS",
                AgentPropertiesExample.ENABLED_TOOLS + "=read_file,grep",
                AgentPropertiesExample.CUSTOM_SYSTEM_PROMPT + "=Host-owned system prompt",
                AgentPropertiesExample.APPEND_SYSTEM_PROMPT + "=Append this guidance",
                AgentPropertiesExample.MAX_TOOL_ITERATIONS + "=9",
                AgentPropertiesExample.MAX_BUDGET_USD + "=1.75",
                AgentPropertiesExample.EMBEDDED_INTEGRATIONS_ENABLED + "=true"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CoderhinoAgentProperties.class);
                assertThat(context).hasSingleBean(CoderhinoAgent.class);

                var properties = context.getBean(CoderhinoAgentProperties.class);
                var agent = context.getBean(CoderhinoAgent.class);

                assertThat(properties.getApiKey()).isEqualTo("test-key-from-file");
                assertThat(properties.getModel()).isEqualTo("test-model-from-file");
                assertThat(properties.getCwd()).isEqualTo(Path.of("./coderhino-external-consumer-spring").toAbsolutePath().normalize());
                assertThat(properties.getPermissionMode()).isEqualTo(com.coderhino.types.PermissionMode.BYPASS);
                assertThat(properties.getEnabledTools()).containsExactly("read_file", "grep");
                assertThat(properties.getCustomSystemPrompt()).isEqualTo("Host-owned system prompt");
                assertThat(properties.getAppendSystemPrompt()).isEqualTo("Append this guidance");
                assertThat(properties.getMaxToolIterations()).isEqualTo(9);
                assertThat(properties.getMaxBudgetUsd()).isEqualTo(1.75);
                assertThat(properties.isEmbeddedIntegrationsEnabled()).isTrue();
                assertThat(properties.getApiBaseUrl()).isEqualTo("https://api.openai.example/v1");
                assertThat(properties.getProviderApiType()).isEqualTo(CoderhinoAgentProperties.ProviderApiType.OPENAI);
                assertThat(properties.getContextWindow()).isEqualTo(64000L);
                assertThat(properties.getMaxOutputTokens()).isEqualTo(2048L);

                assertThat(agent.config().model()).isEqualTo("test-model-from-file");
                assertThat(agent.config().cwd()).isEqualTo(Path.of("./coderhino-external-consumer-spring").toAbsolutePath().normalize());
                assertThat(agent.config().permissionMode()).isEqualTo(com.coderhino.types.PermissionMode.BYPASS);
                assertThat(agent.config().customSystemPrompt()).isEqualTo("Host-owned system prompt");
                assertThat(agent.config().appendSystemPrompt()).isEqualTo("Append this guidance");
                assertThat(agent.config().maxToolIterations()).isEqualTo(9);
                assertThat(agent.config().maxBudgetUsd()).isEqualTo(1.75);
                assertThat(agent.config().toolRegistry().all())
                    .extracting(com.coderhino.tools.ToolDefinition::name)
                    .containsExactly("read_file", "grep");
                assertThat(agent.config().modelClient()).isSameAs(context.getBean(DeterministicFakeModelClient.class));
            });
    }

    @Test
    void defaultSpringToolsStayOnSafeEmbeddedSetWhenNoToolListIsProvided() {
        contextRunner
            .withUserConfiguration(FakeModelClientConfig.class)
            .run(context -> {
                var agent = context.getBean(CoderhinoAgent.class);
                assertThat(agent.config().toolRegistry().all())
                    .extracting(com.coderhino.tools.ToolDefinition::name)
                    .containsExactly("read_file", "glob", "grep");
            });
    }

    @Test
    void customModelClientBeanAvoidsCredentialRequirements() {
        contextRunner
            .withUserConfiguration(FakeModelClientConfig.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(context.getBean(CoderhinoAgent.class).config().modelClient())
                    .isSameAs(context.getBean(DeterministicFakeModelClient.class));
            });
    }

    @Test
    void usesAnthropicApiKeyFallbackWhenSpringPropertyIsUnset() {
        contextRunner
            .withPropertyValues("ANTHROPIC_API_KEY=test-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
            });
    }

    @Test
    void createsModelClientForOpenAiProviderType() {
        contextRunner
            .withPropertyValues(
                AgentPropertiesExample.API_KEY + "=test-key",
                AgentPropertiesExample.PROVIDER_API_TYPE + "=OPENAI",
                AgentPropertiesExample.MODEL + "=test-openai-model"
            )
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(context.getBean(CoderhinoAgentProperties.class).getProviderApiType())
                    .isEqualTo(CoderhinoAgentProperties.ProviderApiType.OPENAI);
            });
    }

    @Test
    void missingCredentialsGuidanceNamesSupportedConfigurationPaths() {
        contextRunner
            .run(context -> assertThat(context).hasFailed()
                .getFailure()
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("CoderhinoAgentCredentialProvider")
                .hasMessageContaining("coderhino.agent.api-key")
                .hasMessageContaining("CODERHINO_AGENT_API_KEY")
                .hasMessageContaining("ANTHROPIC_API_KEY")
                .hasMessageContaining("custom ModelClient"));
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeModelClientConfig {
        @Bean
        DeterministicFakeModelClient deterministicFakeModelClient() {
            return DeterministicFakeModelClient.replying("spring recipe ok");
        }
    }
}
