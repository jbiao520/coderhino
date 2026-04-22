package com.coderhino.plugins;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for loading, unloading, and querying plugins.
 */
public interface PluginService {

    void load(PluginDescriptor plugin);

    void unload(String id);

    List<PluginDescriptor> list();

    Optional<PluginDescriptor> findById(String id);

    default String serviceName() {
        return "plugin-service";
    }
}
