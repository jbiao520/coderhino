package com.coderhino.tools.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface PluginCommandService {
    List<PluginSummary> list();

    Optional<PluginSummary> findById(String id);

    int reload();

    PluginInstallResult installFromLocalPath(Path path);

    Optional<PluginDetails> enable(String id);

    Optional<PluginDetails> disable(String id);

    Optional<PluginDetails> details(String id);

    List<PluginMarketplace> listMarketplaces();

    void addMarketplace(String name, String location);

    void removeMarketplace(String name);
}
