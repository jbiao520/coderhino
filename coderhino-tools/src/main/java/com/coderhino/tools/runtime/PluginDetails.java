package com.coderhino.tools.runtime;

public record PluginDetails(
    String id,
    String name,
    String version,
    String description,
    String path,
    boolean enabled,
    String source,
    int commandCount,
    int skillCount,
    int mcpServerCount,
    int lspServerCount,
    String sha
) {
}
