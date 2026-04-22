package com.coderhino.services.summary;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BashCommandFileParser {

    private static final Pattern RM_PATTERN = Pattern.compile("(?:rm\\s+(?:-[rfRdilnv]+\\s+)*)(\\S+)");
    private static final Pattern CP_PATTERN = Pattern.compile("(?:cp\\s+(?:-[rfLlns]+\\s+)*)(?:\\S+\\s+)(\\S+)");
    private static final Pattern MV_PATTERN = Pattern.compile("(?:mv\\s+(?:-[fiLn]+\\s+)*)(?:\\S+\\s+)(\\S+)");
    private static final Pattern TOUCH_PATTERN = Pattern.compile("(?:touch\\s+(?:-[acmdr\\s]+)*)(\\S+)");
    private static final Pattern MKDIR_PATTERN = Pattern.compile("(?:mkdir\\s+(?:-[pv]+\\s+)*)(\\S+)");
    private static final Pattern REDIRECT_PATTERN = Pattern.compile(">?>(\\s+)?([\"']?)([^\"'\\s;|&]+)\\2");
    private static final Pattern GIT_CHECKOUT_PATTERN = Pattern.compile("git\\s+checkout\\s+(?:-[bB]\\s+)?(\\S+)");

    private BashCommandFileParser() {
    }

    public static List<FileChange> parse(String command, Path cwd) {
        var changes = new ArrayList<FileChange>();
        var now = Instant.now();

        parseMatches(command, RM_PATTERN, cwd, FileOperation.DELETED, "bash", changes, now);
        parseMatches(command, CP_PATTERN, cwd, FileOperation.CREATED, "bash", changes, now);
        parseMatches(command, MV_PATTERN, cwd, FileOperation.CREATED, "bash", changes, now);
        parseMatches(command, TOUCH_PATTERN, cwd, FileOperation.CREATED, "bash", changes, now);
        parseMatches(command, MKDIR_PATTERN, cwd, FileOperation.CREATED, "bash", changes, now);
        parseRedirectMatches(command, cwd, "bash", changes, now);
        parseMatches(command, GIT_CHECKOUT_PATTERN, cwd, FileOperation.MODIFIED, "bash", changes, now);

        return changes;
    }

    private static void parseMatches(String command, Pattern pattern, Path cwd, FileOperation operation, String toolName, List<FileChange> changes, Instant timestamp) {
        Matcher matcher = pattern.matcher(command);
        while (matcher.find()) {
            var rawPath = matcher.group(1);
            if (rawPath != null && !rawPath.startsWith("-")) {
                changes.add(new FileChange(resolvePath(rawPath, cwd), operation, timestamp, toolName));
            }
        }
    }

    private static void parseRedirectMatches(String command, Path cwd, String toolName, List<FileChange> changes, Instant timestamp) {
        Matcher matcher = REDIRECT_PATTERN.matcher(command);
        while (matcher.find()) {
            var rawPath = matcher.group(3);
            if (rawPath != null && !rawPath.isEmpty()) {
                changes.add(new FileChange(resolvePath(rawPath, cwd), FileOperation.MODIFIED, timestamp, toolName));
            }
        }
    }

    private static Path resolvePath(String rawPath, Path cwd) {
        var path = Path.of(rawPath);
        if (path.isAbsolute()) return path.normalize();
        return cwd.resolve(path).normalize();
    }
}
