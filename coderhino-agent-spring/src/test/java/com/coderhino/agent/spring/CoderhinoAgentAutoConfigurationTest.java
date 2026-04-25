package com.coderhino.agent.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryRequest;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.PermissionMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoderhinoAgentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class));

    @Test
    void createsAgentFromProperties() {
        contextRunner
            .withPropertyValues(
                "coderhino.agent.model=test-model",
                "coderhino.agent.permission-mode=BYPASS",
                "coderhino.agent.max-tool-iterations=7",
                "coderhino.agent.max-budget-usd=0.25",
                "coderhino.agent.append-system-prompt=extra guidance"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                var agent = context.getBean(CoderhinoAgent.class);
                assertThat(agent.config().model()).isEqualTo("test-model");
                assertThat(agent.config().permissionMode()).isEqualTo(PermissionMode.BYPASS);
                assertThat(agent.config().maxToolIterations()).isEqualTo(7);
                assertThat(agent.config().maxBudgetUsd()).isEqualTo(0.25);
                assertThat(agent.config().appendSystemPrompt()).isEqualTo("extra guidance");
            });
    }

    @Test
    void customBeansOverrideDefaults() {
        contextRunner
            .withUserConfiguration(CustomBeans.class)
            .run(context -> {
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                var agent = context.getBean(CoderhinoAgent.class);
                assertThat(agent.config().modelClient()).isSameAs(context.getBean(ModelClient.class));
                assertThat(agent.config().toolRegistry()).isSameAs(context.getBean(ToolRegistry.class));
                assertThat(agent.config().serviceRegistry()).isSameAs(context.getBean(ServiceRegistry.class));
                assertThat(agent.config().permissionChecker()).isSameAs(context.getBean(PermissionChecker.class));
            });
    }

    @Test
    void exposesOnlyConfiguredToolSubset() {
        contextRunner
            .withPropertyValues("coderhino.agent.enabled-tools=read_file,grep")
            .run(context -> {
                var agent = context.getBean(CoderhinoAgent.class);
                var toolNames = agent.config().toolRegistry().all().stream()
                    .map(com.coderhino.tools.ToolDefinition::name)
                    .toList();

                assertThat(toolNames).containsExactly("read_file", "grep");
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
}
