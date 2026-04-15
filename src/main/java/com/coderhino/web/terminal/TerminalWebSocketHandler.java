package com.coderhino.web.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final WebTerminalService webTerminalService;
    private final ObjectMapper objectMapper;
    private final Map<String, TerminalSession.TerminalEventListener> listenersBySocket = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(WebTerminalService webTerminalService, ObjectMapper objectMapper) {
        this.webTerminalService = webTerminalService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var terminalId = resolveTerminalId(session);
        if (terminalId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Missing terminal id"));
            return;
        }
        var ownerSessionId = resolveOwnerSessionId(session);
        if (ownerSessionId.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing session id"));
            return;
        }
        var terminal = webTerminalService.findTerminal(ownerSessionId, terminalId).orElse(null);
        if (terminal == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unknown terminal"));
            return;
        }

        var listener = new TerminalSession.TerminalEventListener() {
            @Override
            public void onOutput(String chunk) {
                send(session, Map.of("type", "output", "data", chunk));
            }

            @Override
            public void onExit(int exitCode) {
                send(session, Map.of("type", "exit", "exitCode", exitCode));
            }

            @Override
            public void onError(String message) {
                send(session, Map.of("type", "error", "message", message));
            }
        };
        listenersBySocket.put(session.getId(), listener);
        terminal.addListener(listener);
        send(session, Map.of("type", "ready", "terminalId", terminalId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var terminalId = resolveTerminalId(session);
        var terminal = terminalId == null
            ? null
            : webTerminalService.findTerminal(resolveOwnerSessionId(session), terminalId).orElse(null);
        if (terminal == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unknown terminal"));
            return;
        }

        JsonNode root = objectMapper.readTree(message.getPayload());
        var type = root.path("type").asText("");
        if ("input".equals(type)) {
            terminal.write(root.path("data").asText(""));
            return;
        }
        if ("resize".equals(type)) {
            terminal.resize(root.path("cols").asInt(120), root.path("rows").asInt(36));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeListener(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        removeListener(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void removeListener(WebSocketSession socketSession) {
        var listener = listenersBySocket.remove(socketSession.getId());
        var terminalId = resolveTerminalId(socketSession);
        if (listener == null || terminalId == null) {
            return;
        }
        webTerminalService.findTerminal(resolveOwnerSessionId(socketSession), terminalId)
            .ifPresent(terminal -> terminal.removeListener(listener));
    }

    private String resolveTerminalId(WebSocketSession session) {
        var uri = session.getUri();
        if (uri == null) {
            return null;
        }
        var path = uri.getPath();
        if (path == null || !path.contains("/ws/terminals/")) {
            return null;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String resolveOwnerSessionId(WebSocketSession session) {
        var attributes = session.getAttributes();
        var sessionId = attributes.get("sessionId");
        return sessionId instanceof String value ? value : "";
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (IOException ignored) {
            }
        }
    }
}
