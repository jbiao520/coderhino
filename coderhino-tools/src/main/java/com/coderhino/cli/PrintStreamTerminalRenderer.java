package com.coderhino.cli;

import java.io.PrintStream;

public final class PrintStreamTerminalRenderer implements TerminalRenderer {
    private final PrintStream out;
    private final PrintStream err;

    public PrintStreamTerminalRenderer(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public void print(String text) {
        out.print(text);
    }

    @Override
    public void printLine(String text) {
        out.println(text);
    }

    @Override
    public void printError(String text) {
        err.println(text);
    }

    @Override
    public void printPrompt(String prompt) {
        out.print(prompt);
        out.flush();
    }

    @Override
    public void flush() {
        out.flush();
        err.flush();
    }
}
