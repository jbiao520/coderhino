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
        var pluginService = context.services().pluginService();
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
            if (pluginService instanceof com.coderhino.plugins.FileSystemPluginService fsPlugins) {
                var skillService = context.services().skillService();
                var scanner = new com.coderhino.plugins.PluginScanningService(fsPlugins);
                var loader = new com.coderhino.plugins.PluginComponentLoader(skillService);
                var manifests = scanner.scanDefaultDirectory();
                for (var manifest : manifests) {
                    loader.loadComponents(manifest);
                }
                renderer.printLine("Reloaded " + manifests.size() + " plugin(s).");
            } else {
                renderer.printLine("Plugin reload not supported in this mode.");
            }
        } else if (sub.startsWith("install ")) {
            var pathStr = sub.substring("install ".length()).trim();
            if (pathStr.isEmpty()) {
                renderer.printLine("Usage: /plugin install <path>");
            } else if (pluginService instanceof com.coderhino.plugins.FileSystemPluginService fsPlugins) {
                var skillService = context.services().skillService();
                var loader = new com.coderhino.plugins.PluginComponentLoader(skillService);
                var installer = new com.coderhino.plugins.PluginInstaller(fsPlugins, loader);
                var result = installer.installFromLocalPath(java.nio.file.Path.of(pathStr));
                if (result.success()) {
                    renderer.printLine("Plugin '" + result.manifest().getId() + "' installed successfully.");
                } else {
                    renderer.printLine("Install failed:");
                    result.errors().forEach(e -> renderer.printLine("  " + e));
                }
            } else {
                renderer.printLine("Plugin install not supported in this mode.");
            }
        } else if (sub.startsWith("enable ")) {
            var id = sub.substring("enable ".length()).trim();
            if (pluginService instanceof com.coderhino.plugins.FileSystemPluginService fsPlugins) {
                var manifestOpt = fsPlugins.findManifestById(id);
                if (manifestOpt.isEmpty()) {
                    renderer.printLine("Plugin not found: " + id);
                } else {
                    var manifest = manifestOpt.get();
                    manifest.setEnabled(true);
                    var skillService = context.services().skillService();
                    new com.coderhino.plugins.PluginComponentLoader(skillService).loadComponents(manifest);
                    var serverWirer = new com.coderhino.plugins.PluginServerWirer(
                        context.services().mcp(), context.services().lsp());
                    serverWirer.wireServers(manifest);
                    renderer.printLine("Plugin " + id + " enabled.");
                }
            } else {
                renderer.printLine("Plugin enable not supported in this mode.");
            }
        } else if (sub.startsWith("disable ")) {
            var id = sub.substring("disable ".length()).trim();
            if (pluginService instanceof com.coderhino.plugins.FileSystemPluginService fsPlugins) {
                var manifestOpt = fsPlugins.findManifestById(id);
                if (manifestOpt.isEmpty()) {
                    renderer.printLine("Plugin not found: " + id);
                } else {
                    var manifest = manifestOpt.get();
                    manifest.setEnabled(false);
                    var skillService = context.services().skillService();
                    new com.coderhino.plugins.PluginComponentLoader(skillService).unloadComponents(id);
                    var serverWirer = new com.coderhino.plugins.PluginServerWirer(
                        context.services().mcp(), context.services().lsp());
                    serverWirer.unwireServers(id);
                    renderer.printLine("Plugin " + id + " disabled.");
                }
            } else {
                renderer.printLine("Plugin disable not supported in this mode.");
            }
        } else if (sub.startsWith("info ")) {
            var id = sub.substring("info ".length()).trim();
            if (pluginService instanceof com.coderhino.plugins.FileSystemPluginService fsPlugins) {
                var manifestOpt = fsPlugins.findManifestById(id);
                if (manifestOpt.isEmpty()) {
                    renderer.printLine("Plugin not found: " + id);
                } else {
                    var m = manifestOpt.get();
                    renderer.printLine("Plugin: " + m.getName());
                    renderer.printLine("  id:          " + m.getId());
                    renderer.printLine("  version:     " + (m.getVersion() != null ? m.getVersion() : "(unknown)"));
                    renderer.printLine("  description: " + (m.getDescription() != null ? m.getDescription() : "(none)"));
                    renderer.printLine("  path:        " + (m.getPath() != null ? m.getPath() : "(none)"));
                    renderer.printLine("  enabled:     " + m.isEnabled());
                    renderer.printLine("  source:      " + (m.getSource() != null ? m.getSource() : "(none)"));
                    renderer.printLine("  commands:    " + (m.getCommands() != null ? m.getCommands().size() : 0));
                    renderer.printLine("  skills:      " + (m.getSkills() != null ? m.getSkills().size() : 0));
                    renderer.printLine("  mcpServers:  " + (m.getMcpServers() != null ? m.getMcpServers().size() : 0));
                    renderer.printLine("  lspServers:  " + (m.getLspServers() != null ? m.getLspServers().size() : 0));
                    renderer.printLine("  sha:         " + (m.getSha() != null ? m.getSha() : "(none)"));
                }
            } else {
                renderer.printLine("Plugin info not supported in this mode.");
            }
        } else if (sub.startsWith("marketplace")) {
            var mktArgs = sub.substring("marketplace".length()).trim();
            var registry = new com.coderhino.plugins.marketplace.MarketplaceRegistry();
            if (mktArgs.isEmpty() || mktArgs.equals("list")) {
                var list = registry.list();
                if (list.isEmpty()) {
                    renderer.printLine("No marketplaces registered.");
                } else {
                    renderer.printLine("Registered marketplaces:");
                    for (var m : list) {
                        renderer.printLine("  " + m.name() + " (" + m.type() + ") \u2192 " + m.location());
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
                    registry.add(new com.coderhino.plugins.marketplace.MarketplaceDefinition(
                        name, com.coderhino.plugins.marketplace.MarketplaceType.LOCAL_FILE, path));
                    renderer.printLine("Marketplace '" + name + "' added.");
                }
            } else if (mktArgs.startsWith("remove ")) {
                String name = mktArgs.substring("remove ".length()).trim();
                registry.remove(name);
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
