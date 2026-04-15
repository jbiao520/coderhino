package com.coderhino.cli;

import java.io.PrintStream;

public final class ConsoleRenderer implements TerminalRenderer {

    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD = "\033[1m";
    private static final String ANSI_UNDERLINE = "\033[4m";
    private static final char PROGRESS_FILL = '#';
    private static final char PROGRESS_EMPTY = '.';
    private static final int PROGRESS_WIDTH = 10;

    private final PrintStream out;
    private final PrintStream err;

    public ConsoleRenderer(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static ConsoleRenderer system() {
        return new ConsoleRenderer(System.out, System.err);
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
    }

    @Override
    public void clearLine() {
        out.print("\r\033[K");
        out.flush();
    }

    @Override
    public void printProgress(int pct, String label) {
        int clamped = Math.max(0, Math.min(100, pct));
        int filled = clamped * PROGRESS_WIDTH / 100;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < PROGRESS_WIDTH; i++) {
            bar.append(i < filled ? PROGRESS_FILL : PROGRESS_EMPTY);
        }
        bar.append("] ").append(clamped).append("% ").append(label);
        out.println(bar);
    }

    @Override
    public void beginBlock(String label) {
        out.println(ANSI_BOLD + ANSI_UNDERLINE + "=== " + label + " ===" + ANSI_RESET);
    }

    @Override
    public void endBlock() {
        out.println();
    }

    @Override
    public void printSectionHeader(String title) {
        out.println("--- " + title + " ---");
    }

    @Override
    public void printKeyValue(String key, String value) {
        out.println(key + ": " + value);
    }

    @Override
    public void showSpinner(String message) {
        out.print("\r⠋ " + message);
        out.flush();
    }

    @Override
    public void stopSpinner() {
        clearLine();
    }
}
