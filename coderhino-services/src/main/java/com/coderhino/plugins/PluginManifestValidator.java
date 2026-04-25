package com.coderhino.plugins;

import com.coderhino.services.lsp.LspServerDefinition;
import com.coderhino.services.mcp.McpServerDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PluginManifestValidator {

    private final ObjectMapper objectMapper;

    public PluginManifestValidator() {
        this(new ObjectMapper());
    }

    public PluginManifestValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ValidationResult(PluginManifest manifest, List<String> errors, List<String> warnings) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public ValidationResult validate(Path pluginDir) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (pluginDir == null || !Files.isDirectory(pluginDir)) {
            errors.add("Plugin directory does not exist: " + pluginDir);
            return new ValidationResult(null, errors, warnings);
        }

        Path manifestFile = pluginDir.resolve("plugin.json");
        if (!Files.exists(manifestFile)) {
            errors.add("plugin.json not found in: " + pluginDir);
            return new ValidationResult(null, errors, warnings);
        }

        Map<String, Object> data;
        try {
            String json = Files.readString(manifestFile);
            data = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            errors.add("Failed to read plugin.json: " + e.getMessage());
            return new ValidationResult(null, errors, warnings);
        }

        String id = getString(data, "id");
        if (id == null || id.isBlank()) {
            errors.add("Plugin manifest missing required field: id");
        } else if (!id.matches("[a-zA-Z0-9._@-]+")) {
            errors.add("Plugin id contains invalid characters: " + id);
        }

        String name = getString(data, "name");
        if (name == null || name.isBlank()) {
            errors.add("Plugin manifest missing required field: name");
        }

        String version = getString(data, "version");
        if (version != null && !version.isBlank() && !version.matches("\\d+\\.\\d+.*")) {
            warnings.add("version does not look like semver: " + version);
        }

        String sourceStr = getString(data, "source");
        PluginSource source = PluginSource.USER;
        if (sourceStr != null && !sourceStr.isBlank()) {
            try {
                source = PluginSource.valueOf(sourceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                warnings.add("Unknown plugin source, defaulting to USER: " + sourceStr);
            }
        }

        List<String> commands = parseStringList(data, "commands", errors);
        List<String> agents = parseStringList(data, "agents", errors);
        List<String> skills = parseStringList(data, "skills", errors);
        Map<String, List<String>> hooks = parseHooks(data);
        List<McpServerDefinition> mcpServers = parseMcpServers(data, errors);
        List<LspServerDefinition> lspServers = parseLspServers(data, errors);

        if (!errors.isEmpty()) {
            return new ValidationResult(null, errors, warnings);
        }

        PluginManifest manifest = PluginManifest.builder(id)
                .name(name)
                .version(version)
                .description(getString(data, "description"))
                .path(pluginDir)
                .source(source)
                .commands(commands)
                .agents(agents)
                .skills(skills)
                .hooks(hooks)
                .mcpServers(mcpServers)
                .lspServers(lspServers)
                .sha(getString(data, "sha"))
                .build();

        return new ValidationResult(manifest, errors, warnings);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Map<String, Object> data, String key, List<String> errors) {
        Object raw = data.get(key);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List)) {
            return List.of();
        }
        List<?> list = (List<?>) raw;
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String) || ((String) item).isBlank()) {
                errors.add("Each entry in '" + key + "' must be a non-blank string");
            } else {
                result.add((String) item);
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseHooks(Map<String, Object> data) {
        Object raw = data.get("hooks");
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        Map<?, ?> rawMap = (Map<?, ?>) raw;
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof List) {
                String hookKey = (String) entry.getKey();
                List<?> hookList = (List<?>) entry.getValue();
                List<String> hookValues = new ArrayList<>();
                for (Object v : hookList) {
                    if (v instanceof String) {
                        hookValues.add((String) v);
                    }
                }
                result.put(hookKey, List.copyOf(hookValues));
            }
        }
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<McpServerDefinition> parseMcpServers(Map<String, Object> data, List<String> errors) {
        Object raw = data.get("mcpServers");
        if (!(raw instanceof List)) {
            return List.of();
        }
        List<?> list = (List<?>) raw;
        List<McpServerDefinition> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) {
                errors.add("Each entry in 'mcpServers' must be an object");
                continue;
            }
            Map<String, Object> entry = (Map<String, Object>) item;
            String mcpName = getString(entry, "name");
            String command = getString(entry, "command");
            if (mcpName == null || mcpName.isBlank()) {
                errors.add("Each entry in 'mcpServers' must have 'name' and 'command' keys");
                continue;
            }
            if (command == null || command.isBlank()) {
                errors.add("Each entry in 'mcpServers' must have 'name' and 'command' keys");
                continue;
            }
            List<String> args = parseRawStringList(entry.get("arguments"));
            Map<String, String> env = parseStringMap(entry.get("environment"));
            boolean enabled = getBoolean(entry, "enabled", false);
            result.add(new McpServerDefinition(mcpName, command, args, env, enabled, 30_000L));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<LspServerDefinition> parseLspServers(Map<String, Object> data, List<String> errors) {
        Object raw = data.get("lspServers");
        if (!(raw instanceof List)) {
            return List.of();
        }
        List<?> list = (List<?>) raw;
        List<LspServerDefinition> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) {
                errors.add("Each entry in 'lspServers' must be an object");
                continue;
            }
            Map<String, Object> entry = (Map<String, Object>) item;
            String language = getString(entry, "language");
            String command = getString(entry, "command");
            if (language == null || language.isBlank()) {
                errors.add("Each entry in 'lspServers' must have 'language' and 'command' keys");
                continue;
            }
            if (command == null || command.isBlank()) {
                errors.add("Each entry in 'lspServers' must have 'language' and 'command' keys");
                continue;
            }
            List<String> args = parseRawStringList(entry.get("arguments"));
            boolean enabled = getBoolean(entry, "enabled", false);
            result.add(new LspServerDefinition(language, command, args, enabled));
        }
        return List.copyOf(result);
    }

    private List<String> parseRawStringList(Object raw) {
        if (!(raw instanceof List)) {
            return List.of();
        }
        List<?> list = (List<?>) raw;
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String) {
                result.add((String) item);
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseStringMap(Object raw) {
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        Map<?, ?> rawMap = (Map<?, ?>) raw;
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                result.put((String) entry.getKey(), (String) entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : null;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : defaultVal;
    }
}
