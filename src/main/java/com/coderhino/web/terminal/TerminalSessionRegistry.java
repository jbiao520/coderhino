package com.coderhino.web.terminal;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TerminalSessionRegistry {

    private final ConcurrentHashMap<String, TerminalSession> terminals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> terminalIdsBySession = new ConcurrentHashMap<>();

    public void register(TerminalSession terminalSession) {
        terminals.put(terminalSession.getTerminalId(), terminalSession);
        terminalIdsBySession
            .computeIfAbsent(terminalSession.getSessionId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(terminalSession.getTerminalId());
    }

    public java.util.Optional<TerminalSession> find(String terminalId) {
        return java.util.Optional.ofNullable(terminals.get(terminalId));
    }

    public List<TerminalSession> listBySession(String sessionId) {
        var ids = terminalIdsBySession.getOrDefault(sessionId, Set.of());
        var result = new ArrayList<TerminalSession>(ids.size());
        for (var id : ids) {
            var terminal = terminals.get(id);
            if (terminal != null) {
                result.add(terminal);
            }
        }
        result.sort(Comparator.comparing(TerminalSession::getCreatedAt));
        return result;
    }

    public boolean closeAndRemove(String sessionId, String terminalId) {
        var terminal = terminals.get(terminalId);
        if (terminal == null || !terminal.getSessionId().equals(sessionId)) {
            return false;
        }
        terminals.remove(terminalId);
        var ids = terminalIdsBySession.get(sessionId);
        if (ids != null) {
            ids.remove(terminalId);
            if (ids.isEmpty()) {
                terminalIdsBySession.remove(sessionId, ids);
            }
        }
        terminal.close();
        return true;
    }

    public void closeAllForSession(String sessionId) {
        for (var terminal : listBySession(sessionId)) {
            closeAndRemove(sessionId, terminal.getTerminalId());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent ignored) {
        for (var terminal : new ArrayList<>(terminals.values())) {
            closeAndRemove(terminal.getSessionId(), terminal.getTerminalId());
        }
    }
}
