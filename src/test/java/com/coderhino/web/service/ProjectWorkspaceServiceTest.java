package com.coderhino.web.service;

import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.project.Worktree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkspaceServiceTest {

    @Test
    void createManagedWorktreePersistsMetadataAfterSuccessfulCreation(@TempDir Path tempDir) throws Exception {
        var projectRoot = Files.createDirectories(tempDir.resolve("repo"));
        var projectService = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var project = projectService.createOrTouch(projectRoot.toString()).getProject();
        var service = new ProjectWorkspaceService(projectService, tempDir.resolve("managed"), (root, worktreePath) -> {
            Files.createDirectories(worktreePath);
            Files.writeString(worktreePath.resolve("README.md"), "ok");
        });

        var updated = service.createManagedWorktree(project.getId(), "Feature A").orElseThrow();

        assertTrue(updated.isWorkspaceEnabled());
        assertEquals(2, updated.getWorktrees().size());
        assertEquals("Feature A", updated.getWorktrees().get(1).getName());
        assertTrue(Files.exists(Path.of(updated.getWorktrees().get(1).getPath())));
    }

    @Test
    void createManagedWorktreeRejectsInvalidName(@TempDir Path tempDir) {
        var projectService = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var project = projectService.createOrTouch(tempDir.resolve("repo").toString()).getProject();
        var service = new ProjectWorkspaceService(projectService, tempDir.resolve("managed"), (root, worktreePath) -> {
        });

        var error = assertThrows(IllegalArgumentException.class, () -> service.createManagedWorktree(project.getId(), "../bad"));

        assertEquals("Workspace name may only contain letters, numbers, spaces, dots, underscores, and hyphens", error.getMessage());
    }

    @Test
    void createManagedWorktreeRejectsDuplicateName(@TempDir Path tempDir) throws Exception {
        var projectRoot = Files.createDirectories(tempDir.resolve("repo"));
        var projectService = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var project = projectService.createOrTouch(projectRoot.toString()).getProject();
        var service = new ProjectWorkspaceService(projectService, tempDir.resolve("managed"), (root, worktreePath) -> Files.createDirectories(worktreePath));
        service.createManagedWorktree(project.getId(), "Feature A");

        var error = assertThrows(IllegalArgumentException.class, () -> service.createManagedWorktree(project.getId(), "Feature A"));

        assertEquals("A workspace with that name already exists", error.getMessage());
    }

    @Test
    void createManagedWorktreeDoesNotPersistMetadataWhenGitCreationFails(@TempDir Path tempDir) {
        var projectService = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var project = projectService.createOrTouch(tempDir.resolve("repo").toString()).getProject();
        var service = new ProjectWorkspaceService(projectService, tempDir.resolve("managed"), (root, worktreePath) -> {
            throw new IOException("git worktree add failed");
        });

        var error = assertThrows(IOException.class, () -> service.createManagedWorktree(project.getId(), "Feature A"));

        assertEquals("git worktree add failed", error.getMessage());
        var reloaded = projectService.find(project.getId()).orElseThrow();
        assertEquals(1, reloaded.getWorktrees().size());
        assertEquals(Worktree.DEFAULT_WORKTREE_ID, reloaded.getDefaultWorktree().getId());
    }

    @Test
    void deleteManagedWorktreeRemovesMetadataAfterSuccessfulDeletion(@TempDir Path tempDir) throws Exception {
        var projectRoot = Files.createDirectories(tempDir.resolve("repo"));
        var projectService = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var project = projectService.createOrTouch(projectRoot.toString()).getProject();
        var service = new ProjectWorkspaceService(projectService, tempDir.resolve("managed"), new ProjectWorkspaceService.GitWorktreeExecutor() {
            @Override
            public void create(Path root, Path worktreePath) throws IOException {
                Files.createDirectories(worktreePath);
            }

            @Override
            public void delete(Path root, Path worktreePath) {
            }
        });
        var updated = service.createManagedWorktree(project.getId(), "Feature A").orElseThrow();
        var worktreeId = updated.getWorktrees().get(1).getId();

        var afterDelete = service.deleteManagedWorktree(project.getId(), worktreeId).orElseThrow();

        assertEquals(1, afterDelete.getWorktrees().size());
        assertFalse(afterDelete.getWorktrees().stream().anyMatch(worktree -> worktreeId.equals(worktree.getId())));
    }

    @Test
    void deleteManagedWorktreeRejectsDefaultWorktree(@TempDir Path tempDir) {
        var projectService = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var project = projectService.createOrTouch(tempDir.resolve("repo").toString()).getProject();
        var service = new ProjectWorkspaceService(projectService, tempDir.resolve("managed"), (root, worktreePath) -> {
        });

        var error = assertThrows(IllegalArgumentException.class, () -> service.deleteManagedWorktree(project.getId(), Worktree.DEFAULT_WORKTREE_ID));

        assertEquals("Only managed worktrees can be deleted", error.getMessage());
    }
}
