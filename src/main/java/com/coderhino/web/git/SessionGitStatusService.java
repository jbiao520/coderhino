package com.coderhino.web.git;

import com.coderhino.web.dto.SessionGitDiffDto;
import com.coderhino.web.dto.SessionGitFileContentCompareDto;
import com.coderhino.web.dto.SessionGitStatusDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SessionGitStatusService {

    private static final int DEFAULT_CONTEXT_LINES = 3;

    public SessionGitStatusDto getStatus(Path worktreePath) {
        var repositoryPath = requireGitRepository(worktreePath);

        try {
            var output = run(repositoryPath, "git", "status", "--short", "--untracked-files=all");
            return parse(output);
        } catch (IOException | InterruptedException e) {
            throw new SessionGitStatusException("Failed to load git status for the session worktree.", e);
        }
    }

    public SessionGitDiffDto getDiff(Path worktreePath, String filePath) {
        var repositoryPath = requireGitRepository(worktreePath);
        var normalizedPath = normalizeRequestedPath(filePath);
        var trackedFile = isTrackedFile(repositoryPath, normalizedPath);
        var unversionedFile = !trackedFile && isUnversionedFile(repositoryPath, normalizedPath);

        if (!trackedFile && !unversionedFile) {
            throw new SessionGitStatusException("File is not a tracked or unversioned change in the session worktree.");
        }

        try {
            if (trackedFile) {
                var diffOutput = run(
                    repositoryPath,
                    "git",
                    "diff",
                    "--no-ext-diff",
                    "--unified=" + DEFAULT_CONTEXT_LINES,
                    "HEAD",
                    "--",
                    normalizedPath
                );
                return new SessionGitDiffDto(
                    SessionGitStatusDto.KIND_TRACKED,
                    normalizedPath,
                    diffOutput
                );
            }

            var diffResult = buildUnversionedDiff(repositoryPath, normalizedPath);
            return new SessionGitDiffDto(
                SessionGitStatusDto.KIND_UNVERSIONED,
                normalizedPath,
                diffResult
            );
        } catch (IOException | InterruptedException e) {
            throw new SessionGitStatusException("Failed to load git diff for the selected file.", e);
        }
    }

    public SessionGitFileContentCompareDto getFileContentCompare(Path worktreePath, String filePath) {
        var repositoryPath = requireGitRepository(worktreePath);
        var normalizedPath = normalizeRequestedPath(filePath);

        if (!isTrackedFile(repositoryPath, normalizedPath)) {
            throw new SessionGitStatusException("File is not a tracked file in the session worktree.");
        }

        String previousContent = null;
        String currentContent = null;

        try {
            previousContent = getFileContentAtHead(repositoryPath, normalizedPath);
        } catch (IOException e) {
        }

        try {
            var filePathResolved = resolvePathInWorktree(repositoryPath, normalizedPath);
            if (Files.isRegularFile(filePathResolved)) {
                currentContent = Files.readString(filePathResolved, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
        }

        return new SessionGitFileContentCompareDto(normalizedPath, previousContent, currentContent);
    }

    private String getFileContentAtHead(Path repositoryPath, String filePath) throws IOException {
        try {
            return run(repositoryPath, "git", "show", "HEAD:" + filePath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Path requireGitRepository(Path worktreePath) {
        if (worktreePath == null) {
            throw new SessionGitStatusException("Session worktree is unavailable.");
        }

        if (!isGitRepository(worktreePath)) {
            throw new SessionGitStatusException("Resolved worktree is not a git repository.");
        }

        return worktreePath;
    }

    private String normalizeRequestedPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new SessionGitStatusException("A file path is required.");
        }
        if (filePath.indexOf('\0') >= 0) {
            throw new SessionGitStatusException("Invalid file path.");
        }
        return filePath.trim();
    }

    private boolean isTrackedFile(Path worktreePath, String filePath) {
        try {
            run(worktreePath, "git", "ls-files", "--error-unmatch", "--", filePath);
            return true;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean isUnversionedFile(Path worktreePath, String filePath) {
        try {
            var path = resolvePathInWorktree(worktreePath, filePath);
            if (!path.startsWith(worktreePath.normalize())) {
                return false;
            }
            if (!Files.isRegularFile(path)) {
                return false;
            }
            return parse(run(worktreePath, "git", "status", "--short", "--untracked-files=all", "--", filePath)).getUnversionedFiles().stream()
                .anyMatch(entry -> filePath.equals(entry.getPath()));
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean isGitRepository(Path cwd) {
        try {
            return "true".equalsIgnoreCase(run(cwd, "git", "rev-parse", "--is-inside-work-tree").trim());
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private String run(Path cwd, String... command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
            .directory(cwd.toFile())
            .start();
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(stderr.isBlank() ? "Command failed with exit code " + exitCode : stderr);
        }
        return stdout;
    }

    private SessionGitStatusDto parse(String statusOutput) {
        var trackedChanges = new ArrayList<SessionGitStatusDto.GitEntry>();
        var unversionedFiles = new ArrayList<SessionGitStatusDto.GitEntry>();
        for (String rawLine : statusOutput.split("\\R")) {
            var line = rawLine.stripTrailing();
            if (line.isBlank() || line.length() < 3) {
                continue;
            }
            var code = line.substring(0, 2);
            var path = normalizePath(code, line.substring(3).trim());
            if (path.isBlank()) {
                continue;
            }
            if ("??".equals(code)) {
                unversionedFiles.add(SessionGitStatusDto.GitEntry.unversioned(path));
                continue;
            }
            trackedChanges.add(SessionGitStatusDto.GitEntry.tracked(path, normalizeStatus(code)));
        }
        return new SessionGitStatusDto(trackedChanges, unversionedFiles);
    }

    private String buildUnversionedDiff(Path worktreePath, String filePath) throws IOException {
        var path = resolvePathInWorktree(worktreePath, filePath);
        var content = Files.readString(path, StandardCharsets.UTF_8);
        var lines = content.split("\\n", -1);
        var hasTrailingNewline = content.endsWith("\n");
        var renderedLineCount = hasTrailingNewline ? Math.max(lines.length - 1, 0) : lines.length;
        var visibleLineCount = Math.min(renderedLineCount, DEFAULT_CONTEXT_LINES);
        var builder = new StringBuilder();
        builder.append("diff --git a/").append(filePath).append(" b/").append(filePath).append('\n');
        builder.append("new file mode 100644\n");
        builder.append("--- /dev/null\n");
        builder.append("+++ b/").append(filePath).append('\n');
        builder.append("@@ -0,0 +1,").append(visibleLineCount).append(" @@\n");
        if (!content.isEmpty()) {
            for (int i = 0; i < visibleLineCount; i++) {
                var line = lines[i];
                builder.append('+').append(line).append('\n');
            }
            if (!hasTrailingNewline && visibleLineCount == renderedLineCount) {
                builder.append("\\ No newline at end of file\n");
            }
        }
        return builder.toString();
    }

    private Path resolvePathInWorktree(Path worktreePath, String filePath) {
        var path = worktreePath.resolve(filePath).normalize();
        if (!path.startsWith(worktreePath.normalize())) {
            throw new SessionGitStatusException("Invalid file path.");
        }
        return path;
    }

    private String normalizePath(String code, String path) {
        if ((code.indexOf('R') >= 0 || code.indexOf('C') >= 0) && path.contains(" -> ")) {
            return path.substring(path.indexOf(" -> ") + 4).trim();
        }
        return path;
    }

    private String normalizeStatus(String code) {
        var parts = new ArrayList<String>();
        addStatusPart(parts, code.charAt(0), true);
        addStatusPart(parts, code.charAt(1), false);
        if (parts.isEmpty()) {
            return "changed";
        }
        return String.join(", ", parts);
    }

    private void addStatusPart(List<String> parts, char code, boolean staged) {
        String label = switch (code) {
            case 'M' -> staged ? "staged modified" : "modified";
            case 'A' -> staged ? "staged added" : "added";
            case 'D' -> staged ? "staged deleted" : "deleted";
            case 'R' -> staged ? "staged renamed" : "renamed";
            case 'C' -> staged ? "staged copied" : "copied";
            case 'U' -> staged ? "staged unmerged" : "unmerged";
            case 'T' -> staged ? "staged type changed" : "type changed";
            default -> null;
        };
        if (label != null) {
            parts.add(label);
        }
    }
}
