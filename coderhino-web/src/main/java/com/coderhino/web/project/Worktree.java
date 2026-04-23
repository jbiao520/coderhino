package com.coderhino.web.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public class Worktree {

    public static final String DEFAULT_WORKTREE_ID = "default";
    public static final String DEFAULT_WORKTREE_NAME = "default";

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;

    @JsonProperty("defaultWorktree")
    private boolean defaultWorktree;

    @JsonProperty("managed")
    private boolean managed;

    @JsonProperty("createdAt")
    private Instant createdAt;

    public Worktree() {
    }

    public Worktree(String id, String name, String path, boolean defaultWorktree, boolean managed, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.path = normalizePath(path);
        this.defaultWorktree = defaultWorktree;
        this.managed = managed;
        this.createdAt = createdAt;
    }

    public static Worktree defaultForProject(String projectPath) {
        var now = Instant.now();
        return new Worktree(DEFAULT_WORKTREE_ID, DEFAULT_WORKTREE_NAME, projectPath, true, false, now);
    }

    public static Worktree managed(String name, String path) {
        return new Worktree(UUID.randomUUID().toString(), name, path, false, true, Instant.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = normalizePath(path); }

    public boolean isDefaultWorktree() { return defaultWorktree; }
    public void setDefaultWorktree(boolean defaultWorktree) { this.defaultWorktree = defaultWorktree; }

    public boolean isManaged() { return managed; }
    public void setManaged(boolean managed) { this.managed = managed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        return Path.of(rawPath).toAbsolutePath().normalize().toString();
    }
}
