package com.coderhino.web.service;

import com.coderhino.web.project.Project;
import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.project.Worktree;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProjectWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(ProjectWorkspaceService.class);

    private final ProjectPersistenceService projectService;
    private final Path worktreeRoot;
    private final GitWorktreeExecutor gitWorktreeExecutor;

    @Autowired
    public ProjectWorkspaceService(ProjectPersistenceService projectService) {
        this(
            projectService,
            Path.of(System.getProperty("user.home"), ".coderhino", "worktrees"),
            ProjectWorkspaceService::runGitWorktreeAdd
        );
    }

    ProjectWorkspaceService(ProjectPersistenceService projectService, Path worktreeRoot, GitWorktreeExecutor gitWorktreeExecutor) {
        this.projectService = projectService;
        this.worktreeRoot = worktreeRoot;
        this.gitWorktreeExecutor = gitWorktreeExecutor;
    }

    public Optional<Project> enableWorkspace(String projectId) {
        return projectService.enableWorkspace(projectId);
    }

    public Optional<Project> createManagedWorktree(String projectId, String requestedName) throws IOException {
        var project = projectService.find(projectId);
        if (project.isEmpty()) {
            return Optional.empty();
        }

        var normalizedName = validateName(requestedName);
        var existingNames = project.get().getWorktrees().stream()
            .map(Worktree::getName)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .toList();
        if (existingNames.contains(normalizedName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("A workspace with that name already exists");
        }

        var worktree = Worktree.managed(normalizedName, worktreeRoot
            .resolve(projectId)
            .resolve(safePathSegment(normalizedName) + "-" + System.currentTimeMillis())
            .toString());
        var worktreePath = Path.of(worktree.getPath());
        Files.createDirectories(worktreePath.getParent());

        try {
            gitWorktreeExecutor.create(projectRoot(project.get()), worktreePath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git worktree creation was interrupted", e);
        } catch (IOException e) {
            cleanupFailedWorktree(worktreePath);
            throw e;
        }

        var updatedProject = projectService.addWorktree(projectId, worktree);
        if (updatedProject.isEmpty()) {
            cleanupFailedWorktree(worktreePath);
        }
        return updatedProject;
    }

    public Optional<Project> deleteManagedWorktree(String projectId, String worktreeId) throws IOException {
        var project = projectService.find(projectId);
        if (project.isEmpty()) {
            return Optional.empty();
        }
        var worktree = projectService.findWorktree(projectId, worktreeId);
        if (worktree.isEmpty()) {
            return Optional.empty();
        }
        if (worktree.get().isDefaultWorktree() || !worktree.get().isManaged()) {
            throw new IllegalArgumentException("Only managed worktrees can be deleted");
        }

        var worktreePath = Path.of(worktree.get().getPath());
        try {
            gitWorktreeExecutor.delete(projectRoot(project.get()), worktreePath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git worktree deletion was interrupted", e);
        }

        var updatedProject = projectService.removeWorktree(projectId, worktreeId);
        if (updatedProject.isEmpty()) {
            throw new IOException("Failed to remove worktree metadata");
        }
        return updatedProject;
    }

    private Path projectRoot(Project project) {
        return Path.of(project.getPath()).toAbsolutePath().normalize();
    }

    private static String validateName(String requestedName) {
        if (requestedName == null) {
            throw new IllegalArgumentException("Workspace name is required");
        }
        var trimmed = requestedName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }
        if (!trimmed.matches("[A-Za-z0-9][A-Za-z0-9 ._-]*")) {
            throw new IllegalArgumentException("Workspace name may only contain letters, numbers, spaces, dots, underscores, and hyphens");
        }
        return trimmed;
    }

    private static String safePathSegment(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-");
    }

    private static void runGitWorktreeAdd(Path projectRoot, Path worktreePath) throws IOException, InterruptedException {
        var process = new ProcessBuilder("git", "worktree", "add", "--detach", worktreePath.toString(), "HEAD")
            .directory(projectRoot.toFile())
            .start();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git worktree add failed: " + (stderr.isBlank() ? stdout : stderr));
        }
    }

    private static void runGitWorktreeRemove(Path projectRoot, Path worktreePath) throws IOException, InterruptedException {
        var process = new ProcessBuilder("git", "worktree", "remove", worktreePath.toString())
            .directory(projectRoot.toFile())
            .start();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("git worktree remove failed: " + (stderr.isBlank() ? stdout : stderr));
        }
    }

    private void cleanupFailedWorktree(Path worktreePath) {
        try {
            if (Files.exists(worktreePath) && Files.isDirectory(worktreePath) && Files.list(worktreePath).findAny().isEmpty()) {
                Files.deleteIfExists(worktreePath);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up worktree path {} after error", worktreePath, e);
        }
    }

    @FunctionalInterface
    interface GitWorktreeExecutor {
        void create(Path projectRoot, Path worktreePath) throws IOException, InterruptedException;

        default void delete(Path projectRoot, Path worktreePath) throws IOException, InterruptedException {
            runGitWorktreeRemove(projectRoot, worktreePath);
        }
    }
}
