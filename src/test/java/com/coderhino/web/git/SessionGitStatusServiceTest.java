package com.coderhino.web.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionGitStatusServiceTest {

    @Test
    void getDiffReturnsPlainGitDiffForTrackedFile(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 2;\n");

        var service = new SessionGitStatusService();
        var diff = service.getDiff(tempDir, "src/App.tsx");

        assertEquals("tracked", diff.getKind());
        assertEquals("src/App.tsx", diff.getPath());
        assertTrue(diff.getDiff().contains("diff --git a/src/App.tsx b/src/App.tsx"));
        assertTrue(diff.getDiff().contains("-export const value = 1;"));
        assertTrue(diff.getDiff().contains("+export const value = 2;"));
    }

    @Test
    void getDiffAllowsEmptyDiffForTrackedFile(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        run(tempDir, "git", "rm", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "remove");
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "restore");

        var service = new SessionGitStatusService();
        var diff = service.getDiff(tempDir, "src/App.tsx");

        assertEquals("tracked", diff.getKind());
        assertEquals("src/App.tsx", diff.getPath());
        assertEquals("", diff.getDiff());
    }

    @Test
    void getDiffReturnsSyntheticDiffForUnversionedFile(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        writeFile(tempDir.resolve("notes/todo.md"), "todo\nsecond\n");

        var service = new SessionGitStatusService();
        var diff = service.getDiff(tempDir, "notes/todo.md");

        assertEquals("unversioned", diff.getKind());
        assertEquals("notes/todo.md", diff.getPath());
        assertTrue(diff.getDiff().contains("diff --git a/notes/todo.md b/notes/todo.md"));
        assertTrue(diff.getDiff().contains("new file mode 100644"));
        assertTrue(diff.getDiff().contains("--- /dev/null"));
        assertTrue(diff.getDiff().contains("+++ b/notes/todo.md"));
        assertTrue(diff.getDiff().contains("+todo"));
    }

    @Test
    void getDiffLimitsSyntheticDiffForUnversionedFileToDefaultContext(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        writeFile(tempDir.resolve("notes/todo.md"), "todo\nsecond\nthird\nfourth\n");

        var service = new SessionGitStatusService();
        var diff = service.getDiff(tempDir, "notes/todo.md");

        assertTrue(diff.getDiff().contains("+todo"));
        assertTrue(diff.getDiff().contains("+second"));
        assertTrue(diff.getDiff().contains("+third"));
        assertFalse(diff.getDiff().contains("+fourth"));
    }

    @Test
    void getDiffUsesDefaultContextSizeForTrackedFiles(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "line1\nline2\nline3\nline4\nline5\nline6\nline7\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        writeFile(tempDir.resolve("src/App.tsx"), "line1\nline2\nline3\nupdated\nline5\nline6\nline7\n");

        var service = new SessionGitStatusService();
        var diff = service.getDiff(tempDir, "src/App.tsx");

        assertTrue(diff.getDiff().contains("@@ -1,7 +1,7 @@") || diff.getDiff().contains("@@ -1,7 +1,7 @@"));
    }

    @Test
    void getDiffRejectsFileOutsideTrackedOrUnversionedChanges(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        var service = new SessionGitStatusService();

        try {
            service.getDiff(tempDir, "src/Missing.tsx");
        } catch (SessionGitStatusException e) {
            assertEquals("File is not a tracked or unversioned change in the session worktree.", e.getMessage());
            return;
        }

        throw new AssertionError("Expected SessionGitStatusException");
    }

    @Test
    void getStatusReturnsStructuredEntriesForTrackedAndUnversionedFiles(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");

        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 2;\n");
        writeFile(tempDir.resolve("notes/todo.md"), "todo\n");

        var service = new SessionGitStatusService();
        var status = service.getStatus(tempDir);

        assertEquals(1, status.getTrackedChanges().size());
        assertEquals("tracked", status.getTrackedChanges().get(0).getKind());
        assertEquals("src/App.tsx", status.getTrackedChanges().get(0).getPath());
        assertEquals("modified", status.getTrackedChanges().get(0).getStatus());
        assertEquals(1, status.getUnversionedFiles().size());
        assertEquals("unversioned", status.getUnversionedFiles().get(0).getKind());
        assertEquals("notes/todo.md", status.getUnversionedFiles().get(0).getPath());
        assertEquals(null, status.getUnversionedFiles().get(0).getStatus());
    }

    @Test
    void getDiffRejectsPathTraversalForUnversionedRequest(@TempDir Path tempDir) throws Exception {
        initRepo(tempDir);
        writeFile(tempDir.resolve("src/App.tsx"), "export const value = 1;\n");
        run(tempDir, "git", "add", "src/App.tsx");
        run(tempDir, "git", "commit", "-m", "initial");
        Files.writeString(tempDir.resolveSibling("outside.txt"), "secret", StandardCharsets.UTF_8);

        var service = new SessionGitStatusService();

        try {
            service.getDiff(tempDir, "../outside.txt");
        } catch (SessionGitStatusException e) {
            assertEquals("Invalid file path.", e.getMessage());
            return;
        }

        throw new AssertionError("Expected SessionGitStatusException");
    }

    private static void initRepo(Path root) throws Exception {
        run(root, "git", "init");
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String run(Path cwd, String... command) throws Exception {
        var processBuilder = new ProcessBuilder(command)
            .directory(cwd.toFile());
        processBuilder.environment().put("GIT_AUTHOR_NAME", "Test User");
        processBuilder.environment().put("GIT_AUTHOR_EMAIL", "test@example.com");
        processBuilder.environment().put("GIT_COMMITTER_NAME", "Test User");
        processBuilder.environment().put("GIT_COMMITTER_EMAIL", "test@example.com");
        var process = processBuilder.start();
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(stderr.isBlank() ? "Command failed with exit code " + exitCode : stderr);
        }
        return stdout;
    }
}
