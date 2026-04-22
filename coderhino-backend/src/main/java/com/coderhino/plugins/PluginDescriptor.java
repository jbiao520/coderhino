package com.coderhino.plugins;

/**
 * Describes a loaded plugin.
 *
 * @param id          unique plugin identifier
 * @param name        human-readable plugin name
 * @param version     semantic version string
 * @param description short summary of plugin capabilities
 */
public record PluginDescriptor(String id, String name, String version, String description) {
}
