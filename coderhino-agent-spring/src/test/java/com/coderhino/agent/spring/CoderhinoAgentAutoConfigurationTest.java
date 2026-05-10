package com.coderhino.agent.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.AgentModelClient;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.ProviderApiType;
import com.coderhino.query.QueryRequest;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.types.PermissionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CoderhinoAgentAutoConfigurationTest {
    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class));

    @Test
    void createsAgentFromProperties() {
        contextRunner
            .withPropertyValues(
                "coderhino.agent.model=test-model",
                "coderhino.agent.api-key=test-key",
                "coderhino.agent.permission-mode=BYPASS",
                "coderhino.agent.max-tool-iterations=7",
                "coderhino.agent.max-output-tokens=4096",
                "coderhino.agent.max-budget-usd=0.25",
                "coderhino.agent.append-system-prompt=extra guidance"
            )
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                var agent = context.getBean(CoderhinoAgent.class);
                assertThat(agent.config().modelClient()).isSameAs(context.getBean(ModelClient.class));
                assertThat(agent.config().model()).isEqualTo("test-model");
                assertThat(agent.config().permissionMode()).isEqualTo(PermissionMode.BYPASS);
                assertThat(agent.config().maxToolIterations()).isEqualTo(7);
                assertThat(agent.config().maxBudgetUsd()).isEqualTo(0.25);
                assertThat(agent.config().appendSystemPrompt()).isEqualTo("extra guidance");
            });
    }

    @Test
    void missingCredentialsFailureNamesSpringConfigurationOptions() {
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

    @Test
    void usesAnthropicApiKeyFallbackWhenSpringPropertyIsUnset() {
        contextRunner
            .withPropertyValues("ANTHROPIC_API_KEY=test-key")
            .run(context -> assertThat(context).hasSingleBean(ModelClient.class));
    }

    @Test
    void credentialProviderSuppliesApiKeyWhenPropertiesAreAbsent() {
        contextRunner
            .withUserConfiguration(ProviderSuppliesCredentialConfig.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("provider-key");
            });
    }

    @Test
    void credentialProviderOverridesCoderhinoAgentApiKeyProperty() {
        contextRunner
            .withUserConfiguration(ProviderSuppliesCredentialConfig.class)
            .withPropertyValues("coderhino.agent.api-key=property-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("provider-key");
            });
    }

    @Test
    void nullCredentialProviderValueFallsBackToCoderhinoAgentApiKeyProperty() {
        contextRunner
            .withUserConfiguration(NullCredentialProviderConfig.class)
            .withPropertyValues("coderhino.agent.api-key=property-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("property-key");
            });
    }

    @Test
    void coderhinoAgentApiKeyPropertyCanPointToFile() throws Exception {
        Path apiKeyFile = tempDir.resolve("api-key.txt");
        Files.writeString(apiKeyFile, " file-backed-key \n");

        contextRunner
            .withPropertyValues("coderhino.agent.api-key=" + apiKeyFile)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("file-backed-key");
            });
    }

    @Test
    void nonFileCoderhinoAgentApiKeyPropertyIsUsedAsLiteralKey() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=literal-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("literal-key");
            });
    }

    @Test
    void emptyCredentialProviderValueFallsBackToCoderhinoAgentApiKeyEnvironmentProperty() {
        contextRunner
            .withUserConfiguration(EmptyCredentialProviderConfig.class)
            .withPropertyValues("CODERHINO_AGENT_API_KEY=env-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("env-key");
            });
    }

    @Test
    void whitespaceCredentialProviderValueFallsBackToAnthropicApiKeyEnvironmentProperty() {
        contextRunner
            .withUserConfiguration(BlankCredentialProviderConfig.class)
            .withPropertyValues("ANTHROPIC_API_KEY=anthropic-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("anthropic-key");
            });
    }

    @Test
    void customModelClientBacksOffDefaultCredentialResolution() {
        contextRunner
            .withUserConfiguration(CustomBeans.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                var agent = context.getBean(CoderhinoAgent.class);
                assertThat(context.getBean(ModelClient.class)).isInstanceOf(StubModelClient.class);
                assertThat(agent.config().modelClient()).isSameAs(context.getBean(ModelClient.class));
                assertThat(agent.config().toolRegistry()).isSameAs(context.getBean(ToolRegistry.class));
                assertThat(agent.config().serviceRegistry()).isSameAs(context.getBean(ServiceRegistry.class));
                assertThat(agent.config().permissionChecker()).isSameAs(context.getBean(PermissionChecker.class));
            });
    }

    @Test
    void customModelClientStillBacksOffCredentialProviderResolution() {
        contextRunner
            .withUserConfiguration(CustomBeansWithCredentialProvider.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(context.getBean(ModelClient.class)).isInstanceOf(StubModelClient.class);
            });
    }

    @Test
    void customToolCommandRegistryIsPassedToAgent() {
        contextRunner
            .withUserConfiguration(CustomBeansWithToolCommandRegistry.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ToolCommandRegistry.class);
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                assertThat(context.getBean(CoderhinoAgent.class).config().commandRegistry())
                    .isSameAs(context.getBean(ToolCommandRegistry.class));
            });
    }

    @Test
    void primaryCredentialProviderWinsWhenMultipleProvidersExist() {
        contextRunner
            .withUserConfiguration(PrimaryAndSecondaryCredentialProvidersConfig.class)
            .withPropertyValues("coderhino.agent.api-key=property-key")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractApiKey(context.getBean(ModelClient.class))).isEqualTo("primary-provider-key");
            });
    }

    @Test
    void duplicateCredentialProvidersWithoutPrimaryFailWithSpringSemantics() {
        contextRunner
            .withUserConfiguration(DuplicateCredentialProvidersConfig.class)
            .run(context -> assertThat(context).hasFailed()
                .getFailure()
                .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class)
                .rootCause()
                .hasMessageContaining(CoderhinoAgentCredentialProvider.class.getName()));
    }

    @Test
    void exposesOnlyConfiguredToolSubset() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=test-key", "coderhino.agent.enabled-tools=read_file,grep")
            .run(context -> {
                var agent = context.getBean(CoderhinoAgent.class);
                var toolNames = agent.config().toolRegistry().all().stream()
                    .map(com.coderhino.tools.ToolDefinition::name)
                    .toList();

                assertThat(toolNames).containsExactly("read_file", "grep");
            });
    }

    @Test
    void defaultSpringToolsUseHardenedEmbeddedSet() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=test-key")
            .run(context -> {
                var agent = context.getBean(CoderhinoAgent.class);
                var toolNames = agent.config().toolRegistry().all().stream()
                    .map(com.coderhino.tools.ToolDefinition::name)
                    .toList();

                assertThat(toolNames).containsExactly("read_file", "glob", "grep");
            });
    }

    @Test
    void openAiProviderCreatesModelClient() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=test-key", "coderhino.agent.provider-api-type=OPENAI")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractBaseUrl(context.getBean(ModelClient.class))).isEqualTo(ProviderApiType.OPENAI.defaultBaseUrl());
            });
    }

    @Test
    void explicitApiBaseUrlWinsForOpenAiProvider() {
        contextRunner
            .withPropertyValues(
                "coderhino.agent.api-key=test-key",
                "coderhino.agent.provider-api-type=OPENAI",
                "coderhino.agent.api-base-url=https://custom-openai.example/v9/"
            )
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractBaseUrl(context.getBean(ModelClient.class))).isEqualTo("https://custom-openai.example/v9");
            });
    }

    @Test
    void explicitApiBaseUrlWinsForClaudeCodeProvider() {
        contextRunner
            .withPropertyValues(
                "coderhino.agent.api-key=test-key",
                "coderhino.agent.provider-api-type=CLAUDE_CODE",
                "coderhino.agent.api-base-url=https://custom-claude.example/messages/"
            )
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractBaseUrl(context.getBean(ModelClient.class))).isEqualTo("https://custom-claude.example/messages");
            });
    }

    @Test
    void openAiProviderUsesProviderAwareDefaultApiBaseUrlWhenUnset() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=test-key", "coderhino.agent.provider-api-type=OPENAI")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractBaseUrl(context.getBean(ModelClient.class)))
                    .startsWith(ProviderApiType.OPENAI.defaultBaseUrl());
            });
    }

    @Test
    void claudeCodeProviderUsesClaudeCompatibleDefaultApiBaseUrlWhenUnset() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=test-key", "coderhino.agent.provider-api-type=CLAUDE_CODE")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(ModelClient.class);
                assertThat(extractBaseUrl(context.getBean(ModelClient.class))).isEqualTo(ProviderApiType.CLAUDE_CODE.defaultBaseUrl());
                assertThat(extractBaseUrl(context.getBean(ModelClient.class))).doesNotStartWith(ProviderApiType.OPENAI.defaultBaseUrl());
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeans {
        @Bean
        ModelClient modelClient() {
            return new StubModelClient();
        }

        @Bean
        ToolRegistry toolRegistry() {
            return new ToolRegistry(List.of());
        }

        @Bean
        ServiceRegistry serviceRegistry() {
            return ServiceRegistry.createEmbeddedDefault();
        }

        @Bean
        PermissionChecker permissionChecker() {
            return new PermissionChecker();
        }
    }

    private static final class StubModelClient implements ModelClient {
        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            return new ModelResponse.AssistantReply("ok", new ModelResponse.Usage(1, 1));
        }
    }

    private static String extractApiKey(ModelClient modelClient) {
        assertThat(modelClient).isInstanceOf(AgentModelClient.class);
        try {
            Field apiKeyField = AgentModelClient.class.getDeclaredField("apiKey");
            apiKeyField.setAccessible(true);
            return (String) apiKeyField.get(modelClient);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to read AgentModelClient apiKey for precedence assertion", exception);
        }
    }

    private static String extractBaseUrl(ModelClient modelClient) {
        assertThat(modelClient).isInstanceOf(AgentModelClient.class);
        try {
            Field baseUrlField = AgentModelClient.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            return (String) baseUrlField.get(modelClient);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to read AgentModelClient baseUrl for precedence assertion", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProviderSuppliesCredentialConfig {
        @Bean
        CoderhinoAgentCredentialProvider credentialProvider() {
            return () -> "provider-key";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NullCredentialProviderConfig {
        @Bean
        CoderhinoAgentCredentialProvider credentialProvider() {
            return () -> null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyCredentialProviderConfig {
        @Bean
        CoderhinoAgentCredentialProvider credentialProvider() {
            return () -> "";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BlankCredentialProviderConfig {
        @Bean
        CoderhinoAgentCredentialProvider credentialProvider() {
            return () -> "   ";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeansWithCredentialProvider extends CustomBeans {
        @Bean
        CoderhinoAgentCredentialProvider credentialProvider() {
            return () -> "provider-key";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeansWithToolCommandRegistry extends CustomBeans {
        @Bean
        ToolCommandRegistry toolCommandRegistry() {
            return name -> Optional.empty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryAndSecondaryCredentialProvidersConfig {
        @Bean
        @Primary
        CoderhinoAgentCredentialProvider primaryCredentialProvider() {
            return () -> "primary-provider-key";
        }

        @Bean
        CoderhinoAgentCredentialProvider secondaryCredentialProvider() {
            return () -> "secondary-provider-key";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateCredentialProvidersConfig {
        @Bean
        CoderhinoAgentCredentialProvider firstCredentialProvider() {
            return () -> "first-provider-key";
        }

        @Bean
        CoderhinoAgentCredentialProvider secondCredentialProvider() {
            return () -> "second-provider-key";
        }
    }
}
