package com.coderhino.web.controller;

import com.coderhino.config.credentials.CredentialsPersistenceService;
import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.SessionDto;
import com.coderhino.web.dto.SessionContextDto;
import com.coderhino.web.dto.SessionGitDiffDto;
import com.coderhino.web.dto.SessionGitFileContentCompareDto;
import com.coderhino.web.dto.SessionGitStatusDto;
import com.coderhino.web.dto.SessionListDto;
import com.coderhino.web.git.SessionGitStatusException;
import com.coderhino.web.git.SessionGitStatusService;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.coderhino.web.terminal.WebTerminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final WebSessionRegistry registry;
    private final CredentialsPersistenceService credentialsService;
    private final WebTerminalService terminalService;
    private final SessionGitStatusService sessionGitStatusService;

    public SessionController(WebSessionRegistry registry) {
        this(registry, new CredentialsPersistenceService(), null, new SessionGitStatusService());
    }

    public SessionController(WebSessionRegistry registry, CredentialsPersistenceService credentialsService) {
        this(registry, credentialsService, null, new SessionGitStatusService());
    }

    public SessionController(WebSessionRegistry registry,
                             CredentialsPersistenceService credentialsService,
                             SessionGitStatusService sessionGitStatusService) {
        this(registry, credentialsService, null, sessionGitStatusService);
    }

    @Autowired
    public SessionController(WebSessionRegistry registry,
                             CredentialsPersistenceService credentialsService,
                             WebTerminalService terminalService,
                             SessionGitStatusService sessionGitStatusService) {
        this.registry = registry;
        this.credentialsService = credentialsService;
        this.terminalService = terminalService;
        this.sessionGitStatusService = sessionGitStatusService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionDto> createSession(@RequestBody(required = false) Map<String, String> body) {
        String projectId = body != null ? body.get("projectId") : null;
        String worktreeId = body != null ? body.get("worktreeId") : null;
        if (projectId == null || projectId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var sessionOpt = registry.createSessionForProject(projectId, worktreeId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var session = sessionOpt.get();
        var worktree = registry.getWorktreeForSession(session.getSessionId()).orElse(null);
        var credentials = credentialsService.load();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SessionDto.fromWithoutMessages(session, projectId, worktree, credentials));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionListDto> listSessions(@RequestParam(value = "projectId", required = false) String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var sessions = registry.listByProject(projectId);
        var credentials = credentialsService.load();
        var dtos = sessions.stream()
            .sorted(Comparator.comparing(WebSession::getCreatedAt))
            .map(s -> {
                var pid = registry.getProjectIdForSession(s.getSessionId()).orElse(null);
                var worktree = registry.getWorktreeForSession(s.getSessionId()).orElse(null);
                return SessionDto.fromWithoutMessages(s, pid, worktree, credentials);
            })
            .toList();
        return ResponseEntity.ok(new SessionListDto(dtos));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionDto> getSession(@PathVariable("id") String id) {
        return registry.find(id)
            .map(session -> {
                var pid = registry.getProjectIdForSession(id).orElse(null);
                var worktree = registry.getWorktreeForSession(id).orElse(null);
                return ResponseEntity.ok(SessionDto.from(session, pid, worktree, credentialsService.load()));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/context", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionContextDto> getSessionContext(@PathVariable("id") String id) {
        return registry.find(id)
            .map(session -> ResponseEntity.ok(SessionContextDto.from(session)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/git", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSessionGitStatus(@PathVariable("id") String id) {
        var session = registry.find(id);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var worktree = registry.getWorktreeForSession(id).orElse(null);
        try {
            SessionGitStatusDto gitStatus = sessionGitStatusService.getStatus(worktree != null ? java.nio.file.Path.of(worktree.getPath()) : null);
            return ResponseEntity.ok(gitStatus);
        } catch (SessionGitStatusException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/git/diff", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSessionGitDiff(@PathVariable("id") String id,
                                               @RequestParam("path") String path) {
        var session = registry.find(id);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var worktree = registry.getWorktreeForSession(id).orElse(null);
        try {
            SessionGitDiffDto gitDiff = sessionGitStatusService.getDiff(
                worktree != null ? java.nio.file.Path.of(worktree.getPath()) : null,
                path
            );
            return ResponseEntity.ok(gitDiff);
        } catch (SessionGitStatusException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/git/file-content", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSessionGitFileContent(@PathVariable("id") String id,
                                                      @RequestParam("path") String path,
                                                      @RequestParam(value = "compare", required = false, defaultValue = "false") boolean compare) {
        var session = registry.find(id);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var worktree = registry.getWorktreeForSession(id).orElse(null);
        try {
            SessionGitFileContentCompareDto fileContent = sessionGitStatusService.getFileContentCompare(
                worktree != null ? java.nio.file.Path.of(worktree.getPath()) : null,
                path
            );
            return ResponseEntity.ok(fileContent);
        } catch (SessionGitStatusException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
    }

    @PatchMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionDto> patchSession(@PathVariable("id") String id, @RequestBody(required = false) Map<String, String> body) {
        if (body == null || body.get("name") == null) {
            return ResponseEntity.badRequest().build();
        }
        var updated = registry.updateName(id, body.get("name"));
        if (updated.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var session = updated.get();
        var pid = registry.getProjectIdForSession(id).orElse(null);
        var worktree = registry.getWorktreeForSession(id).orElse(null);
        return ResponseEntity.ok(SessionDto.fromWithoutMessages(session, pid, worktree, credentialsService.load()));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable("id") String id) {
        if (terminalService != null) {
            terminalService.closeAllForSession(id);
        }
        if (!registry.remove(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
