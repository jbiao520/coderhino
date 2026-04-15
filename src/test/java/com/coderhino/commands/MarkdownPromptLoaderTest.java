package com.coderhino.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownPromptLoaderTest {

    @Test
    void loadsCommandsAndSkillsFromProjectDirectories(@TempDir Path tempDir) throws Exception {
        withUserHome(tempDir.resolve("home"), () -> {
            var cwd = tempDir.resolve("workspace").resolve("project");
            Files.createDirectories(cwd.resolve(".claude/commands/review"));
            Files.createDirectories(cwd.resolve(".claude/skills/triage"));

            Files.writeString(cwd.resolve(".claude/commands/review/checklist.md"), """
                ---
                description: Review checklist
                user-invocable: true
                ---

                Inspect the change carefully.
                """);
            Files.writeString(cwd.resolve(".claude/skills/triage/SKILL.md"), """
                ---
                description: Triage failures
                user-invocable: false
                when_to_use: Use when a failure needs diagnosis.
                ---

                Investigate the failure and report causes.
                """);

            var definitions = new MarkdownPromptLoader().load(cwd);

            assertEquals(2, definitions.size());
            var command = definitions.stream().filter(def -> def.name().equals("review:checklist")).findFirst().orElseThrow();
            assertEquals(MarkdownPromptDefinition.DefinitionType.COMMAND, command.definitionType());
            assertTrue(command.userInvocable());

            var skill = definitions.stream().filter(def -> def.name().equals("triage")).findFirst().orElseThrow();
            assertEquals(MarkdownPromptDefinition.DefinitionType.SKILL, skill.definitionType());
            assertFalse(skill.userInvocable());
            assertEquals("Use when a failure needs diagnosis.", skill.whenToUse());
        });
    }

    @Test
    void nearerProjectDirectoryOverridesHigherLevelProjectDirectory(@TempDir Path tempDir) throws Exception {
        withUserHome(tempDir.resolve("home"), () -> {
            var repo = tempDir.resolve("repo");
            var nested = repo.resolve("app").resolve("feature");
            Files.createDirectories(nested);
            Files.createDirectories(repo.resolve(".git"));
            Files.createDirectories(repo.resolve(".claude/commands"));
            Files.createDirectories(nested.resolve(".claude/commands"));

            Files.writeString(repo.resolve(".claude/commands/deploy.md"), "Top level command\n");
            Files.writeString(nested.resolve(".claude/commands/deploy.md"), "Nested command\n");

            var definitions = new MarkdownPromptLoader().load(nested);
            var deploy = definitions.stream().filter(def -> def.name().equals("deploy")).findFirst().orElseThrow();

            assertEquals("Nested command", deploy.description());
            assertEquals(nested.resolve(".claude/commands/deploy.md"), deploy.sourcePath());
        });
    }

    @Test
    void skipsMalformedFrontmatterAndContinuesLoading(@TempDir Path tempDir) throws Exception {
        withUserHome(tempDir.resolve("home"), () -> {
            var cwd = tempDir.resolve("workspace");
            Files.createDirectories(cwd.resolve(".opencode/command"));
            Files.writeString(cwd.resolve(".opencode/command/good.md"), """
                ---
                description: Good command
                allowed-tools: [bash, read]
                ---

                Run the good command.
                """);
            Files.writeString(cwd.resolve(".opencode/command/bad.md"), """
                ---
                user-invocable: maybe
                ---

                Broken command.
                """);

            var definitions = new MarkdownPromptLoader().load(cwd);

            assertEquals(1, definitions.size());
            assertEquals(List.of("bash", "read"), definitions.get(0).allowedTools());
            assertEquals("good", definitions.get(0).name());
        });
    }

    private static void withUserHome(Path userHome, ThrowingRunnable action) throws Exception {
        Files.createDirectories(userHome);
        var previous = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
