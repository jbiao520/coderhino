package com.coderhino.services;

import com.coderhino.plugins.FileSystemPluginService;
import com.coderhino.plugins.PluginComponentLoader;
import com.coderhino.plugins.PluginInstaller;
import com.coderhino.plugins.PluginScanningService;
import com.coderhino.plugins.PluginServerWirer;
import com.coderhino.plugins.PluginService;
import com.coderhino.plugins.marketplace.MarketplaceDefinition;
import com.coderhino.plugins.marketplace.MarketplaceRegistry;
import com.coderhino.plugins.marketplace.MarketplaceType;
import com.coderhino.skills.SkillService;
import com.coderhino.tools.runtime.PluginCommandService;
import com.coderhino.tools.runtime.PluginDetails;
import com.coderhino.tools.runtime.PluginInstallResult;
import com.coderhino.tools.runtime.PluginMarketplace;
import com.coderhino.tools.runtime.PluginSummary;
import com.coderhino.tools.runtime.ToolLspService;
import com.coderhino.tools.runtime.ToolMcpService;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class CommandPluginServiceAdapter implements PluginCommandService {
    private final PluginService pluginService;
    private final SkillService skillService;
    private final ToolMcpService mcpService;
    private final ToolLspService lspService;
    private final MarketplaceRegistry marketplaceRegistry;

    CommandPluginServiceAdapter(
        PluginService pluginService,
        SkillService skillService,
        ToolMcpService mcpService,
        ToolLspService lspService
    ) {
        this.pluginService = pluginService;
        this.skillService = skillService;
        this.mcpService = mcpService;
        this.lspService = lspService;
        this.marketplaceRegistry = new MarketplaceRegistry();
    }

    @Override
    public List<PluginSummary> list() {
        return pluginService.list().stream()
            .map(plugin -> new PluginSummary(plugin.id(), plugin.name(), plugin.version(), plugin.description()))
            .toList();
    }

    @Override
    public Optional<PluginSummary> findById(String id) {
        return pluginService.findById(id)
            .map(plugin -> new PluginSummary(plugin.id(), plugin.name(), plugin.version(), plugin.description()));
    }

    @Override
    public int reload() {
        return withFileSystemPlugins(fsPlugins -> {
            var scanner = new PluginScanningService(fsPlugins);
            var loader = new PluginComponentLoader(skillService);
            var manifests = scanner.scanDefaultDirectory();
            for (var manifest : manifests) {
                loader.loadComponents(manifest);
            }
            return manifests.size();
        }).orElse(0);
    }

    @Override
    public PluginInstallResult installFromLocalPath(Path path) {
        return withFileSystemPlugins(fsPlugins -> {
            var installer = new PluginInstaller(fsPlugins, new PluginComponentLoader(skillService));
            var result = installer.installFromLocalPath(path);
            return new PluginInstallResult(
                result.success(),
                result.manifest() == null ? null : result.manifest().getId(),
                result.errors()
            );
        }).orElseGet(() -> new PluginInstallResult(false, null, List.of("Plugin install not supported in this mode.")));
    }

    @Override
    public Optional<PluginDetails> enable(String id) {
        return withFileSystemPlugins(fsPlugins -> {
            var manifestOpt = fsPlugins.findManifestById(id);
            if (manifestOpt.isEmpty()) {
                return Optional.<PluginDetails>empty();
            }
            var manifest = manifestOpt.get();
            manifest.setEnabled(true);
            new PluginComponentLoader(skillService).loadComponents(manifest);
            new PluginServerWirer((com.coderhino.services.mcp.McpConnectionManager) mcpService, (com.coderhino.services.lsp.LspClientManager) lspService)
                .wireServers(manifest);
            return Optional.of(toDetails(manifest));
        }).orElse(Optional.empty());
    }

    @Override
    public Optional<PluginDetails> disable(String id) {
        return withFileSystemPlugins(fsPlugins -> {
            var manifestOpt = fsPlugins.findManifestById(id);
            if (manifestOpt.isEmpty()) {
                return Optional.<PluginDetails>empty();
            }
            var manifest = manifestOpt.get();
            manifest.setEnabled(false);
            new PluginComponentLoader(skillService).unloadComponents(id);
            new PluginServerWirer((com.coderhino.services.mcp.McpConnectionManager) mcpService, (com.coderhino.services.lsp.LspClientManager) lspService)
                .unwireServers(id);
            return Optional.of(toDetails(manifest));
        }).orElse(Optional.empty());
    }

    @Override
    public Optional<PluginDetails> details(String id) {
        return withFileSystemPlugins(fsPlugins -> fsPlugins.findManifestById(id).map(this::toDetails))
            .orElseGet(() -> pluginService.findById(id).map(plugin -> new PluginDetails(
                plugin.id(),
                plugin.name(),
                plugin.version(),
                plugin.description(),
                null,
                true,
                null,
                0,
                0,
                0,
                0,
                null
            )));
    }

    @Override
    public List<PluginMarketplace> listMarketplaces() {
        return marketplaceRegistry.list().stream()
            .map(marketplace -> new PluginMarketplace(marketplace.name(), marketplace.type().name(), marketplace.location()))
            .toList();
    }

    @Override
    public void addMarketplace(String name, String location) {
        marketplaceRegistry.add(new MarketplaceDefinition(name, MarketplaceType.LOCAL_FILE, location));
    }

    @Override
    public void removeMarketplace(String name) {
        marketplaceRegistry.remove(name);
    }

    private PluginDetails toDetails(com.coderhino.plugins.PluginManifest manifest) {
        return new PluginDetails(
            manifest.getId(),
            manifest.getName(),
            manifest.getVersion(),
            manifest.getDescription(),
            manifest.getPath() == null ? null : manifest.getPath().toString(),
            manifest.isEnabled(),
            manifest.getSource() == null ? null : manifest.getSource().name(),
            manifest.getCommands() == null ? 0 : manifest.getCommands().size(),
            manifest.getSkills() == null ? 0 : manifest.getSkills().size(),
            manifest.getMcpServers() == null ? 0 : manifest.getMcpServers().size(),
            manifest.getLspServers() == null ? 0 : manifest.getLspServers().size(),
            manifest.getSha()
        );
    }

    private <T> Optional<T> withFileSystemPlugins(java.util.function.Function<FileSystemPluginService, T> action) {
        if (pluginService instanceof FileSystemPluginService fsPlugins) {
            return Optional.ofNullable(action.apply(fsPlugins));
        }
        return Optional.empty();
    }
}
