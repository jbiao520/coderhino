package com.coderhino.services.analytics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Feature-flag service backed by environment variables and a JSON config file.
 * <p>
 * Resolution order (highest priority first):
 * <ol>
 *   <li>Environment variable {@code CLAUDECODE_FLAG_<UPPER_SNAKE_NAME>}
 *       — values {@code true}/{@code 1}/{@code yes} enable,
 *       {@code false}/{@code 0}/{@code no} disable.</li>
 *   <li>Config file (default {@code ~/.claude/feature-flags.json})
 *       — JSON object mapping flag names to boolean/string values.</li>
 *   <li>Default — all flags return {@code false}.</li>
 * </ol>
 */
public final class EnvFeatureFlagService implements FeatureFlagService {

    private static final String ENV_PREFIX = "CLAUDECODE_FLAG_";
    private static final Path DEFAULT_CONFIG_PATH =
            Path.of(System.getProperty("user.home"), ".claude", "feature-flags.json");

    private final Path configPath;
    private final Function<String, String> envProvider;
    private final Function<Set<String>, Map<String, String>> envMapProvider;
    private volatile Map<String, Object> fileFlags;

    public EnvFeatureFlagService() {
        this(DEFAULT_CONFIG_PATH);
    }

    public EnvFeatureFlagService(Path configPath) {
        this(configPath, System::getenv);
    }

    EnvFeatureFlagService(Path configPath, Function<String, String> envProvider) {
        this(configPath, envProvider, name -> {
            Map<String, String> filtered = new HashMap<>();
            System.getenv().forEach((k, v) -> {
                if (k.startsWith(ENV_PREFIX)) filtered.put(k, v);
            });
            return filtered;
        });
    }

    EnvFeatureFlagService(Path configPath, Function<String, String> envProvider,
                          Function<Set<String>, Map<String, String>> envMapProvider) {
        this.configPath = configPath;
        this.envProvider = envProvider;
        this.envMapProvider = envMapProvider;
        this.fileFlags = loadFromFile();
    }

    @Override
    public boolean isEnabled(String flagName) {
        return isEnabled(flagName, false);
    }

    @Override
    public boolean isEnabled(String flagName, boolean defaultValue) {
        String envValue = envProvider.apply(envVarName(flagName));
        if (envValue != null) {
            return parseBoolean(envValue);
        }

        Object fileValue = fileFlags.get(flagName);
        if (fileValue instanceof Boolean b) {
            return b;
        }
        if (fileValue instanceof String s) {
            return parseBoolean(s);
        }

        return defaultValue;
    }

    @Override
    public String getString(String flagName, String defaultValue) {
        String envValue = envProvider.apply(envVarName(flagName));
        if (envValue != null) {
            return envValue;
        }

        Object fileValue = fileFlags.get(flagName);
        if (fileValue instanceof String s) {
            return s;
        }

        return defaultValue;
    }

    @Override
    public Set<String> flagNames() {
        Map<String, Object> snapshot = fileFlags;
        Set<String> fromFile = snapshot != null
                ? snapshot.keySet()
                : Collections.emptySet();

        Map<String, String> envMap = envMapProvider.apply(Set.of());
        Set<String> fromEnv = envMap.keySet().stream()
                .filter(k -> k.startsWith(ENV_PREFIX))
                .map(k -> k.substring(ENV_PREFIX.length()))
                .collect(Collectors.toSet());

        fromEnv.addAll(fromFile);
        return Collections.unmodifiableSet(fromEnv);
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>(fileFlags);

        envMapProvider.apply(Set.of()).forEach((k, v) -> {
            if (k.startsWith(ENV_PREFIX)) {
                String flagName = k.substring(ENV_PREFIX.length());
                if ("true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v)
                        || "false".equalsIgnoreCase(v) || "0".equals(v) || "no".equalsIgnoreCase(v)) {
                    result.put(flagName, parseBoolean(v));
                } else {
                    result.put(flagName, v);
                }
            }
        });

        return Collections.unmodifiableMap(result);
    }

    /**
     * Reload the config file from disk. Environment variables are always
     * read live and are not affected by reload.
     */
    public void reload() {
        this.fileFlags = loadFromFile();
    }

    private Map<String, Object> loadFromFile() {
        if (!Files.isRegularFile(configPath)) {
            return new HashMap<>();
        }
        try {
            String json = Files.readString(configPath);
            return parseJsonObject(json);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static String envVarName(String flagName) {
        return ENV_PREFIX + flagName.toUpperCase();
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }

    static Map<String, Object> parseJsonObject(String json) {
        String trimmed = json.trim();
        if (trimmed.isEmpty() || trimmed.equals("{}")) {
            return new HashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        trimmed = trimmed.substring(1, trimmed.length() - 1).trim();

        if (trimmed.isEmpty()) {
            return result;
        }

        int i = 0;
        while (i < trimmed.length()) {
            i = skipWhitespace(trimmed, i);
            if (i >= trimmed.length()) break;

            if (trimmed.charAt(i) != '"') break;
            int[] keyResult = readQuotedString(trimmed, i);
            String key = trimmed.substring(keyResult[0], keyResult[1]);
            i = keyResult[1] + 1;

            i = skipWhitespace(trimmed, i);
            if (i >= trimmed.length() || trimmed.charAt(i) != ':') break;
            i++;

            i = skipWhitespace(trimmed, i);
            if (i >= trimmed.length()) break;

            Object value;
            if (trimmed.charAt(i) == '"') {
                int[] strResult = readQuotedString(trimmed, i);
                value = trimmed.substring(strResult[0], strResult[1]);
                i = strResult[1] + 1;
            } else if (trimmed.startsWith("true", i)) {
                value = Boolean.TRUE;
                i += 4;
            } else if (trimmed.startsWith("false", i)) {
                value = Boolean.FALSE;
                i += 5;
            } else if (trimmed.startsWith("null", i)) {
                value = null;
                i += 4;
            } else {
                int start = i;
                while (i < trimmed.length() && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') {
                    i++;
                }
                String numStr = trimmed.substring(start, i).trim();
                try {
                    if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                        value = Double.parseDouble(numStr);
                    } else {
                        value = Long.parseLong(numStr);
                    }
                } catch (NumberFormatException e) {
                    value = numStr;
                }
            }

            if (value != null) {
                result.put(key, value);
            }

            i = skipWhitespace(trimmed, i);
            if (i < trimmed.length() && trimmed.charAt(i) == ',') {
                i++;
            }
        }

        return result;
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int[] readQuotedString(String s, int start) {
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i += 2;
            } else if (c == '"') {
                return new int[]{start + 1, i};
            } else {
                i++;
            }
        }
        return new int[]{start + 1, i};
    }
}
