package com.coderhino.plugins;

import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.lsp.LspServerDefinition;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.mcp.McpServerDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PluginServerWirer {
    private final McpConnectionManager mcpManager;
    private final LspClientManager lspManager;
    private final Map<String, List<String>> wiredMcpServers = new HashMap<>();
    private final Map<String, List<String>> wiredLspServers = new HashMap<>();

    public PluginServerWirer(McpConnectionManager mcpManager, LspClientManager lspManager) {
        this.mcpManager = mcpManager;
        this.lspManager = lspManager;
    }

    public void wireServers(PluginManifest manifest) {
        if (!manifest.isEnabled()) {
            return;
        }
        String pluginId = manifest.getId();
        List<String> mcpNames = new ArrayList<>();
        List<String> lspLangs = new ArrayList<>();

        if (manifest.getMcpServers() != null) {
            for (McpServerDefinition def : manifest.getMcpServers()) {
                String prefixedName = "plugin:" + pluginId + ":" + def.name();
                boolean alreadyRegistered = mcpManager.definitions().stream()
                        .anyMatch(d -> d.name().equals(prefixedName));
                if (alreadyRegistered) {
                    System.err.println("[plugin] MCP server '" + prefixedName + "' already registered, skipping");
                    continue;
                }
                Map<String, String> env = new HashMap<>(def.environment());
                env.put("CLAUDE_PLUGIN_ROOT", manifest.getPath() != null ? manifest.getPath().toString() : "");
                var newDef = new McpServerDefinition(prefixedName, def.command(), def.arguments(), env, def.enabled(), def.initializeTimeoutMs());
                mcpManager.register(newDef);
                mcpNames.add(prefixedName);
            }
        }

        if (manifest.getLspServers() != null) {
            for (LspServerDefinition def : manifest.getLspServers()) {
                String prefixedLang = "plugin:" + pluginId + ":" + def.language();
                boolean alreadyRegistered = lspManager.definitions().stream()
                        .anyMatch(d -> d.language().equals(prefixedLang));
                if (alreadyRegistered) {
                    System.err.println("[plugin] LSP server '" + prefixedLang + "' already registered, skipping");
                    continue;
                }
                var newDef = new LspServerDefinition(prefixedLang, def.command(), def.arguments(), def.enabled());
                lspManager.register(newDef);
                lspLangs.add(prefixedLang);
            }
        }

        if (!mcpNames.isEmpty()) wiredMcpServers.put(pluginId, mcpNames);
        if (!lspLangs.isEmpty()) wiredLspServers.put(pluginId, lspLangs);
    }

    public void unwireServers(String pluginId) {
        List<String> mcpNames = wiredMcpServers.remove(pluginId);
        if (mcpNames != null) {
            for (String name : mcpNames) {
                mcpManager.unregister(name);
            }
        }
        List<String> lspLangs = wiredLspServers.remove(pluginId);
        if (lspLangs != null) {
            for (String lang : lspLangs) {
                lspManager.unregister(lang);
            }
        }
    }
}
