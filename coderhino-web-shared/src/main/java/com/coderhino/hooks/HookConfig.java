package com.coderhino.hooks;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class HookConfig {

    private final Map<HookEvent, List<HookEntry>> entries;

    public HookConfig(Map<HookEvent, List<HookEntry>> entries) {
        this.entries = entries;
    }

    public List<HookEntry> forEvent(HookEvent event) {
        List<HookEntry> list = entries.get(event);
        return list != null ? list : List.of();
    }

    public static HookConfig empty() {
        return new HookConfig(new EnumMap<>(HookEvent.class));
    }

    public static HookConfig load(Path hooksFile) {
        if (!Files.exists(hooksFile)) {
            return empty();
        }
        try {
            var mapper = new ObjectMapper();
            var root = mapper.readTree(hooksFile.toFile());
            var hooksNode = root.get("hooks");
            if (hooksNode == null || !hooksNode.isObject()) {
                return empty();
            }
            Map<HookEvent, List<HookEntry>> entries = new EnumMap<>(HookEvent.class);
            var fieldNames = hooksNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                HookEvent event;
                try {
                    event = HookEvent.fromJsonKey(fieldName);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                var arrayNode = hooksNode.get(fieldName);
                if (arrayNode == null || !arrayNode.isArray()) {
                    continue;
                }
                List<HookEntry> hookEntries = new ArrayList<>();
                for (var element : arrayNode) {
                    var commandNode = element.get("command");
                    var patternNode = element.get("pattern");
                    String command = commandNode != null ? commandNode.asText() : "";
                    String pattern = patternNode != null ? patternNode.asText() : null;
                    hookEntries.add(new HookEntry(command, pattern));
                }
                entries.put(event, hookEntries);
            }
            return new HookConfig(entries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load hooks configuration from " + hooksFile, e);
        }
    }
}
