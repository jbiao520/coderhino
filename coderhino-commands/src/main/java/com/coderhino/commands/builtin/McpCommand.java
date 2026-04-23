package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.services.mcp.McpServerDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public final class McpCommand implements CommandDefinition {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "mcp";
    }

    @Override
    public String description() {
        return "List configured MCP servers and connect one by name";
    }

    @Override
    public void execute(CommandContext context, String args) {
        if (args == null || args.isBlank()) {
            context.services().mcp().connections().forEach(connection ->
                context.out().printf("%s connected=%s status=%s pid=%s cmd=%s%n",
                    connection.serverName(),
                    connection.connected(),
                    connection.statusMessage(),
                    connection.processId(),
                    connection.commandLine()));
            return;
        }

        if (args.startsWith("disconnect ")) {
            var serverName = args.substring("disconnect ".length()).trim();
            var disconnected = context.services().mcp().disconnect(serverName);
            if (disconnected.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            context.out().printf("MCP server %s status=%s connected=%s%n",
                disconnected.get().serverName(),
                disconnected.get().statusMessage(),
                disconnected.get().connected());
            return;
        }

        if (args.startsWith("reconnect ")) {
            var serverName = args.substring("reconnect ".length()).trim();
            var reconnected = context.services().mcp().reconnect(serverName);
            if (reconnected.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            context.out().printf("MCP server %s status=%s connected=%s pid=%s%n",
                reconnected.get().serverName(),
                reconnected.get().statusMessage(),
                reconnected.get().connected(),
                reconnected.get().processId());
            return;
        }

        if (args.startsWith("enable ")) {
            var serverName = args.substring("enable ".length()).trim();
            var enabled = context.services().mcp().enable(serverName);
            if (enabled.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            try {
                context.services().mcpConfig().setServerEnabled(Path.of(context.bootstrapState().get().cwd()), serverName, true);
            } catch (Exception exception) {
                context.err().printf("Failed to persist MCP server state: %s%n", exception.getMessage());
                return;
            }
            context.out().printf("MCP server %s status=%s connected=%s%n",
                enabled.get().serverName(),
                enabled.get().statusMessage(),
                enabled.get().connected());
            return;
        }

        if (args.startsWith("disable ")) {
            var serverName = args.substring("disable ".length()).trim();
            var disabled = context.services().mcp().disable(serverName);
            if (disabled.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            try {
                context.services().mcpConfig().setServerEnabled(Path.of(context.bootstrapState().get().cwd()), serverName, false);
            } catch (Exception exception) {
                context.err().printf("Failed to persist MCP server state: %s%n", exception.getMessage());
                return;
            }
            context.out().printf("MCP server %s status=%s connected=%s%n",
                disabled.get().serverName(),
                disabled.get().statusMessage(),
                disabled.get().connected());
            return;
        }

        if (args.startsWith("add ")) {
            var remainder = args.substring("add ".length()).trim();
            var parts = remainder.isBlank() ? new String[0] : remainder.split("\\s+");
            if (parts.length < 2) {
                context.err().println("Usage: /mcp add <name> <command> [args...]");
                return;
            }
            var definition = new McpServerDefinition(
                parts[0],
                parts[1],
                parts.length > 2 ? Arrays.asList(parts).subList(2, parts.length) : java.util.List.of(),
                Map.of(),
                true,
                30_000L
            );
            try {
                var cwd = Path.of(context.bootstrapState().get().cwd());
                context.services().mcpConfig().addServer(cwd, definition);
                context.services().mcp().register(definition);
                context.out().printf("Added MCP server %s to %s%n", definition.name(), cwd.resolve(".mcp.json"));
            } catch (Exception exception) {
                context.err().printf("Failed to add MCP server: %s%n", exception.getMessage());
            }
            return;
        }

        if (args.startsWith("tools ")) {
            var serverName = args.substring("tools ".length()).trim();
            var tools = context.services().mcp().listTools(serverName);
            if (tools.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            if (tools.get().isEmpty()) {
                var connection = context.services().mcp().connection(serverName).orElse(null);
                if (connection != null && connection.statusMessage().startsWith("protocol-")) {
                    context.err().printf("MCP server %s status=%s connected=%s pid=%s%n",
                        connection.serverName(),
                        connection.statusMessage(),
                        connection.connected(),
                        connection.processId());
                    return;
                }
                context.out().printf("No tools reported by %s%n", serverName);
                return;
            }
            tools.get().forEach(tool -> context.out().printf("%s - %s%n", tool.name(), tool.description()));
            return;
        }

        if (args.startsWith("resources ")) {
            var serverName = args.substring("resources ".length()).trim();
            var resources = context.services().mcp().listResources(serverName);
            if (resources.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            if (resources.get().isEmpty()) {
                var connection = context.services().mcp().connection(serverName).orElse(null);
                if (connection != null && connection.statusMessage().startsWith("protocol-")) {
                    context.err().printf("MCP server %s status=%s connected=%s pid=%s%n",
                        connection.serverName(),
                        connection.statusMessage(),
                        connection.connected(),
                        connection.processId());
                    return;
                }
                context.out().printf("No resources reported by %s%n", serverName);
                return;
            }
            resources.get().forEach(resource -> context.out().printf("%s - %s (%s)%n",
                resource.uri(),
                resource.name().isBlank() ? "resource" : resource.name(),
                resource.mimeType().isBlank() ? "unknown" : resource.mimeType()));
            return;
        }

        if (args.startsWith("read ")) {
            var remainder = args.substring("read ".length()).trim();
            var firstSpace = remainder.indexOf(' ');
            if (firstSpace < 0) {
                context.err().println("Usage: /mcp read <server> <uri>");
                return;
            }
            var serverName = remainder.substring(0, firstSpace).trim();
            var uri = remainder.substring(firstSpace + 1).trim();
            if (uri.isEmpty()) {
                context.err().println("Usage: /mcp read <server> <uri>");
                return;
            }
            var result = context.services().mcp().readResource(serverName, uri);
            if (result.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            context.out().println(result.get());
            return;
        }

        if (args.startsWith("call ")) {
            var remainder = args.substring("call ".length()).trim();
            var firstSpace = remainder.indexOf(' ');
            if (firstSpace < 0) {
                context.err().println("Usage: /mcp call <server> <tool> [json-arguments]");
                return;
            }
            var serverName = remainder.substring(0, firstSpace).trim();
            var toolAndArgs = remainder.substring(firstSpace + 1).trim();
            if (toolAndArgs.isEmpty()) {
                context.err().println("Usage: /mcp call <server> <tool> [json-arguments]");
                return;
            }

            var jsonStart = toolAndArgs.indexOf('{');
            var toolName = jsonStart >= 0 ? toolAndArgs.substring(0, jsonStart).trim() : toolAndArgs;
            if (toolName.isEmpty()) {
                context.err().println("Usage: /mcp call <server> <tool> [json-arguments]");
                return;
            }

            var argumentsNode = OBJECT_MAPPER.createObjectNode();
            if (jsonStart >= 0) {
                var jsonArguments = toolAndArgs.substring(jsonStart).trim();
                try {
                    var parsed = OBJECT_MAPPER.readTree(jsonArguments);
                    if (parsed == null || !parsed.isObject()) {
                        context.err().println("MCP call arguments must be a JSON object.");
                        return;
                    }
                    argumentsNode = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
                } catch (Exception exception) {
                    context.err().printf("Invalid MCP call arguments: %s%n", exception.getMessage());
                    return;
                }
            }

            var result = context.services().mcp().callTool(serverName, toolName, argumentsNode);
            if (result.isEmpty()) {
                context.err().printf("Unknown MCP server: %s%n", serverName);
                return;
            }
            context.out().println(result.get());
            return;
        }

        var result = context.services().mcp().connect(args.trim());
        if (result.isEmpty()) {
            context.err().printf("Unknown MCP server: %s%n", args.trim());
            return;
        }

        var connection = result.get();
        context.out().printf("MCP server %s status=%s connected=%s pid=%s%n",
            connection.serverName(),
            connection.statusMessage(),
            connection.connected(),
            connection.processId());
    }
}
