package com.coderhino.services.summary;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BashCommandFileParserTest {

    private static final Path CWD = Path.of("/project");

    @Test
    void detectsRm() {
        var changes = BashCommandFileParser.parse("rm foo.txt", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.DELETED, changes.get(0).operation());
        assertEquals(CWD.resolve("foo.txt"), changes.get(0).file());
    }

    @Test
    void detectsRmWithFlags() {
        var changes = BashCommandFileParser.parse("rm -rf build/", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.DELETED, changes.get(0).operation());
    }

    @Test
    void detectsCp() {
        var changes = BashCommandFileParser.parse("cp src.txt dst.txt", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
        assertEquals(CWD.resolve("dst.txt"), changes.get(0).file());
    }

    @Test
    void detectsCpWithFlags() {
        var changes = BashCommandFileParser.parse("cp -r src/ dst/", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
    }

    @Test
    void detectsMv() {
        var changes = BashCommandFileParser.parse("mv old.txt new.txt", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
        assertEquals(CWD.resolve("new.txt"), changes.get(0).file());
    }

    @Test
    void detectsTouch() {
        var changes = BashCommandFileParser.parse("touch newfile.txt", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
    }

    @Test
    void detectsMkdir() {
        var changes = BashCommandFileParser.parse("mkdir newdir", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
    }

    @Test
    void detectsRedirect() {
        var changes = BashCommandFileParser.parse("echo hello > output.txt", CWD);
        assertTrue(changes.stream().anyMatch(c -> c.file().equals(CWD.resolve("output.txt")) && c.operation() == FileOperation.MODIFIED));
    }

    @Test
    void detectsAppendRedirect() {
        var changes = BashCommandFileParser.parse("echo hello >> output.txt", CWD);
        assertTrue(changes.stream().anyMatch(c -> c.file().equals(CWD.resolve("output.txt"))));
    }

    @Test
    void ignoresNonModifyingLs() {
        var changes = BashCommandFileParser.parse("ls -la", CWD);
        assertTrue(changes.isEmpty());
    }

    @Test
    void ignoresNonModifyingCat() {
        var changes = BashCommandFileParser.parse("cat file.txt", CWD);
        assertTrue(changes.isEmpty());
    }

    @Test
    void ignoresNonModifyingGrep() {
        var changes = BashCommandFileParser.parse("grep pattern file.txt", CWD);
        assertTrue(changes.isEmpty());
    }

    @Test
    void handlesAbsolutePath() {
        var changes = BashCommandFileParser.parse("touch /tmp/test.txt", CWD);
        assertEquals(1, changes.size());
        assertEquals(Path.of("/tmp/test.txt"), changes.get(0).file());
    }

    @Test
    void detectsGitCheckout() {
        var changes = BashCommandFileParser.parse("git checkout main", CWD);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.MODIFIED, changes.get(0).operation());
    }
}
