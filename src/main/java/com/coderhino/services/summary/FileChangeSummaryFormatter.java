package com.coderhino.services.summary;

public final class FileChangeSummaryFormatter {

    private FileChangeSummaryFormatter() {
    }

    public static String format(FileChangeSummary summary) {
        if (summary.totalChanges() == 0) {
            return "Session Summary — No file changes detected";
        }

        var sb = new StringBuilder();
        sb.append("Session Summary — ").append(summary.totalChanges()).append(" file").append(summary.totalChanges() == 1 ? "" : "s").append(" changed");

        if (!summary.created().isEmpty()) {
            sb.append("\n  Created (").append(summary.created().size()).append("): ");
            sb.append(formatPaths(summary.created()));
        }
        if (!summary.modified().isEmpty()) {
            sb.append("\n  Modified (").append(summary.modified().size()).append("): ");
            sb.append(formatPaths(summary.modified()));
        }
        if (!summary.deleted().isEmpty()) {
            sb.append("\n  Deleted (").append(summary.deleted().size()).append("): ");
            sb.append(formatPaths(summary.deleted()));
        }

        return sb.toString();
    }

    private static String formatPaths(java.util.List<java.nio.file.Path> paths) {
        var sb = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paths.get(i));
        }
        return sb.toString();
    }
}
