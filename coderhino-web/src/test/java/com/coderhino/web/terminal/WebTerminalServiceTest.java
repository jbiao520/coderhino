package com.coderhino.web.terminal;

import com.coderhino.state.SessionStore;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.project.Project;
import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.project.Worktree;
import com.coderhino.web.session.SessionPersistenceService;
import com.coderhino.web.session.WebSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebTerminalServiceTest {

    @Test
    void createTerminalUsesSessionWorktreeRoot(@TempDir Path tempDir) throws Exception {
        var projectRoot = Files.createDirectories(tempDir.resolve("repo"));
        var managedRoot = Files.createDirectories(tempDir.resolve("worktree-a"));
        var registry = createRegistry(tempDir, projectRoot, managedRoot);
        var session = registry.createSessionForProject("project-1", "managed-1").orElseThrow();
        var service = new WebTerminalService(
            registry,
            createProjectService(projectRoot, managedRoot),
            new TerminalSessionRegistry(),
            cwd -> new FakeTerminalProcess(cwd)
        );

        var terminal = service.createTerminal(session.getSessionId(), null, null);

        assertEquals(managedRoot.toAbsolutePath().normalize(), terminal.getCwd());
        assertEquals("managed-1", terminal.getWorktreeId());
        assertEquals("Terminal 1", terminal.getLabel());
        assertTrue(terminal.isAlive());
    }

    @Test
    void createTerminalRejectsUnknownProjectScopedSession(@TempDir Path tempDir) {
        var projectRoot = tempDir.resolve("repo");
        var managedRoot = tempDir.resolve("worktree-a");
        var registry = createRegistry(tempDir, projectRoot, managedRoot);
        var service = new WebTerminalService(
            registry,
            createProjectService(projectRoot, managedRoot),
            new TerminalSessionRegistry(),
            cwd -> new FakeTerminalProcess(cwd)
        );

        var error = assertThrows(IllegalArgumentException.class, () -> service.createTerminal("missing", null, null));

        assertEquals("Unknown session", error.getMessage());
    }

    @Test
    void closeTerminalRemovesAndStopsProcess(@TempDir Path tempDir) throws Exception {
        var projectRoot = Files.createDirectories(tempDir.resolve("repo"));
        var managedRoot = Files.createDirectories(tempDir.resolve("worktree-a"));
        var registry = createRegistry(tempDir, projectRoot, managedRoot);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var terminalRegistry = new TerminalSessionRegistry();
        var service = new WebTerminalService(
            registry,
            createProjectService(projectRoot, managedRoot),
            terminalRegistry,
            cwd -> new FakeTerminalProcess(cwd)
        );
        var terminal = service.createTerminal(session.getSessionId(), null, null);

        assertTrue(service.closeTerminal(session.getSessionId(), terminal.getTerminalId()));
        assertFalse(service.findTerminal(session.getSessionId(), terminal.getTerminalId()).isPresent());
        assertFalse(terminal.isAlive());
    }

    @Test
    void listTerminalsPreservesErrorAndExitDetails(@TempDir Path tempDir) throws Exception {
        var projectRoot = Files.createDirectories(tempDir.resolve("repo"));
        var managedRoot = Files.createDirectories(tempDir.resolve("worktree-a"));
        var registry = createRegistry(tempDir, projectRoot, managedRoot);
        var session = registry.createSessionForProject("project-1", "managed-1").orElseThrow();
        var process = new FakeTerminalProcess(managedRoot);
        var service = new WebTerminalService(
            registry,
            createProjectService(projectRoot, managedRoot),
            new TerminalSessionRegistry(),
            cwd -> process
        );

        var terminal = service.createTerminal(session.getSessionId(), null, null);
        process.emitError(new IOException("PTY startup failed"));

        var errored = service.listTerminals(session.getSessionId()).get(0);
        assertEquals(TerminalStatus.ERROR, errored.getStatus());
        assertEquals("PTY startup failed", errored.getMessage());

        process.emitExit(23);

        var exited = service.listTerminals(session.getSessionId()).get(0);
        assertEquals(TerminalStatus.EXITED, exited.getStatus());
        assertEquals(23, exited.getExitCode());
        assertEquals("PTY startup failed", exited.getMessage());
        assertEquals(terminal.getTerminalId(), exited.getTerminalId());
    }

    private static WebSessionRegistry createRegistry(Path tempDir, Path projectRoot, Path managedRoot) {
        return new WebSessionRegistry(
            new SessionPersistenceService(tempDir.resolve("metadata")),
            new SessionStore(new ObjectMapper(), tempDir.resolve("transcripts")),
            new SessionEventBus(new ObjectMapper()),
            createProjectService(projectRoot, managedRoot)
        );
    }

    private static ProjectPersistenceService createProjectService(Path projectRoot, Path managedRoot) {
        return new ProjectPersistenceService(projectRoot.resolveSibling("projects.json")) {
            @Override
            public Optional<Project> find(String id) {
                return Optional.of(new Project(
                    id,
                    "Project",
                    projectRoot.toString(),
                    Instant.now(),
                    Instant.now(),
                    true,
                    List.of(
                        Worktree.defaultForProject(projectRoot.toString()),
                        new Worktree("managed-1", "feature-a", managedRoot.toString(), false, true, Instant.now())
                    )
                ));
            }

            @Override
            public Optional<Worktree> findWorktree(String projectId, String worktreeId) {
                return find(projectId).flatMap(project -> project.getWorktrees().stream()
                    .filter(worktree -> worktreeId == null || worktreeId.isBlank()
                        ? worktree.isDefaultWorktree()
                        : worktreeId.equals(worktree.getId()))
                    .findFirst());
            }
        };
    }

    private static final class FakeTerminalProcess implements TerminalProcess {
        private final Path cwd;
        private TerminalListener listener;
        private boolean alive = true;

        private FakeTerminalProcess(Path cwd) {
            this.cwd = cwd;
        }

        @Override
        public void start(TerminalListener listener) {
            this.listener = listener;
            listener.onOutput("ready:" + cwd);
        }

        @Override
        public void write(String data) throws IOException {
        }

        @Override
        public void resize(int cols, int rows) throws IOException {
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void close() {
            alive = false;
        }

        private void emitError(Throwable error) {
            listener.onError(error);
        }

        private void emitExit(int exitCode) {
            listener.onExit(exitCode);
        }
    }
}
