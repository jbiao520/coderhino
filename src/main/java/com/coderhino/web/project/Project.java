package com.coderhino.web.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;

    @JsonProperty("lastOpened")
    private Instant lastOpened;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("workspaceEnabled")
    private boolean workspaceEnabled;

    @JsonProperty("worktrees")
    private List<Worktree> worktrees;

    public Project() {
    }

    public Project(String id, String name, String path, Instant lastOpened, Instant createdAt) {
        this(id, name, path, lastOpened, createdAt, false, new ArrayList<>());
    }

    public Project(String id, String name, String path, Instant lastOpened, Instant createdAt, boolean workspaceEnabled, List<Worktree> worktrees) {
        this.id = id;
        this.name = name;
        this.path = Path.of(path).toAbsolutePath().normalize().toString();
        this.lastOpened = lastOpened;
        this.createdAt = createdAt;
        this.workspaceEnabled = workspaceEnabled;
        this.worktrees = worktrees != null ? new ArrayList<>(worktrees) : new ArrayList<>();
        ensureDefaultWorktree();
    }

    public static Project create(String path) {
        var absolutePath = Path.of(path).toAbsolutePath().normalize().toString();
        var name = Path.of(absolutePath).getFileName().toString();
        var now = Instant.now();
        return new Project(UUID.randomUUID().toString(), name, absolutePath, now, now, false, List.of(Worktree.defaultForProject(absolutePath)));
    }

    public void touch() {
        this.lastOpened = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) {
        this.path = Path.of(path).toAbsolutePath().normalize().toString();
        ensureDefaultWorktree();
    }

    public Instant getLastOpened() { return lastOpened; }
    public void setLastOpened(Instant lastOpened) { this.lastOpened = lastOpened; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isWorkspaceEnabled() { return workspaceEnabled; }
    public void setWorkspaceEnabled(boolean workspaceEnabled) { this.workspaceEnabled = workspaceEnabled; }

    public List<Worktree> getWorktrees() {
        ensureDefaultWorktree();
        return worktrees;
    }

    public void setWorktrees(List<Worktree> worktrees) {
        this.worktrees = worktrees != null ? new ArrayList<>(worktrees) : new ArrayList<>();
        ensureDefaultWorktree();
    }

    public Worktree getDefaultWorktree() {
        ensureDefaultWorktree();
        return worktrees.stream()
            .filter(Worktree::isDefaultWorktree)
            .findFirst()
            .orElseGet(() -> {
                var defaultWorktree = Worktree.defaultForProject(path);
                worktrees.add(0, defaultWorktree);
                return defaultWorktree;
            });
    }

    public void ensureDefaultWorktree() {
        if (worktrees == null) {
            worktrees = new ArrayList<>();
        }
        var defaultWorktree = worktrees.stream()
            .filter(Worktree::isDefaultWorktree)
            .findFirst()
            .orElse(null);
        if (defaultWorktree == null) {
            worktrees.add(0, Worktree.defaultForProject(path));
            return;
        }
        defaultWorktree.setId(Worktree.DEFAULT_WORKTREE_ID);
        defaultWorktree.setName(Worktree.DEFAULT_WORKTREE_NAME);
        defaultWorktree.setPath(path);
        defaultWorktree.setDefaultWorktree(true);
        defaultWorktree.setManaged(false);
    }
}
