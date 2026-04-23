package com.coderhino.cli;

public interface TerminalRenderer {
    void print(String text);

    void printLine(String text);

    void printError(String text);

    void printPrompt(String prompt);

    void flush();

    default void printChunk(String chunk) {
        print(chunk);
    }

    default void printStatus(String status) {
        printLine(status);
    }

    default void renderLongOutput(String text, int maxLines) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] allLines = text.split("\\R", -1);
        if (allLines.length <= maxLines) {
            for (String line : allLines) {
                printLine(line);
            }
        } else {
            for (int i = 0; i < maxLines; i++) {
                printLine(allLines[i]);
            }
            int omitted = allLines.length - maxLines;
            printLine("... [" + omitted + " more lines]");
        }
    }

    default void clearLine() {
    }

    default void printProgress(int pct, String label) {
        printLine("[" + pct + "%] " + label);
    }

    default void beginBlock(String label) {
        printLine("=== " + label + " ===");
    }

    default void endBlock() {
        printLine("");
    }

    default void printSectionHeader(String title) {
        printLine("--- " + title + " ---");
    }

    default void printKeyValue(String key, String value) {
        printLine(key + ": " + value);
    }

    default void showSpinner(String message) {
        printLine("[...] " + message);
    }

    default void stopSpinner() {
    }
}
