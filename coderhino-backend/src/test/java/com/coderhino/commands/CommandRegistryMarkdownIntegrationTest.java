package com.coderhino.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryMarkdownIntegrationTest {

    @Test
    void registryIncludesUserInvocableMarkdownCommands(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve(".opencode/command"));
        Files.writeString(tempDir.resolve(".opencode/command/opsx-apply.md"), """
            ---
            description: Apply change
            disable-model-invocation: false
            ---

            Implement the change.
            """);

        var registry = CommandRegistry.createDefault(tempDir);
        var command = registry.find("opsx-apply").orElseThrow();

        assertInstanceOf(MarkdownCommandDefinition.class, command);
        assertTrue(command.userInvocable());
        assertTrue(command.includeInModelContext());
    }

    @Test
    void builtInCommandNameRemainsReserved(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve(".claude/commands"));
        Files.writeString(tempDir.resolve(".claude/commands/help.md"), "Custom help\n");

        var registry = CommandRegistry.createDefault(tempDir);
        var command = registry.find("help").orElseThrow();

        assertFalse(command instanceof MarkdownCommandDefinition);
        assertEquals("help", command.name());
    }
}
