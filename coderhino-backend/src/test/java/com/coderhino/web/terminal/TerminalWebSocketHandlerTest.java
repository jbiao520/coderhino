package com.coderhino.web.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerminalWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void afterConnectionEstablishedSendsReadyEventForOwnedTerminal() throws Exception {
        var service = mock(WebTerminalService.class);
        var terminal = new TerminalSession(
            "term-1",
            "ses-1",
            "proj-1",
            "default",
            "Terminal 1",
            Path.of("/tmp/project"),
            Instant.now(),
            new NoOpTerminalProcess()
        );
        when(service.findTerminal("ses-1", "term-1")).thenReturn(java.util.Optional.of(terminal));
        var session = mockSession("/ws/terminals/term-1?sessionId=ses-1", "ses-1");
        var handler = new TerminalWebSocketHandler(service, objectMapper);

        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(any(TextMessage.class));
        var message = objectMapper.readTree(capturedText(session));
        assertEquals("ready", message.path("type").asText());
        assertEquals("term-1", message.path("terminalId").asText());
    }

    @Test
    void afterConnectionEstablishedRejectsMissingSessionId() throws Exception {
        var service = mock(WebTerminalService.class);
        var session = mockSession("/ws/terminals/term-1", null);
        var handler = new TerminalWebSocketHandler(service, objectMapper);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing session id"));
        verify(service, never()).findTerminal(any(), any());
    }

    @Test
    void afterConnectionEstablishedRejectsUnknownTerminalWithReason() throws Exception {
        var service = mock(WebTerminalService.class);
        when(service.findTerminal("ses-1", "term-404")).thenReturn(java.util.Optional.empty());
        var session = mockSession("/ws/terminals/term-404?sessionId=ses-1", "ses-1");
        var handler = new TerminalWebSocketHandler(service, objectMapper);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE.withReason("Unknown terminal"));
    }

    @Test
    void handleTextMessageForwardsInputAndResizeToTerminal() throws Exception {
        var service = mock(WebTerminalService.class);
        var process = new RecordingTerminalProcess();
        var terminal = new TerminalSession(
            "term-1",
            "ses-1",
            "proj-1",
            "default",
            "Terminal 1",
            Path.of("/tmp/project"),
            Instant.now(),
            process
        );
        when(service.findTerminal("ses-1", "term-1")).thenReturn(java.util.Optional.of(terminal));
        var session = mockSession("/ws/terminals/term-1?sessionId=ses-1", "ses-1");
        var handler = new TerminalWebSocketHandler(service, objectMapper);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"input\",\"data\":\"pwd\\r\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"resize\",\"cols\":132,\"rows\":40}"));

        assertEquals("pwd\r", process.lastInput);
        assertEquals(132, process.lastCols);
        assertEquals(40, process.lastRows);
    }

    private WebSocketSession mockSession(String uri, String sessionId) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("socket-1");
        when(session.getUri()).thenReturn(URI.create("ws://localhost:8080" + uri));
        when(session.isOpen()).thenReturn(true);
        var attributes = new HashMap<String, Object>();
        if (sessionId != null) {
            attributes.put("sessionId", sessionId);
        }
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private String capturedText(WebSocketSession session) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }

    private static class NoOpTerminalProcess implements TerminalProcess {
        @Override
        public void start(TerminalListener listener) {
        }

        @Override
        public void write(String data) {
        }

        @Override
        public void resize(int cols, int rows) {
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingTerminalProcess extends NoOpTerminalProcess {
        private String lastInput;
        private int lastCols;
        private int lastRows;

        @Override
        public void write(String data) {
            lastInput = data;
        }

        @Override
        public void resize(int cols, int rows) {
            lastCols = cols;
            lastRows = rows;
        }
    }
}
