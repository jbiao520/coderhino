package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class PluginCommand implements CommandDefinition {
    @Override
    public String name() {
        return "plugin";
    }

    @Override
    public String description() {
        return "List and inspect available plugins";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var pluginService = context.services().commandPlugins();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty() || sub.equals("list")) {
            var plugins = pluginService.list();
            if (plugins.isEmpty()) {
                renderer.printLine("No plugins registered.");
                renderer.printLine("Plugins can be added by creating plugin definition files in ~/.coderhino/plugins/");
            } else {
                renderer.printLine("Available plugins (" + plugins.size() + "):");
                for (var plugin : plugins) {
                    renderer.printLine("  " + plugin.id() + " - " + plugin.name());
                    if (plugin.description() != null && !plugin.description().isBlank()) {
                        renderer.printLine("    " + plugin.description());
                    }
                }
            }
        } else if (sub.startsWith("show ")) {
            var id = sub.substring(5).trim();
            var found = pluginService.findById(id);
            if (found.isEmpty()) {
                renderer.printLine("Plugin not found: " + id);
            } else {
                var plugin = found.get();
                renderer.printLine("Plugin: " + plugin.name());
                renderer.printLine("  id:      " + plugin.id());
                renderer.printLine("  version: " + (plugin.version() != null ? plugin.version() : "(unknown)"));
                renderer.printLine("  desc:    " + (plugin.description() != null ? plugin.description() : "(none)"));
            }
        } else if (sub.equals("reload")) {
            renderer.printLine("Reloaded " + pluginService.reload() + " plugin(s).");
        } else if (sub.startsWith("install ")) {
            var pathStr = sub.substring("install ".length()).trim();
            if (pathStr.isEmpty()) {
                renderer.printLine("Usage: /plugin install <path>");
            } else {
                var result = pluginService.installFromLocalPath(java.nio.file.Path.of(pathStr));
                if (result.success()) {
                    renderer.printLine("Plugin '" + result.pluginId() + "' installed successfully.");
                } else {
                    renderer.printLine("Install failed:");
                    result.errors().forEach(e -> renderer.printLine("  " + e));
                }
            }
        } else if (sub.startsWith("enable ")) {
            var id = sub.substring("enable ".length()).trim();
            if (pluginService.enable(id).isPresent()) {
                renderer.printLine("Plugin " + id + " enabled.");
            } else {
                renderer.printLine("Plugin not found: " + id);
            }
        } else if (sub.startsWith("disable ")) {
            var id = sub.substring("disable ".length()).trim();
            if (pluginService.disable(id).isPresent()) {
                renderer.printLine("Plugin " + id + " disabled.");
            } else {
                renderer.printLine("Plugin not found: " + id);
            }
        } else if (sub.startsWith("info ")) {
            var id = sub.substring("info ".length()).trim();
            var details = pluginService.details(id);
            if (details.isEmpty()) {
                renderer.printLine("Plugin not found: " + id);
            } else {
                var plugin = details.get();
                renderer.printLine("Plugin: " + plugin.name());
                renderer.printLine("  id:          " + plugin.id());
                renderer.printLine("  version:     " + (plugin.version() != null ? plugin.version() : "(unknown)"));
                renderer.printLine("  description: " + (plugin.description() != null ? plugin.description() : "(none)"));
                renderer.printLine("  path:        " + (plugin.path() != null ? plugin.path() : "(none)"));
                renderer.printLine("  enabled:     " + plugin.enabled());
                renderer.printLine("  source:      " + (plugin.source() != null ? plugin.source() : "(none)"));
                renderer.printLine("  commands:    " + plugin.commandCount());
                renderer.printLine("  skills:      " + plugin.skillCount());
                renderer.printLine("  mcpServers:  " + plugin.mcpServerCount());
                renderer.printLine("  lspServers:  " + plugin.lspServerCount());
                renderer.printLine("  sha:         " + (plugin.sha() != null ? plugin.sha() : "(none)"));
            }
        } else if (sub.startsWith("marketplace")) {
            var mktArgs = sub.substring("marketplace".length()).trim();
            if (mktArgs.isEmpty() || mktArgs.equals("list")) {
                var list = pluginService.listMarketplaces();
                if (list.isEmpty()) {
                    renderer.printLine("No marketplaces registered.");
                } else {
                    renderer.printLine("Registered marketplaces:");
                    for (var m : list) {
                        renderer.printLine("  " + m.name() + " (" + m.type() + ") -> " + m.location());
                    }
                }
            } else if (mktArgs.startsWith("add ")) {
                var addArgs = mktArgs.substring(4).trim();
                int spaceIdx = addArgs.indexOf(' ');
                if (spaceIdx < 0) {
                    renderer.printLine("Usage: /plugin marketplace add <name> <path>");
                } else {
                    String name = addArgs.substring(0, spaceIdx).trim();
                    String path = addArgs.substring(spaceIdx + 1).trim();
                    pluginService.addMarketplace(name, path);
                    renderer.printLine("Marketplace '" + name + "' added.");
                }
            } else if (mktArgs.startsWith("remove ")) {
                String name = mktArgs.substring("remove ".length()).trim();
                pluginService.removeMarketplace(name);
                renderer.printLine("Marketplace '" + name + "' removed.");
            } else {
                renderer.printLine("Usage: /plugin marketplace [list|add <name> <path>|remove <name>]");
            }
        } else {
            renderer.printLine("Usage: /plugin [list|show <id>|reload|install <path>|enable <id>|disable <id>|info <id>|marketplace]");
            renderer.printLine("  list            - List all registered plugins (default)");
            renderer.printLine("  show <id>       - Show details about a specific plugin");
            renderer.printLine("  reload          - Reload plugins from the default directory");
            renderer.printLine("  install <path>  - Install a plugin from a local directory");
            renderer.printLine("  enable <id>     - Enable a plugin");
            renderer.printLine("  disable <id>    - Disable a plugin");
            renderer.printLine("  info <id>       - Show full manifest details for a plugin");
            renderer.printLine("  marketplace     - Manage plugin marketplaces (list/add/remove)");
        }
    }
}
