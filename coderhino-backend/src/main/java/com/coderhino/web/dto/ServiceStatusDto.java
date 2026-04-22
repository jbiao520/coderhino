package com.coderhino.web.dto;

import com.coderhino.plugins.PluginDescriptor;
import com.coderhino.services.lsp.LspConnection;
import com.coderhino.services.lsp.LspServerDefinition;
import com.coderhino.services.mcp.McpConnection;
import com.coderhino.services.mcp.McpServerDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceStatusDto {

    @JsonProperty("mcpServers")
    private List<McpServerStatusDto> mcpServers;

    @JsonProperty("lspServers")
    private List<LspServerStatusDto> lspServers;

    @JsonProperty("plugins")
    private List<PluginStatusDto> plugins;

    public ServiceStatusDto() {
        this(List.of(), List.of(), List.of());
    }

    public ServiceStatusDto(List<McpServerStatusDto> mcpServers,
                            List<LspServerStatusDto> lspServers,
                            List<PluginStatusDto> plugins) {
        this.mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
        this.lspServers = lspServers != null ? List.copyOf(lspServers) : List.of();
        this.plugins = plugins != null ? List.copyOf(plugins) : List.of();
    }

    public List<McpServerStatusDto> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<McpServerStatusDto> mcpServers) {
        this.mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    public List<LspServerStatusDto> getLspServers() {
        return lspServers;
    }

    public void setLspServers(List<LspServerStatusDto> lspServers) {
        this.lspServers = lspServers != null ? List.copyOf(lspServers) : List.of();
    }

    public List<PluginStatusDto> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginStatusDto> plugins) {
        this.plugins = plugins != null ? List.copyOf(plugins) : List.of();
    }

    public static ServiceStatusDto from(List<McpServerDefinition> mcpDefinitions,
                                        List<McpConnection> mcpConnections,
                                        List<LspServerDefinition> lspDefinitions,
                                        List<LspConnection> lspConnections,
                                        List<PluginDescriptor> plugins) {
        return new ServiceStatusDto(
            toMcpStatuses(mcpDefinitions, mcpConnections),
            toLspStatuses(lspDefinitions, lspConnections),
            toPluginStatuses(plugins)
        );
    }

    private static List<McpServerStatusDto> toMcpStatuses(List<McpServerDefinition> definitions,
                                                          List<McpConnection> connections) {
        Map<String, McpConnection> byName = new LinkedHashMap<>();
        for (var connection : connections == null ? List.<McpConnection>of() : connections) {
            byName.put(connection.serverName(), connection);
        }
        return (definitions == null ? List.<McpServerDefinition>of() : definitions).stream()
            .map(definition -> McpServerStatusDto.from(definition, byName.get(definition.name())))
            .toList();
    }

    private static List<LspServerStatusDto> toLspStatuses(List<LspServerDefinition> definitions,
                                                          List<LspConnection> connections) {
        Map<String, LspConnection> byLanguage = new LinkedHashMap<>();
        for (var connection : connections == null ? List.<LspConnection>of() : connections) {
            byLanguage.put(connection.language(), connection);
        }
        return (definitions == null ? List.<LspServerDefinition>of() : definitions).stream()
            .map(definition -> LspServerStatusDto.from(definition, byLanguage.get(definition.language())))
            .toList();
    }

    private static List<PluginStatusDto> toPluginStatuses(List<PluginDescriptor> plugins) {
        return (plugins == null ? List.<PluginDescriptor>of() : plugins).stream()
            .map(PluginStatusDto::from)
            .toList();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpServerStatusDto {
        @JsonProperty("name")
        private String name;

        @JsonProperty("enabled")
        private boolean enabled;

        @JsonProperty("connected")
        private boolean connected;

        @JsonProperty("status")
        private String status;

        @JsonProperty("command")
        private String command;

        @JsonProperty("commandLine")
        private List<String> commandLine;

        @JsonProperty("processId")
        private Long processId;

        @JsonProperty("lastStartedAt")
        private Instant lastStartedAt;

        public McpServerStatusDto() {
        }

        public McpServerStatusDto(String name, boolean enabled, boolean connected, String status, String command,
                                  List<String> commandLine, Long processId, Instant lastStartedAt) {
            this.name = name;
            this.enabled = enabled;
            this.connected = connected;
            this.status = status;
            this.command = command;
            this.commandLine = commandLine != null ? List.copyOf(commandLine) : List.of();
            this.processId = processId;
            this.lastStartedAt = lastStartedAt;
        }

        public static McpServerStatusDto from(McpServerDefinition definition, McpConnection connection) {
            return new McpServerStatusDto(
                definition.name(),
                definition.enabled(),
                connection != null && connection.connected(),
                connection != null ? connection.statusMessage() : (definition.enabled() ? "registered" : "disabled"),
                definition.command(),
                connection != null && !connection.commandLine().isEmpty()
                    ? connection.commandLine()
                    : buildCommandLine(definition.command(), definition.arguments()),
                connection != null ? connection.processId() : null,
                connection != null ? connection.lastStartedAt() : null
            );
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getCommandLine() { return commandLine; }
        public void setCommandLine(List<String> commandLine) { this.commandLine = commandLine != null ? List.copyOf(commandLine) : List.of(); }
        public Long getProcessId() { return processId; }
        public void setProcessId(Long processId) { this.processId = processId; }
        public Instant getLastStartedAt() { return lastStartedAt; }
        public void setLastStartedAt(Instant lastStartedAt) { this.lastStartedAt = lastStartedAt; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LspServerStatusDto {
        @JsonProperty("language")
        private String language;

        @JsonProperty("enabled")
        private boolean enabled;

        @JsonProperty("connected")
        private boolean connected;

        @JsonProperty("status")
        private String status;

        @JsonProperty("command")
        private String command;

        @JsonProperty("commandLine")
        private List<String> commandLine;

        @JsonProperty("processId")
        private Long processId;

        @JsonProperty("lastStartedAt")
        private Instant lastStartedAt;

        public LspServerStatusDto() {
        }

        public LspServerStatusDto(String language, boolean enabled, boolean connected, String status, String command,
                                  List<String> commandLine, Long processId, Instant lastStartedAt) {
            this.language = language;
            this.enabled = enabled;
            this.connected = connected;
            this.status = status;
            this.command = command;
            this.commandLine = commandLine != null ? List.copyOf(commandLine) : List.of();
            this.processId = processId;
            this.lastStartedAt = lastStartedAt;
        }

        public static LspServerStatusDto from(LspServerDefinition definition, LspConnection connection) {
            return new LspServerStatusDto(
                definition.language(),
                definition.enabled(),
                connection != null && connection.connected(),
                connection != null ? connection.statusMessage() : (definition.enabled() ? "registered" : "disabled"),
                definition.command(),
                connection != null && !connection.commandLine().isEmpty()
                    ? connection.commandLine()
                    : buildCommandLine(definition.command(), definition.arguments()),
                connection != null ? connection.processId() : null,
                connection != null ? connection.lastStartedAt() : null
            );
        }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getCommandLine() { return commandLine; }
        public void setCommandLine(List<String> commandLine) { this.commandLine = commandLine != null ? List.copyOf(commandLine) : List.of(); }
        public Long getProcessId() { return processId; }
        public void setProcessId(Long processId) { this.processId = processId; }
        public Instant getLastStartedAt() { return lastStartedAt; }
        public void setLastStartedAt(Instant lastStartedAt) { this.lastStartedAt = lastStartedAt; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PluginStatusDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("version")
        private String version;

        @JsonProperty("description")
        private String description;

        @JsonProperty("status")
        private String status;

        public PluginStatusDto() {
        }

        public PluginStatusDto(String id, String name, String version, String description, String status) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
            this.status = status;
        }

        public static PluginStatusDto from(PluginDescriptor plugin) {
            return new PluginStatusDto(plugin.id(), plugin.name(), plugin.version(), plugin.description(), "loaded");
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    private static List<String> buildCommandLine(String command, List<String> arguments) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        var values = new java.util.ArrayList<String>();
        values.add(command);
        if (arguments != null && !arguments.isEmpty()) {
            values.addAll(arguments);
        }
        return List.copyOf(values);
    }
}
