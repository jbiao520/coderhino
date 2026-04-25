package com.coderhino.plugins;

import com.coderhino.services.lsp.LspServerDefinition;
import com.coderhino.services.mcp.McpServerDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class PluginManifest {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final Path path;
    private boolean enabled;
    private final PluginSource source;
    private final List<String> commands;
    private final List<String> agents;
    private final List<String> skills;
    private final Map<String, List<String>> hooks;
    private final List<McpServerDefinition> mcpServers;
    private final List<LspServerDefinition> lspServers;
    private final String sha;

    private PluginManifest(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.version = b.version;
        this.description = b.description;
        this.path = b.path;
        this.enabled = b.enabled;
        this.source = b.source;
        this.commands = b.commands;
        this.agents = b.agents;
        this.skills = b.skills;
        this.hooks = b.hooks;
        this.mcpServers = b.mcpServers;
        this.lspServers = b.lspServers;
        this.sha = b.sha;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public Path getPath() { return path; }
    public boolean isEnabled() { return enabled; }
    public PluginSource getSource() { return source; }
    public List<String> getCommands() { return commands; }
    public List<String> getAgents() { return agents; }
    public List<String> getSkills() { return skills; }
    public Map<String, List<String>> getHooks() { return hooks; }
    public List<McpServerDefinition> getMcpServers() { return mcpServers; }
    public List<LspServerDefinition> getLspServers() { return lspServers; }
    public String getSha() { return sha; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static PluginManifest fromDescriptor(PluginDescriptor d) {
        return builder(d.id())
                .name(d.name())
                .version(d.version())
                .description(d.description())
                .build();
    }

    public static final class Builder {

        private final String id;
        private String name;
        private String version;
        private String description;
        private Path path;
        private boolean enabled = true;
        private PluginSource source = PluginSource.USER;
        private List<String> commands = List.of();
        private List<String> agents = List.of();
        private List<String> skills = List.of();
        private Map<String, List<String>> hooks = Map.of();
        private List<McpServerDefinition> mcpServers = List.of();
        private List<LspServerDefinition> lspServers = List.of();
        private String sha;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder path(Path path) { this.path = path; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder source(PluginSource source) { this.source = source; return this; }
        public Builder commands(List<String> commands) { this.commands = commands; return this; }
        public Builder agents(List<String> agents) { this.agents = agents; return this; }
        public Builder skills(List<String> skills) { this.skills = skills; return this; }
        public Builder hooks(Map<String, List<String>> hooks) { this.hooks = hooks; return this; }
        public Builder mcpServers(List<McpServerDefinition> mcpServers) { this.mcpServers = mcpServers; return this; }
        public Builder lspServers(List<LspServerDefinition> lspServers) { this.lspServers = lspServers; return this; }
        public Builder sha(String sha) { this.sha = sha; return this; }

        public PluginManifest build() {
            return new PluginManifest(this);
        }
    }
}
