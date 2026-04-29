package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.runtime.ToolBootstrapState;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolingConfigurationExampleTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class));

    @Test
    void defaultSpringToolsExposeOnlyEmbeddedSafeReadOnlySet() {
        contextRunner
            .withUserConfiguration(FakeModelClientConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                assertThat(toolNames(context.getBean(CoderhinoAgent.class).config().toolRegistry()))
                    .containsExactlyElementsOf(ToolingConfigurationExample.SAFE_DEFAULT_TOOL_NAMES);
            });
    }

    @Test
    void enabledToolsPropertyNarrowsBuiltInsToExactConfiguredSubset() {
        contextRunner
            .withUserConfiguration(FakeModelClientConfiguration.class)
            .withPropertyValues(ToolingConfigurationExample.ENABLED_TOOLS_PROPERTY + "=read_file,grep")
            .run(context -> {
                assertThat(context).hasSingleBean(CoderhinoAgent.class);
                assertThat(toolNames(context.getBean(CoderhinoAgent.class).config().toolRegistry()))
                    .containsExactlyElementsOf(ToolingConfigurationExample.NARROWED_TOOL_NAMES);
            });
    }

    @Test
    void hostToolOverrideAddsHostEchoWithoutEnablingBroadBuiltIns() throws Exception {
        contextRunner
            .withUserConfiguration(
                FakeModelClientConfiguration.class,
                ToolingConfigurationExample.HostToolRegistryOverrideConfiguration.class
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ToolRegistry.class);
                assertThat(context).hasSingleBean(HostEchoTool.class);
                assertThat(context).hasSingleBean(OrderQueryTool.class);

                var registry = context.getBean(ToolRegistry.class);
                var toolNames = toolNames(registry);
                assertThat(toolNames)
                    .containsExactly("read_file", "glob", "grep", HostEchoTool.TOOL_NAME, OrderQueryTool.TOOL_NAME);

                var hostEchoTool = context.getBean(HostEchoTool.class);
                assertThat(registry.find(HostEchoTool.TOOL_NAME)).containsSame(hostEchoTool);
                assertThat(hostEchoTool.execute(new HostEchoTool.Input("hello from host"), toolContext()))
                    .isEqualTo("host:hello from host");

                var orderQueryTool = context.getBean(OrderQueryTool.class);
                assertThat(registry.find(OrderQueryTool.TOOL_NAME)).containsSame(orderQueryTool);
                assertThat(orderQueryTool.execute(new OrderQueryTool.Input(" order-123 "), toolContext()).orderId())
                    .isEqualTo("order-123");

                var agent = context.getBean(CoderhinoAgent.class);
                assertThat(agent.config().toolRegistry()).isSameAs(registry);
                assertThat(toolNames(agent.config().toolRegistry()))
                    .containsExactly("read_file", "glob", "grep", HostEchoTool.TOOL_NAME, OrderQueryTool.TOOL_NAME);
            });
    }

    @Test
    void hostEchoToolIsReadOnlyAndUsesMatchingInputSchema() throws Exception {
        var tool = new HostEchoTool();

        assertThat(tool.name()).isEqualTo(HostEchoTool.TOOL_NAME);
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.inputSchema().type()).isEqualTo("object");
        assertThat(tool.inputSchema().properties())
            .containsExactly(Map.entry("message", Map.of("type", "string")));

        var denied = tool.validate(new HostEchoTool.Input("   "), toolContext());
        assertThat(denied).isInstanceOf(PermissionResult.Deny.class);
        assertThat(((PermissionResult.Deny) denied).reason()).isEqualTo("message must not be blank.");

        var allowed = tool.validate(new HostEchoTool.Input("echo me"), toolContext());
        assertThat(allowed.allowed()).isTrue();
        assertThat(tool.execute(new HostEchoTool.Input(" echo me "), toolContext())).isEqualTo("host:echo me");
    }

    @Test
    void orderQueryToolIsReadOnlyAndReturnsMockOrder() throws Exception {
        var tool = new OrderQueryTool();

        assertThat(tool.name()).isEqualTo(OrderQueryTool.TOOL_NAME);
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.inputSchema().type()).isEqualTo("object");
        assertThat(tool.inputSchema().properties())
            .containsExactly(Map.entry("orderId", Map.of("type", "string")));

        var denied = tool.validate(new OrderQueryTool.Input("   "), toolContext());
        assertThat(denied).isInstanceOf(PermissionResult.Deny.class);
        assertThat(((PermissionResult.Deny) denied).reason()).isEqualTo("orderId must not be blank.");

        var allowed = tool.validate(new OrderQueryTool.Input("order-123"), toolContext());
        assertThat(allowed.allowed()).isTrue();

        var output = tool.execute(new OrderQueryTool.Input(" order-123 "), toolContext());
        assertThat(output.orderId()).isEqualTo("order-123");
        assertThat(output.status()).isEqualTo("MOCK_CONFIRMED");
        assertThat(output.customerName()).isEqualTo("Ada Lovelace");
        assertThat(output.currency()).isEqualTo("USD");
        assertThat(output.total()).isEqualTo("129.99");
        assertThat(output.lineItems())
            .containsExactly(new OrderQueryTool.LineItem("SKU-COFFEE-001", "Coderhino Coffee Beans", 1));
    }

    private static List<String> toolNames(ToolRegistry registry) {
        return registry.all().stream()
            .map(com.coderhino.tools.ToolDefinition::name)
            .toList();
    }

    private static ToolContext toolContext() {
        return new ToolContext(toolBootstrapState(), PermissionMode.BYPASS, null, null, null, null);
    }

    private static ToolBootstrapState toolBootstrapState() {
        var cwd = Path.of(".").toAbsolutePath().normalize().toString();
        return new ToolBootstrapState() {
            @Override
            public String cwd() {
                return cwd;
            }

            @Override
            public UUID sessionId() {
                return UUID.fromString("00000000-0000-0000-0000-000000000004");
            }

            @Override
            public void updatePermissionMode(PermissionMode permissionMode) {
                // No-op for deterministic example execution tests.
            }
        };
    }
}
