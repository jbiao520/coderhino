package com.coderhino.services.analytics;

import com.coderhino.common.ConfigDirectoryResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultAnalyticsService implements AnalyticsService {

    private final CopyOnWriteArrayList<AnalyticsEvent> buffer;
    private final Path eventsFile;
    private final ObjectMapper objectMapper;

    public DefaultAnalyticsService() {
        this(defaultEventsFile(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public DefaultAnalyticsService(Path eventsFile) {
        this(eventsFile, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public DefaultAnalyticsService(Path eventsFile, ObjectMapper objectMapper) {
        this.eventsFile = eventsFile;
        this.objectMapper = objectMapper;
        this.buffer = new CopyOnWriteArrayList<>();
    }

    @Override
    public void trackEvent(String eventName, String payload) {
        if (eventName == null || eventName.isBlank()) {
            return;
        }
        Map<String, Object> properties = payload != null
                ? Map.of("payload", payload)
                : Map.of();
        buffer.add(new AnalyticsEvent(eventName, properties, java.time.Instant.now()));
    }

    @Override
    public void flush() {
        List<AnalyticsEvent> snapshot = List.copyOf(buffer);
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(eventsFile.getParent());
            StringBuilder sb = new StringBuilder();
            for (AnalyticsEvent event : snapshot) {
                sb.append(objectMapper.writeValueAsString(event)).append('\n');
            }
            Files.writeString(
                    eventsFile,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            // best-effort: swallow flush errors to avoid crashing the CLI
        }
    }

    @Override
    public void shutdown() {
        flush();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public List<AnalyticsEvent> bufferedEvents() {
        return Collections.unmodifiableList(buffer);
    }

    public void clearBuffer() {
        buffer.clear();
    }

    public Path eventsFile() {
        return eventsFile;
    }

    private static Path defaultEventsFile() {
        return ConfigDirectoryResolver.resolveConfigSubdir("analytics-events.jsonl");
    }
}
