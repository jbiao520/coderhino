package com.coderhino.plugins;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * No-op implementation of {@link PluginService}.
 * All operations are non-throwing and deterministic.
 */
public final class NoOpPluginService implements PluginService {

    @Override
    public void load(PluginDescriptor plugin) {
        // no-op
    }

    @Override
    public void unload(String id) {
        // no-op
    }

    @Override
    public List<PluginDescriptor> list() {
        return Collections.emptyList();
    }

    @Override
    public Optional<PluginDescriptor> findById(String id) {
        return Optional.empty();
    }

    @Override
    public String serviceName() {
        return "plugin-service";
    }
}
