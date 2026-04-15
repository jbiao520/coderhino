package com.coderhino.cli;

/**
 * Terminal output abstraction. Implementations route output to console,
 * test buffers, or future TUI frameworks without coupling to PrintStream.
 *
 * <p>Streaming and status methods are provided as {@code default} methods
 * so that existing implementors are not broken.  Production implementations
 * should override them for richer behaviour (e.g. ANSI cursor movement,
 * progress spinners).
 */
public interface TerminalRenderer {

    void print(String text);

    void printLine(String text);

    void printError(String text);

    void printPrompt(String prompt);

    void flush();

    // ── Streaming support ────────────────────────────────────────────

    /**
     * Append a single streaming chunk to the ongoing output.
     * The default implementation delegates to {@link #print(String)}.
     *
     * @param chunk a fragment of assistant output
     */
    default void printChunk(String chunk) {
        print(chunk);
    }

    /**
     * Display a transient status line (e.g. tool execution progress).
     * The default implementation delegates to {@link #printLine(String)}.
     *
     * @param status a human-readable status message
     */
    default void printStatus(String status) {
        printLine(status);
    }

    /**
     * Render potentially long output, truncating to at most {@code maxLines}.
     * If the text exceeds the limit the first {@code maxLines} lines are shown
     * followed by an ellipsis marker indicating the number of omitted lines.
     *
     * @param text     the full output text
     * @param maxLines maximum number of content lines to display
     */
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

    // ── Terminal manipulation ────────────────────────────────────────

    /**
     * Clear the current line (ANSI escape: carriage return + clear-to-EOL).
     * No-op by default; production implementations should override.
     */
    default void clearLine() { /* no-op default */ }

    // ── Progress bar ────────────────────────────────────────────────

    /**
     * Render a progress bar with percentage and label.
     *
     * @param pct   completion percentage (0-100)
     * @param label human-readable label shown after the bar
     */
    default void printProgress(int pct, String label) {
        printLine("[" + pct + "%] " + label);
    }

    // ── Structural grouping ─────────────────────────────────────────

    /**
     * Begin a labelled block (visual delimiter).
     *
     * @param label block title
     */
    default void beginBlock(String label) {
        printLine("=== " + label + " ===");
    }

    /**
     * End the current block (visual delimiter).
     */
    default void endBlock() {
        printLine("");
    }

    /**
     * Print a section header within a block.
     *
     * @param title section title
     */
    default void printSectionHeader(String title) {
        printLine("--- " + title + " ---");
    }

    /**
     * Print a key-value pair in {@code "key: value"} format.
     *
     * @param key   the key
     * @param value the value
     */
    default void printKeyValue(String key, String value) {
        printLine(key + ": " + value);
    }

    // ── Spinner ─────────────────────────────────────────────────────

    /**
     * Show an animated spinner with the given message.
     * Default implementation prints a static placeholder.
     *
     * @param message the status message to display alongside the spinner
     */
    default void showSpinner(String message) {
        printLine("[...] " + message);
    }

    /**
     * Stop the active spinner, clearing its output.
     * Default implementation is a no-op.
     */
    default void stopSpinner() { /* no-op default */ }
}
