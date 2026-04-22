package com.coderhino.services.mcp;

import java.util.Optional;

public final class McpToolName {
    private static final String PREFIX = "mcp__";
    private static final String CLAUDE_AI_SERVER_PREFIX = "claude.ai ";

    private McpToolName() {
    }

    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        var normalized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (name.startsWith(CLAUDE_AI_SERVER_PREFIX)) {
            normalized = normalized.replaceAll("_+", "_").replaceAll("^_|_$", "");
        }
        return normalized;
    }

    public static String prefix(String serverName) {
        return PREFIX + normalize(serverName) + "__";
    }

    public static String qualified(String serverName, String toolName) {
        return prefix(serverName) + normalize(toolName);
    }

    public static Optional<ParsedToolName> parse(String toolName) {
        if (toolName == null || !toolName.startsWith(PREFIX)) {
            return Optional.empty();
        }

        var withoutPrefix = toolName.substring(PREFIX.length());
        var delimiter = withoutPrefix.indexOf("__");
        if (delimiter <= 0 || delimiter == withoutPrefix.length() - 2) {
            return Optional.empty();
        }

        var serverName = withoutPrefix.substring(0, delimiter);
        var remoteToolName = withoutPrefix.substring(delimiter + 2);
        if (serverName.isBlank() || remoteToolName.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ParsedToolName(serverName, remoteToolName));
    }

    public record ParsedToolName(String serverName, String toolName) {
    }
}
