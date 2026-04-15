package com.coderhino.web.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPersistenceService.class);
    private static final String PROJECTS_DIR = ".coderhino";
    private static final String PROJECTS_FILE = "projects.json";
    private static final String WORKSPACE_STATE_FILE = "project-workspace-state.json";

    private final ConcurrentHashMap<String, Project> projects = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path projectsFile;
    private final Path legacyProjectsFile;
    private final Path workspaceStateFile;
    private volatile ProjectWorkspaceState workspaceState = new ProjectWorkspaceState();

    public ProjectPersistenceService() {
        this(defaultProjectsFile(), legacyProjectsFile(), defaultWorkspaceStateFile());
    }

    public ProjectPersistenceService(Path projectsFile) {
        this(projectsFile, null, defaultWorkspaceStateFile(projectsFile));
    }

    public ProjectPersistenceService(Path projectsFile, Path legacyProjectsFile) {
        this(projectsFile, legacyProjectsFile, defaultWorkspaceStateFile(projectsFile));
    }

    public ProjectPersistenceService(Path projectsFile, Path legacyProjectsFile, Path workspaceStateFile) {
        this.projectsFile = projectsFile;
        this.legacyProjectsFile = legacyProjectsFile;
        this.workspaceStateFile = workspaceStateFile;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void reloadPersistedProjects() {
        var sourceFile = resolveLoadSource();
        if (sourceFile == null) {
            log.info("No projects file found at {}, starting fresh", projectsFile);
            reloadWorkspaceState();
            return;
        }
        try {
            var list = objectMapper.readValue(sourceFile.toFile(), new TypeReference<List<Project>>() {});
            boolean changed = false;
            for (var project : list) {
                project.ensureDefaultWorktree();
                projects.put(project.getId(), project);
                changed = true;
            }
            log.info("Reloaded {} persisted project(s)", list.size());
            if (changed || !projectsFile.equals(sourceFile)) {
                persistAll();
            }
        } catch (IOException e) {
            log.warn("Failed to load projects from {}: {}", sourceFile, e.getMessage());
        }
        reloadWorkspaceState();
    }

    /**
     * Upsert: if path already exists, update lastOpened; otherwise create new.
     * @return project + true if newly created, false if existing was updated
     */
    public ProjectAndCreated createOrTouch(String path) {
        var absolutePath = Path.of(path).toAbsolutePath().normalize().toString();

        for (var existing : projects.values()) {
            if (existing.getPath().equals(absolutePath)) {
                existing.touch();
                existing.ensureDefaultWorktree();
                persistAll();
                return new ProjectAndCreated(existing, false);
            }
        }

        var project = Project.create(path);
        projects.put(project.getId(), project);
        persistAll();
        log.debug("Created new project: {} ({})", project.getName(), project.getId());
        return new ProjectAndCreated(project, true);
    }

    public Optional<Project> find(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    public List<Project> listAllSorted() {
        return projects.values().stream()
            .sorted(Comparator.comparing(Project::getLastOpened).reversed())
            .toList();
    }

    public Collection<Project> listAll() {
        return List.copyOf(projects.values());
    }

    public boolean delete(String id) {
        var removed = projects.remove(id);
        if (removed != null) {
            removeProjectFromWorkspaceState(id);
            persistAll();
            log.debug("Deleted project: {} ({})", removed.getName(), removed.getId());
            return true;
        }
        return false;
    }

    public Optional<Project> enableWorkspace(String id) {
        var project = projects.get(id);
        if (project == null) {
            return Optional.empty();
        }
        project.setWorkspaceEnabled(true);
        project.ensureDefaultWorktree();
        persistAll();
        return Optional.of(project);
    }

    public Optional<Project> rename(String id, String name) {
        var project = projects.get(id);
        if (project == null) {
            return Optional.empty();
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        project.setName(name.trim());
        persistAll();
        return Optional.of(project);
    }

    public Optional<Project> addWorktree(String projectId, Worktree worktree) {
        var project = projects.get(projectId);
        if (project == null) {
            return Optional.empty();
        }
        project.ensureDefaultWorktree();
        project.getWorktrees().add(worktree);
        project.setWorkspaceEnabled(true);
        persistAll();
        return Optional.of(project);
    }

    public Optional<Project> removeWorktree(String projectId, String worktreeId) {
        var project = projects.get(projectId);
        if (project == null) {
            return Optional.empty();
        }
        project.ensureDefaultWorktree();
        var removed = project.getWorktrees().removeIf(worktree -> worktreeId.equals(worktree.getId()) && !worktree.isDefaultWorktree());
        if (!removed) {
            return Optional.empty();
        }
        persistAll();
        return Optional.of(project);
    }

    public Optional<Worktree> findWorktree(String projectId, String worktreeId) {
        var project = find(projectId).orElse(null);
        if (project == null) {
            return Optional.empty();
        }
        project.ensureDefaultWorktree();
        if (worktreeId == null || worktreeId.isBlank()) {
            return Optional.of(project.getDefaultWorktree());
        }
        return project.getWorktrees().stream()
            .filter(worktree -> worktreeId.equals(worktree.getId()))
            .findFirst();
    }

    public Optional<Worktree> resolveWorktree(String projectId, String worktreeId, String cwd) {
        var project = find(projectId).orElse(null);
        if (project == null) {
            return Optional.empty();
        }
        project.ensureDefaultWorktree();
        var byId = findWorktree(projectId, worktreeId);
        if (byId.isPresent()) {
            return byId;
        }
        if (cwd != null && !cwd.isBlank()) {
            var normalizedCwd = Path.of(cwd).toAbsolutePath().normalize().toString();
            var byPath = project.getWorktrees().stream()
                .filter(worktree -> normalizedCwd.equals(worktree.getPath()))
                .findFirst();
            if (byPath.isPresent()) {
                return byPath;
            }
        }
        return Optional.of(project.getDefaultWorktree());
    }

    public synchronized ProjectWorkspaceState getWorkspaceState() {
        var normalized = normalizeWorkspaceState(workspaceState);
        if (workspaceStateChanged(workspaceState, normalized)) {
            workspaceState = normalized;
            persistWorkspaceState();
        }
        return copyWorkspaceState(workspaceState);
    }

    public synchronized ProjectWorkspaceState updateWorkspaceState(ProjectWorkspaceState nextState) {
        workspaceState = normalizeWorkspaceState(nextState);
        persistWorkspaceState();
        return copyWorkspaceState(workspaceState);
    }

    private void persistAll() {
        try {
            var dir = projectsFile.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            var list = new ArrayList<>(projects.values());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(projectsFile.toFile(), list);
        } catch (IOException e) {
            log.error("Failed to persist projects to {}: {}", projectsFile, e.getMessage());
        }
    }

    private synchronized void reloadWorkspaceState() {
        if (!Files.exists(workspaceStateFile)) {
            workspaceState = new ProjectWorkspaceState();
            return;
        }
        try {
            var loaded = objectMapper.readValue(workspaceStateFile.toFile(), ProjectWorkspaceState.class);
            var normalized = normalizeWorkspaceState(loaded);
            boolean changed = workspaceStateChanged(loaded, normalized);
            workspaceState = normalized;
            if (changed) {
                persistWorkspaceState();
            }
        } catch (IOException e) {
            log.warn("Failed to load workspace state from {}: {}", workspaceStateFile, e.getMessage());
            workspaceState = new ProjectWorkspaceState();
        }
    }

    private synchronized void removeProjectFromWorkspaceState(String projectId) {
        var nextIds = workspaceState.getOpenProjectIds().stream()
            .filter(id -> !projectId.equals(id))
            .toList();
        var nextActiveProjectId = projectId.equals(workspaceState.getActiveProjectId())
            ? (nextIds.isEmpty() ? null : nextIds.get(0))
            : workspaceState.getActiveProjectId();
        workspaceState = new ProjectWorkspaceState(nextIds, nextActiveProjectId);
        persistWorkspaceState();
    }

    private ProjectWorkspaceState normalizeWorkspaceState(ProjectWorkspaceState state) {
        if (state == null) {
            return new ProjectWorkspaceState();
        }
        var validIds = new ArrayList<String>();
        for (var projectId : new LinkedHashSet<>(state.getOpenProjectIds() != null ? state.getOpenProjectIds() : List.<String>of())) {
            if (projectId != null && projects.containsKey(projectId)) {
                validIds.add(projectId);
            }
        }
        var activeProjectId = state.getActiveProjectId();
        if (activeProjectId == null || !validIds.contains(activeProjectId)) {
            activeProjectId = validIds.isEmpty() ? null : validIds.get(0);
        }
        return new ProjectWorkspaceState(validIds, activeProjectId);
    }

    private static ProjectWorkspaceState copyWorkspaceState(ProjectWorkspaceState state) {
        return new ProjectWorkspaceState(state.getOpenProjectIds(), state.getActiveProjectId());
    }

    private synchronized void persistWorkspaceState() {
        try {
            var dir = workspaceStateFile.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(workspaceStateFile.toFile(), workspaceState);
        } catch (IOException e) {
            log.error("Failed to persist workspace state to {}: {}", workspaceStateFile, e.getMessage());
        }
    }

    private static boolean workspaceStateChanged(ProjectWorkspaceState current, ProjectWorkspaceState next) {
        return !current.getOpenProjectIds().equals(next.getOpenProjectIds())
            || !java.util.Objects.equals(current.getActiveProjectId(), next.getActiveProjectId());
    }

    private Path resolveLoadSource() {
        if (Files.exists(projectsFile)) {
            return projectsFile;
        }
        if (legacyProjectsFile != null && Files.exists(legacyProjectsFile)) {
            log.info("Migrating persisted projects from legacy path {} to {}", legacyProjectsFile, projectsFile);
            return legacyProjectsFile;
        }
        return null;
    }

    private static Path defaultProjectsFile() {
        return Path.of(System.getProperty("user.home"), PROJECTS_DIR, PROJECTS_FILE);
    }

    private static Path defaultWorkspaceStateFile() {
        return Path.of(System.getProperty("user.home"), PROJECTS_DIR, WORKSPACE_STATE_FILE);
    }

    private static Path defaultWorkspaceStateFile(Path projectsFile) {
        var parent = projectsFile.toAbsolutePath().normalize().getParent();
        return (parent != null ? parent : Path.of("")).resolve(WORKSPACE_STATE_FILE);
    }

    private static Path legacyProjectsFile() {
        return Path.of("").toAbsolutePath().normalize().resolve(PROJECTS_DIR).resolve(PROJECTS_FILE);
    }

    public static class ProjectAndCreated {
        private final Project project;
        private final boolean created;

        public ProjectAndCreated(Project project, boolean created) {
            this.project = project;
            this.created = created;
        }

        public Project getProject() { return project; }
        public boolean isCreated() { return created; }
    }
}
