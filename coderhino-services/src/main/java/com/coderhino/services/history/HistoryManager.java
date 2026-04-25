package com.coderhino.services.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HistoryManager {
    public record HistoryEntry(String display, Instant timestamp) {}

    private final ObjectMapper objectMapper;
    private final Path historyPath;

    public Path historyPath() {
        return historyPath;
    }

    public HistoryManager(Path historyPath) {
        this(new ObjectMapper().registerModule(new JavaTimeModule()), historyPath);
    }

    public HistoryManager(ObjectMapper objectMapper, Path historyPath) {
        this.objectMapper = objectMapper;
        this.historyPath = historyPath;
    }

    public static HistoryManager createDefault() {
        return new HistoryManager(Path.of(System.getProperty("user.home"), ".coderhino", "history.jsonl"));
    }

    public synchronized void add(String command) {
        var trimmed = command == null ? "" : command.trim();
        if (trimmed.isBlank()) {
            return;
        }
        var entry = new HistoryEntry(trimmed, Instant.now());
        try {
            Files.createDirectories(historyPath.getParent());
            Files.writeString(
                historyPath,
                objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist history entry: %s".formatted(exception.getMessage()), exception);
        }
    }

    public List<HistoryEntry> list() {
        return list(Integer.MAX_VALUE);
    }

    public List<HistoryEntry> list(int limit) {
        if (!Files.exists(historyPath)) {
            return List.of();
        }
        try {
            var entries = new ArrayList<HistoryEntry>();
            for (String line : Files.readAllLines(historyPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                entries.add(objectMapper.readValue(line, HistoryEntry.class));
            }
            if (entries.size() <= limit) {
                return List.copyOf(entries);
            }
            return List.copyOf(entries.subList(entries.size() - limit, entries.size()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read history: %s".formatted(exception.getMessage()), exception);
        }
    }

    public Optional<HistoryEntry> last() {
        var all = list();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(all.get(all.size() - 1));
    }

    public synchronized void removeLast() {
        if (!Files.exists(historyPath)) {
            return;
        }
        try {
            var entries = new ArrayList<HistoryEntry>();
            for (String line : Files.readAllLines(historyPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                entries.add(objectMapper.readValue(line, HistoryEntry.class));
            }
            if (entries.isEmpty()) {
                return;
            }
            entries.remove(entries.size() - 1);
            Files.writeString(historyPath, "");
            for (HistoryEntry entry : entries) {
                Files.writeString(
                    historyPath,
                    objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to remove last history entry: %s".formatted(exception.getMessage()), exception);
        }
    }

    public int size() {
        return list().size();
    }

    public boolean contains(String command) {
        var trimmed = command == null ? "" : command.trim();
        if (trimmed.isBlank()) {
            return false;
        }
        return list().stream().anyMatch(entry -> entry.display().equals(trimmed));
    }

    public List<HistoryEntry> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        var term = keyword.toLowerCase(Locale.ROOT);
        return list().stream()
            .filter(entry -> entry.display().toLowerCase(Locale.ROOT).contains(term))
            .toList();
    }

    public synchronized void clear() {
        if (!Files.exists(historyPath)) {
            return;
        }
        try {
            Files.deleteIfExists(historyPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clear history: %s".formatted(exception.getMessage()), exception);
        }
    }
}