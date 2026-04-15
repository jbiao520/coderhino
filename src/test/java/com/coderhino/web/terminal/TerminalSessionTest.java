package com.coderhino.web.terminal;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalSessionTest {

    @Test
    void addListenerReplaysBufferedStartupOutputAndExitState() throws Exception {
        var process = new StubTerminalProcess();
        var session = new TerminalSession(
            "term-1",
            "ses-1",
            "proj-1",
            "default",
            "Terminal 1",
            Path.of("/tmp/project"),
            Instant.now(),
            process
        );
        session.start();

        process.emitOutput("Last login: Sat Apr 11 17:54:22 on ttys020\r\n");
        process.emitOutput("jianguo@Mac ~ % ");
        process.emitExit(0);

        var replayedOutput = new ArrayList<String>();
        var exitCodes = new ArrayList<Integer>();
        session.addListener(new TerminalSession.TerminalEventListener() {
            @Override
            public void onOutput(String chunk) {
                replayedOutput.add(chunk);
            }

            @Override
            public void onExit(int exitCode) {
                exitCodes.add(exitCode);
            }

            @Override
            public void onError(String message) {
            }
        });

        assertEquals(List.of(
            "Last login: Sat Apr 11 17:54:22 on ttys020\r\njianguo@Mac ~ % "
        ), replayedOutput);
        assertEquals(List.of(0), exitCodes);
    }

    private static final class StubTerminalProcess implements TerminalProcess {
        private TerminalListener listener;

        @Override
        public void start(TerminalListener listener) {
            this.listener = listener;
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

        private void emitOutput(String chunk) {
            listener.onOutput(chunk);
        }

        private void emitExit(int exitCode) {
            listener.onExit(exitCode);
        }
    }
}
