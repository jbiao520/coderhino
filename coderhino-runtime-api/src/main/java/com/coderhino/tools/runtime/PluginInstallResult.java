package com.coderhino.tools.runtime;

import java.util.List;

public record PluginInstallResult(boolean success, String pluginId, List<String> errors) {
    public PluginInstallResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
