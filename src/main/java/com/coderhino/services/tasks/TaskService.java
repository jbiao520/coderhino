package com.coderhino.services.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TaskService {
    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Map<UUID, TaskRecord> tasks = new LinkedHashMap<>();
    private final Map<UUID, CompletableFuture<String>> futures = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> progressMessages = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public TaskService() {
        this(defaultObjectMapper(), null);
    }

    public TaskService(Path storagePath) {
        this(defaultObjectMapper(), storagePath);
    }

    public TaskService(ObjectMapper objectMapper, Path storagePath) {
        this(objectMapper, storagePath, Executors.newCachedThreadPool());
    }

    public TaskService(ObjectMapper objectMapper, Path storagePath, ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
        this.executor = executor;
        loadFromDisk();
    }

    public TaskRecord create(String description) {
        var now = Instant.now();
        var record = new TaskRecord(UUID.randomUUID(), description, TaskStatus.RUNNING.value(), now, now);
        tasks.put(record.id(), record);
        persist();
        return record;
    }

    /**
     * Submit an async task for real background execution.
     * The task starts in PENDING status, transitions to RUNNING when execution begins,
     * and ends in DONE (success) or FAILED (error). The callable's return value is
     * stored as the task's output.
     */
    public TaskRecord submit(String description, Callable<String> task) {
        return submit(description, null, null, task);
    }

    public TaskRecord submit(String description, String projectId, String sessionId, Callable<String> task) {
        var now = Instant.now();
        var id = UUID.randomUUID();
        var record = new TaskRecord(id, description, TaskStatus.PENDING.value(), null, now, now, projectId, sessionId);
        tasks.put(id, record);
        progressMessages.put(id, Collections.synchronizedList(new ArrayList<>()));

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            updateStatus(id, TaskStatus.RUNNING);
            try {
                var result = task.call();
                setOutput(id, result);
                updateStatus(id, TaskStatus.DONE);
                return result;
            } catch (Exception e) {
                updateStatus(id, TaskStatus.FAILED);
                return null;
            }
        }, executor);

        futures.put(id, future);
        persist();
        return record;
    }

    public List<TaskRecord> list() {
        return new ArrayList<>(tasks.values());
    }

    public List<TaskRecord> listCompletedAfter(Instant since) {
        return tasks.values().stream()
            .filter(record -> TaskStatus.DONE.value().equals(record.status()))
            .filter(record -> since == null || (record.updatedAt() != null && !record.updatedAt().isBefore(since)))
            .sorted(java.util.Comparator.comparing(TaskRecord::updatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .toList();
    }

    public Optional<TaskRecord> get(String id) {
        try {
            return Optional.ofNullable(tasks.get(UUID.fromString(id)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<String> getOutput(String id) {
        return get(id).map(TaskRecord::output);
    }

    /**
     * Retrieve task output, blocking briefly (max 100ms) if the task is still running.
     * Returns empty if task doesn't exist, is still running after timeout, or has no output.
     */
    public Optional<String> getOutputAwait(String id) {
        try {
            var uuid = UUID.fromString(id);
            var future = futures.get(uuid);
            if (future == null) {
                return get(id).map(TaskRecord::output);
            }
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Task still running — return whatever output exists so far
            } catch (Exception ignored) {
                // Task failed or was interrupted
            }
            return get(id).map(TaskRecord::output);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<TaskRecord> stop(String id) {
        return update(id, "stopped");
    }

    /**
     * Cancel a running background task. Returns true if cancellation was requested
     * successfully (the future was cancelled), false if the task was already done
     * or didn't exist.
     */
    public boolean cancel(String id) {
        try {
            var uuid = UUID.fromString(id);
            var future = futures.get(uuid);
            if (future == null) {
                return false;
            }
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                updateStatus(uuid, TaskStatus.CANCELLED);
            }
            return cancelled;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Optional<TaskRecord> update(String id, String status) {
        var normalizedStatus = status == null ? "" : status.trim();
        if (normalizedStatus.isBlank()) {
            return Optional.empty();
        }
        return get(id).map(existing -> {
            var updated = new TaskRecord(
                existing.id(),
                existing.description(),
                normalizedStatus,
                existing.output(),
                existing.createdAt(),
                Instant.now(),
                existing.projectId(),
                existing.sessionId()
            );
            tasks.put(updated.id(), updated);
            persist();
            return updated;
        });
    }

    public Optional<TaskRecord> delete(String id) {
        try {
            var removed = tasks.remove(UUID.fromString(id));
            if (removed == null) {
                return Optional.empty();
            }
            futures.remove(removed.id());
            progressMessages.remove(removed.id());
            persist();
            return Optional.of(removed);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void reportProgress(String id, String message) {
        try {
            var uuid = UUID.fromString(id);
            var messages = progressMessages.get(uuid);
            if (messages != null) {
                messages.add(message);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    public List<String> getProgressMessages(String id) {
        try {
            var messages = progressMessages.get(UUID.fromString(id));
            return messages != null ? List.copyOf(messages) : List.of();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void updateStatus(UUID id, TaskStatus status) {
        var existing = tasks.get(id);
        if (existing != null) {
            var updated = new TaskRecord(
                existing.id(),
                existing.description(),
                status.value(),
                existing.output(),
                existing.createdAt(),
                Instant.now(),
                existing.projectId(),
                existing.sessionId()
            );
            tasks.put(id, updated);
            persist();
        }
    }

    private void setOutput(UUID id, String output) {
        var existing = tasks.get(id);
        if (existing != null) {
            var updated = new TaskRecord(
                existing.id(),
                existing.description(),
                existing.status(),
                output,
                existing.createdAt(),
                Instant.now(),
                existing.projectId(),
                existing.sessionId()
            );
            tasks.put(id, updated);
            persist();
        }
    }

    private void loadFromDisk() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            var content = Files.readString(storagePath);
            if (content.isBlank()) {
                return;
            }
            var stored = objectMapper.readValue(content, TaskRecord[].class);
            Arrays.stream(stored).forEach(record -> tasks.put(record.id(), record));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load tasks: %s".formatted(exception.getMessage()), exception);
        }
    }

    private void persist() {
        if (storagePath == null) {
            return;
        }
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            Files.writeString(storagePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks.values()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist tasks: %s".formatted(exception.getMessage()), exception);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
