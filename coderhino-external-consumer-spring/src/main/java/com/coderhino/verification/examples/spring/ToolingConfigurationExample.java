package com.coderhino.verification.examples.spring;

import com.coderhino.tools.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

public final class ToolingConfigurationExample {
    public static final String ENABLED_TOOLS_PROPERTY = "coderhino.agent.enabled-tools";
    public static final List<String> SAFE_DEFAULT_TOOL_NAMES = List.of("read_file", "glob", "grep");
    public static final List<String> NARROWED_TOOL_NAMES = List.of("read_file", "grep");

    private ToolingConfigurationExample() {
    }

    public static ToolRegistry hostOwnedToolRegistry(HostEchoTool hostEchoTool) {
        return ToolRegistry.createEmbeddedDefault().with(hostEchoTool);
    }

    @Configuration(proxyBeanMethods = false)
    public static class HostToolRegistryOverrideConfiguration {
        @Bean
        HostEchoTool hostEchoTool() {
            return new HostEchoTool();
        }

        @Bean
        ToolRegistry coderhinoToolRegistry(HostEchoTool hostEchoTool) {
            return hostOwnedToolRegistry(hostEchoTool);
        }
    }
}
