package com.coderhino.web.controller;

import com.coderhino.plugins.PluginDescriptor;
import com.coderhino.plugins.PluginService;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.CostTracker;
import com.coderhino.services.analytics.NoOpAnalyticsService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;
import com.coderhino.services.compact.CompactService;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.lsp.LspServerDefinition;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.mcp.McpServerDefinition;
import com.coderhino.server.NoOpServerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceStatusControllerTest {

    @Test
    void getStatusReturnsAggregatedServiceSnapshots() {
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("filesystem", "npx", List.of("-y", "@modelcontextprotocol/server-filesystem"), Map.of(), true, 30_000));
        mcp.enable("filesystem");

        var lsp = new LspClientManager();
        lsp.register(new LspServerDefinition("java", "jdtls", List.of("--stdio"), true));

        var controller = new ServiceStatusController(createRegistry(mcp, lsp, new StaticPluginService(
            new PluginDescriptor("plugin-1", "Plugin One", "1.0.0", "Test plugin")
        )));

        var response = controller.getStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getMcpServers().size());
        assertEquals("filesystem", body.getMcpServers().get(0).getName());
        assertEquals("enabled", body.getMcpServers().get(0).getStatus());
        assertEquals(1, body.getLspServers().size());
        assertEquals("java", body.getLspServers().get(0).getLanguage());
        assertEquals("registered", body.getLspServers().get(0).getStatus());
        assertEquals(1, body.getPlugins().size());
        assertEquals("plugin-1", body.getPlugins().get(0).getId());
        assertEquals("loaded", body.getPlugins().get(0).getStatus());
    }

    @Test
    void getStatusDoesNotStartRegisteredServices() {
        var mcp = new McpConnectionManager();
        mcp.register(new McpServerDefinition("docs", "uvx", List.of("serve-docs"), Map.of(), true, 30_000));

        var lsp = new LspClientManager();
        lsp.register(new LspServerDefinition("typescript", "typescript-language-server", List.of("--stdio"), true));

        var controller = new ServiceStatusController(createRegistry(mcp, lsp, new StaticPluginService()));

        var response = controller.getStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(1, mcp.connections().size());
        assertEquals("registered", mcp.connections().get(0).statusMessage());
        assertFalse(mcp.connections().get(0).connected());
        assertEquals(1, lsp.connections().size());
        assertEquals("registered", lsp.connections().get(0).statusMessage());
        assertFalse(lsp.connections().get(0).connected());
        assertEquals("registered", body.getMcpServers().get(0).getStatus());
        assertEquals("registered", body.getLspServers().get(0).getStatus());
        assertTrue(body.getPlugins().isEmpty());
    }

    private ServiceRegistry createRegistry(McpConnectionManager mcp, LspClientManager lsp, PluginService pluginService) {
        return new ServiceRegistry(
            mcp,
            lsp,
            new com.coderhino.services.tasks.TaskService(),
            new CostTracker(),
            new CompactService(),
            new NoOpAnalyticsService(),
            new NoOpFeatureFlagService(),
            new NoOpServerService(),
            pluginService,
            new com.coderhino.skills.NoOpSkillService(),
            new com.coderhino.coordinator.NoOpCoordinatorService()
        );
    }

    private static final class StaticPluginService implements PluginService {
        private final List<PluginDescriptor> plugins;

        private StaticPluginService(PluginDescriptor... plugins) {
            this.plugins = List.of(plugins);
        }

        @Override
        public void load(PluginDescriptor plugin) {
        }

        @Override
        public void unload(String id) {
        }

        @Override
        public List<PluginDescriptor> list() {
            return plugins;
        }

        @Override
        public Optional<PluginDescriptor> findById(String id) {
            return plugins.stream().filter(plugin -> plugin.id().equals(id)).findFirst();
        }
    }
}
