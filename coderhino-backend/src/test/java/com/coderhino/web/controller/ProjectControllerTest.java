package com.coderhino.web.controller;

import com.coderhino.web.dto.ProjectDto;
import com.coderhino.web.dto.ProjectWorkspaceStateDto;
import com.coderhino.web.dto.WorktreeDto;
import com.coderhino.web.project.Project;
import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.project.ProjectWorkspaceState;
import com.coderhino.web.project.Worktree;
import com.coderhino.web.service.ProjectWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectControllerTest {

    private ProjectController createController(ProjectPersistenceService service) {
        return new ProjectController(service, new ProjectWorkspaceService(service));
    }

    @Test
    void getProjectReturns200WithDtoForValidId() {
        var project = new Project("proj-1", "Test", "/tmp/test", Instant.now(), Instant.now());
        var service = new ProjectPersistenceService() {
            @Override
            public Optional<Project> find(String id) {
                return Optional.of(project);
            }
        };

        var controller = createController(service);
        ResponseEntity<ProjectDto> response = controller.getProject("proj-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("proj-1", response.getBody().getId());
        assertEquals("Test", response.getBody().getName());
        assertEquals("/tmp/test", response.getBody().getPath());
    }

    @Test
    void getProjectReturns404ForUnknownId() {
        var service = new ProjectPersistenceService() {
            @Override
            public Optional<Project> find(String id) {
                return Optional.empty();
            }
        };

        var controller = createController(service);
        ResponseEntity<ProjectDto> response = controller.getProject("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void getWorkspaceStateReturnsPersistedState() {
        var service = new ProjectPersistenceService() {
            @Override
            public ProjectWorkspaceState getWorkspaceState() {
                return new ProjectWorkspaceState(List.of("proj-1", "proj-2"), "proj-2");
            }
        };

        var controller = createController(service);
        var response = controller.getWorkspaceState();

        assertEquals(List.of("proj-1", "proj-2"), response.getOpenProjectIds());
        assertEquals("proj-2", response.getActiveProjectId());
    }

    @Test
    void updateWorkspaceStateReturnsNormalizedState() {
        var service = new ProjectPersistenceService() {
            @Override
            public ProjectWorkspaceState updateWorkspaceState(ProjectWorkspaceState nextState) {
                return new ProjectWorkspaceState(nextState.getOpenProjectIds(), nextState.getActiveProjectId());
            }
        };

        var controller = createController(service);
        ResponseEntity<?> response = controller.updateWorkspaceState(new ProjectWorkspaceStateDto(List.of("proj-1"), "proj-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = (ProjectWorkspaceStateDto) response.getBody();
        assertNotNull(body);
        assertEquals(List.of("proj-1"), body.getOpenProjectIds());
        assertEquals("proj-1", body.getActiveProjectId());
    }

    @Test
    void updateWorkspaceStateRejectsMissingBody() {
        var controller = createController(new ProjectPersistenceService());

        ResponseEntity<?> response = controller.updateWorkspaceState(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void renameProjectReturnsUpdatedProject() {
        var renamedProject = new Project("proj-1", "Renamed", "/tmp/test", Instant.now(), Instant.now());
        var service = new ProjectPersistenceService() {
            @Override
            public Optional<Project> rename(String id, String name) {
                return Optional.of(renamedProject);
            }
        };

        var controller = createController(service);
        ResponseEntity<?> response = controller.renameProject("proj-1", new com.coderhino.web.dto.ProjectRenameRequest("Renamed"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = (ProjectDto) response.getBody();
        assertNotNull(body);
        assertEquals("Renamed", body.getName());
    }

    @Test
    void deleteWorktreeReturnsUpdatedProjectWhenDeletionSucceeds() {
        var service = new ProjectPersistenceService() {
            @Override
            public Optional<Project> find(String id) {
                return Optional.of(new Project(
                    "proj-1",
                    "Test",
                    "/tmp/test",
                    Instant.now(),
                    Instant.now(),
                    true,
                    List.of(
                        Worktree.defaultForProject("/tmp/test"),
                        Worktree.managed("feature-a", "/tmp/test-feature-a")
                    )
                ));
            }
        };
        var workspaceService = new ProjectWorkspaceService(service) {
            @Override
            public Optional<Project> deleteManagedWorktree(String projectId, String worktreeId) {
                return Optional.of(new Project(
                    "proj-1",
                    "Test",
                    "/tmp/test",
                    Instant.now(),
                    Instant.now(),
                    true,
                    List.of(Worktree.defaultForProject("/tmp/test"))
                ));
            }
        };

        var controller = new ProjectController(service, workspaceService);
        ResponseEntity<?> response = controller.deleteWorktree("proj-1", "wt-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = (ProjectDto) response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getWorktrees().size());
        assertEquals(Worktree.DEFAULT_WORKTREE_ID, body.getWorktrees().get(0).getId());
    }
}
