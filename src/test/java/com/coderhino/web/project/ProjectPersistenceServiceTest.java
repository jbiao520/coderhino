package com.coderhino.web.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPersistenceServiceTest {

    @Test
    void reloadPersistedProjectsBackfillsDefaultWorktree(@TempDir Path tempDir) throws Exception {
        var projectsFile = tempDir.resolve("projects.json");
        Files.writeString(projectsFile, """
            [
              {
                "id": "project-1",
                "name": "alpha",
                "path": "/tmp/alpha",
                "lastOpened": "2026-04-11T00:00:00Z",
                "createdAt": "2026-04-11T00:00:00Z"
              }
            ]
            """);

        var service = new ProjectPersistenceService(projectsFile);
        service.reloadPersistedProjects();

        var project = service.find("project-1").orElseThrow();
        assertFalse(project.isWorkspaceEnabled());
        assertEquals(1, project.getWorktrees().size());
        assertEquals(Worktree.DEFAULT_WORKTREE_ID, project.getWorktrees().get(0).getId());
        assertEquals("/tmp/alpha", project.getWorktrees().get(0).getPath());
        assertNotNull(service.findWorktree("project-1", Worktree.DEFAULT_WORKTREE_ID).orElse(null));
    }

    @Test
    void enableWorkspaceKeepsDefaultWorktree(@TempDir Path tempDir) {
        var service = new ProjectPersistenceService(tempDir.resolve("projects.json"));
        var created = service.createOrTouch(tempDir.resolve("project").toString()).getProject();

        var enabled = service.enableWorkspace(created.getId()).orElseThrow();

        assertTrue(enabled.isWorkspaceEnabled());
        assertEquals(1, enabled.getWorktrees().size());
        assertEquals(Worktree.DEFAULT_WORKTREE_ID, enabled.getDefaultWorktree().getId());
    }

    @Test
    void renamePersistsProjectName(@TempDir Path tempDir) {
        var projectsFile = tempDir.resolve("projects.json");
        var service = new ProjectPersistenceService(projectsFile);
        var created = service.createOrTouch(tempDir.resolve("project").toString()).getProject();

        var renamed = service.rename(created.getId(), "Renamed Project").orElseThrow();
        assertEquals("Renamed Project", renamed.getName());

        var reloaded = new ProjectPersistenceService(projectsFile);
        reloaded.reloadPersistedProjects();
        assertEquals("Renamed Project", reloaded.find(created.getId()).orElseThrow().getName());
    }

    @Test
    void recreateServiceReloadsPersistedProjects(@TempDir Path tempDir) {
        var projectsFile = tempDir.resolve("projects.json");
        var created = new ProjectPersistenceService(projectsFile)
            .createOrTouch(tempDir.resolve("project").toString())
            .getProject();

        var reloaded = new ProjectPersistenceService(projectsFile);
        reloaded.reloadPersistedProjects();

        var project = reloaded.find(created.getId()).orElseThrow();
        assertEquals(created.getId(), project.getId());
        assertEquals(created.getPath(), project.getPath());
        assertNotNull(reloaded.findWorktree(created.getId(), Worktree.DEFAULT_WORKTREE_ID).orElse(null));
    }

    @Test
    void reloadMigratesProjectsFromLegacyLocation(@TempDir Path tempDir) throws Exception {
        var projectsFile = tempDir.resolve("home").resolve(".coderhino").resolve("projects.json");
        var legacyFile = tempDir.resolve("workspace").resolve(".coderhino").resolve("projects.json");
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, """
            [
              {
                "id": "legacy-project-1",
                "name": "alpha",
                "path": "/tmp/alpha",
                "lastOpened": "2026-04-11T00:00:00Z",
                "createdAt": "2026-04-11T00:00:00Z"
              }
            ]
            """);

        var service = new ProjectPersistenceService(projectsFile, legacyFile);
        service.reloadPersistedProjects();

        var project = service.find("legacy-project-1").orElseThrow();
        assertEquals("legacy-project-1", project.getId());
        assertTrue(Files.exists(projectsFile));
        assertTrue(Files.exists(legacyFile));

        var migratedJson = Files.readString(projectsFile);
        assertTrue(migratedJson.contains("legacy-project-1"));
    }

    @Test
    void reloadPrefersDurableProjectsFileWhenBothLocationsExist(@TempDir Path tempDir) throws Exception {
        var projectsFile = tempDir.resolve("home").resolve(".coderhino").resolve("projects.json");
        var legacyFile = tempDir.resolve("workspace").resolve(".coderhino").resolve("projects.json");
        Files.createDirectories(projectsFile.getParent());
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(projectsFile, """
            [
              {
                "id": "durable-project",
                "name": "durable",
                "path": "/tmp/durable",
                "lastOpened": "2026-04-11T00:00:00Z",
                "createdAt": "2026-04-11T00:00:00Z"
              }
            ]
            """);
        Files.writeString(legacyFile, """
            [
              {
                "id": "legacy-project",
                "name": "legacy",
                "path": "/tmp/legacy",
                "lastOpened": "2026-04-11T00:00:00Z",
                "createdAt": "2026-04-11T00:00:00Z"
              }
            ]
            """);

        var service = new ProjectPersistenceService(projectsFile, legacyFile);
        service.reloadPersistedProjects();

        assertTrue(service.find("durable-project").isPresent());
        assertNull(service.find("legacy-project").orElse(null));
    }

    @Test
    void workspaceStateSurvivesServiceRestart(@TempDir Path tempDir) {
        var projectsFile = tempDir.resolve("projects.json");
        var workspaceStateFile = tempDir.resolve("project-workspace-state.json");
        var service = new ProjectPersistenceService(projectsFile, null, workspaceStateFile);

        var first = service.createOrTouch(tempDir.resolve("project-a").toString()).getProject();
        var second = service.createOrTouch(tempDir.resolve("project-b").toString()).getProject();
        service.updateWorkspaceState(new ProjectWorkspaceState(List.of(first.getId(), second.getId()), second.getId()));

        var reloaded = new ProjectPersistenceService(projectsFile, null, workspaceStateFile);
        reloaded.reloadPersistedProjects();

        var workspaceState = reloaded.getWorkspaceState();
        assertEquals(List.of(first.getId(), second.getId()), workspaceState.getOpenProjectIds());
        assertEquals(second.getId(), workspaceState.getActiveProjectId());
    }

    @Test
    void workspaceStateDropsUnknownProjectIdsOnLoad(@TempDir Path tempDir) throws Exception {
        var projectsFile = tempDir.resolve("projects.json");
        var workspaceStateFile = tempDir.resolve("project-workspace-state.json");
        var created = new ProjectPersistenceService(projectsFile, null, workspaceStateFile)
            .createOrTouch(tempDir.resolve("project-a").toString())
            .getProject();

        Files.writeString(workspaceStateFile, """
            {
              "openProjectIds": ["missing-project", "%s"],
              "activeProjectId": "missing-project"
            }
            """.formatted(created.getId()));

        var reloaded = new ProjectPersistenceService(projectsFile, null, workspaceStateFile);
        reloaded.reloadPersistedProjects();

        var workspaceState = reloaded.getWorkspaceState();
        assertEquals(List.of(created.getId()), workspaceState.getOpenProjectIds());
        assertEquals(created.getId(), workspaceState.getActiveProjectId());
    }
}
