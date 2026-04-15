package com.coderhino.web.controller;

import com.coderhino.web.dto.ProjectCreateRequest;
import com.coderhino.web.dto.ProjectDto;
import com.coderhino.web.dto.ProjectListDto;
import com.coderhino.web.dto.ProjectRenameRequest;
import com.coderhino.web.dto.ProjectWorkspaceStateDto;
import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.WorktreeCreateRequest;
import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.service.ProjectWorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectPersistenceService projectService;
    private final ProjectWorkspaceService workspaceService;

    public ProjectController(ProjectPersistenceService projectService, ProjectWorkspaceService workspaceService) {
        this.projectService = projectService;
        this.workspaceService = workspaceService;
    }

    @GetMapping(value = "/workspace-state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectWorkspaceStateDto getWorkspaceState() {
        return ProjectWorkspaceStateDto.from(projectService.getWorkspaceState());
    }

    @PutMapping(value = "/workspace-state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateWorkspaceState(@RequestBody(required = false) ProjectWorkspaceStateDto request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Workspace state is required"));
        }
        return ResponseEntity.ok(ProjectWorkspaceStateDto.from(projectService.updateWorkspaceState(request.toModel())));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProjectDto> createOrOpen(@RequestBody ProjectCreateRequest request) {
        var result = projectService.createOrTouch(request.getPath());
        var dto = ProjectDto.from(result.getProject());
        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectListDto listProjects() {
        var dtos = projectService.listAllSorted().stream()
            .map(ProjectDto::from)
            .toList();
        return new ProjectListDto(dtos);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProjectDto> getProject(@PathVariable("id") String id) {
        return projectService.find(id)
            .map(project -> ResponseEntity.ok(ProjectDto.from(project)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> renameProject(@PathVariable("id") String id, @RequestBody(required = false) ProjectRenameRequest request) {
        if (request == null || request.getName() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Project name is required"));
        }
        try {
            return projectService.rename(id, request.getName())
                .<ResponseEntity<?>>map(project -> ResponseEntity.ok(ProjectDto.from(project)))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable("id") String id) {
        boolean deleted = projectService.delete(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/{id}/workspace/enable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProjectDto> enableWorkspace(@PathVariable("id") String id) {
        return workspaceService.enableWorkspace(id)
            .map(project -> ResponseEntity.ok(ProjectDto.from(project)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{id}/worktrees", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createWorktree(@PathVariable("id") String id, @RequestBody(required = false) WorktreeCreateRequest request) {
        if (request == null || request.getName() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Workspace name is required"));
        }
        try {
            return workspaceService.createManagedWorktree(id, request.getName())
                .<ResponseEntity<?>>map(project -> ResponseEntity.status(HttpStatus.CREATED).body(ProjectDto.from(project)))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping(value = "/{id}/worktrees/{worktreeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteWorktree(@PathVariable("id") String id, @PathVariable("worktreeId") String worktreeId) {
        try {
            return workspaceService.deleteManagedWorktree(id, worktreeId)
                .<ResponseEntity<?>>map(project -> ResponseEntity.ok(ProjectDto.from(project)))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }
}
