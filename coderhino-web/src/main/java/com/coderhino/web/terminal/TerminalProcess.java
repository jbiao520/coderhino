package com.coderhino.web.terminal;

import java.io.IOException;

public interface TerminalProcess {

    void start(TerminalListener listener) throws IOException;

    void write(String data) throws IOException;

    void resize(int cols, int rows) throws IOException;

    boolean isAlive();

    void close();

    interface TerminalListener {
        void onOutput(String chunk);

        void onExit(int exitCode);

        void onError(Throwable error);
    }
}
