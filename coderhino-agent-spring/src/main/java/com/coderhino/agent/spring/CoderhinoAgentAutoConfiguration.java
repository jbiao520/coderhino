package com.coderhino.agent.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEventSink;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.tools.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(CoderhinoAgentProperties.class)
public class CoderhinoAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistry coderhinoServiceRegistry(CoderhinoAgentProperties properties) {
        return ServiceRegistry.createEmbeddedDefault(properties.getCwd());
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry coderhinoToolRegistry(CoderhinoAgentProperties properties) {
        if (properties.getEnabledTools().isEmpty()) {
            return ToolRegistry.createEmbeddedDefault();
        }
        return ToolRegistry.createDefault().filtered(properties.getEnabledTools());
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionChecker coderhinoPermissionChecker() {
        return new PermissionChecker();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelClient coderhinoModelClient(CoderhinoAgentProperties properties, Environment environment) {
        var apiKey = firstNonBlank(properties.getApiKey(), environment.getProperty("ANTHROPIC_API_KEY"));
        if (apiKey == null) {
            throw new IllegalStateException(
                "Model API credentials are required. Set coderhino.agent.api-key, "
                    + "CODERHINO_AGENT_API_KEY, ANTHROPIC_API_KEY, or inject a custom ModelClient "
                    + "for local/test behavior."
            );
        }

        return ModelClientFactory.create(
            properties.getModel(),
            apiKey,
            properties.getApiBaseUrl(),
            properties.getProviderApiType().toRuntimeType(),
            properties.getContextWindow(),
            properties.getMaxOutputTokens()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CoderhinoAgent coderhinoAgent(
        CoderhinoAgentProperties properties,
        ModelClient modelClient,
        ToolRegistry toolRegistry,
        ServiceRegistry serviceRegistry,
        PermissionChecker permissionChecker,
        ObjectProvider<QueryEventSink> eventSink
    ) {
        var builder = CoderhinoAgent.builder()
            .model(properties.getModel())
            .modelClient(modelClient)
            .cwd(properties.getCwd())
            .permissionMode(properties.getPermissionMode())
            .toolRegistry(toolRegistry)
            .serviceRegistry(serviceRegistry)
            .permissionChecker(permissionChecker)
            .customSystemPrompt(properties.getCustomSystemPrompt())
            .appendSystemPrompt(properties.getAppendSystemPrompt())
            .maxToolIterations(properties.getMaxToolIterations())
            .maxOutputTokens(properties.getMaxOutputTokens())
            .maxBudgetUsd(properties.getMaxBudgetUsd());

        var sink = eventSink.getIfAvailable();
        if (sink != null) {
            builder.eventSink(sink);
        }
        return builder.build();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
