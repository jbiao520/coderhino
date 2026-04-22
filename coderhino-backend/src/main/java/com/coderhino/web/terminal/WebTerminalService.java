package com.coderhino.web.terminal;

import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.session.WebSessionRegistry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebTerminalService {

    private static final Pattern TERMINAL_LABEL_PATTERN = Pattern.compile("^Terminal\\s+(\\d+)$");

    private final WebSessionRegistry webSessionRegistry;
    private final ProjectPersistenceService projectPersistenceService;
    private final TerminalSessionRegistry terminalSessionRegistry;
    private final TerminalProcessFactory terminalProcessFactory;

    public WebTerminalService(
        WebSessionRegistry webSessionRegistry,
        ProjectPersistenceService projectPersistenceService,
        TerminalSessionRegistry terminalSessionRegistry,
        TerminalProcessFactory terminalProcessFactory
    ) {
        this.webSessionRegistry = webSessionRegistry;
        this.projectPersistenceService = projectPersistenceService;
        this.terminalSessionRegistry = terminalSessionRegistry;
        this.terminalProcessFactory = terminalProcessFactory;
    }

    public List<TerminalSession> listTerminals(String sessionId) {
        return terminalSessionRegistry.listBySession(sessionId);
    }

    public Optional<TerminalSession> findTerminal(String sessionId, String terminalId) {
        return terminalSessionRegistry.find(terminalId)
            .filter(terminal -> terminal.getSessionId().equals(sessionId));
    }

    public TerminalSession createTerminal(String sessionId, String label, String requestedWorktreeId) throws IOException {
        var webSession = webSessionRegistry.find(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown session"));
        var projectId = webSessionRegistry.getProjectIdForSession(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Terminals require a project-scoped session"));

        var project = projectPersistenceService.find(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown project"));

        var effectiveWorktreeId = requestedWorktreeId != null && !requestedWorktreeId.isBlank()
            ? requestedWorktreeId
            : webSessionRegistry.getWorktreeIdForSession(sessionId).orElse(null);

        var worktree = projectPersistenceService.findWorktree(projectId, effectiveWorktreeId)
            .orElseThrow(() -> new IllegalArgumentException("Requested worktree is outside the active project workspace"));

        var workspaceRoot = Path.of(worktree.getPath()).toAbsolutePath().normalize();
        var projectRoot = Path.of(project.getPath()).toAbsolutePath().normalize();
        if (!workspaceRoot.equals(projectRoot) && project.getWorktrees().stream().noneMatch(candidate -> workspaceRoot.equals(Path.of(candidate.getPath()).toAbsolutePath().normalize()))) {
            throw new IllegalArgumentException("Requested worktree is outside the active project workspace");
        }

        var terminalProcess = terminalProcessFactory.create(workspaceRoot);
        var terminal = new TerminalSession(
            UUID.randomUUID().toString(),
            sessionId,
            projectId,
            worktree.getId(),
            resolveLabel(sessionId, label),
            workspaceRoot,
            Instant.now(),
            terminalProcess
        );
        terminalSessionRegistry.register(terminal);
        terminal.start();
        return terminal;
    }

    public boolean closeTerminal(String sessionId, String terminalId) {
        return terminalSessionRegistry.closeAndRemove(sessionId, terminalId);
    }

    public void closeAllForSession(String sessionId) {
        terminalSessionRegistry.closeAllForSession(sessionId);
    }

    private String resolveLabel(String sessionId, String requestedLabel) {
        if (requestedLabel != null && !requestedLabel.isBlank()) {
            return requestedLabel.trim();
        }
        int max = 0;
        for (var terminal : terminalSessionRegistry.listBySession(sessionId)) {
            Matcher matcher = TERMINAL_LABEL_PATTERN.matcher(terminal.getLabel());
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return "Terminal " + (max + 1);
    }
}
