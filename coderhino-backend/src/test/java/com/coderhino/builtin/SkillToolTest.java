package com.coderhino.builtin;

import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.PromptBackedCommand;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.builtin.SkillTool;
import com.coderhino.types.PermissionMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillToolTest {

    @Test
    void validateRejectsCommandsExcludedFromModelContext() {
        var tool = new SkillTool();
        var result = tool.validate(new SkillTool.Input("read", "hello"), toolContext());

        assertFalse(result.allowed());
    }

    @Test
    void validateAllowsNormalSlashCommand() {
        var tool = new SkillTool();
        var result = tool.validate(new SkillTool.Input("help", ""), toolContext());

        assertTrue(result.allowed());
    }

    @Test
    void executeReturnsMarkdownPromptAndAllowedToolsForCustomSkill() throws Exception {
        var tool = new SkillTool();
        var output = tool.execute(new SkillTool.Input("triage", "build failed"), toolContextWithCustomSkill());

        assertTrue(output instanceof SkillTool.Output.InlineResult);
        var inline = (SkillTool.Output.InlineResult) output;
        assertTrue(inline.success());
        assertTrue(inline.description().contains("Diagnose the issue."));
        assertTrue(inline.description().contains("ARGUMENTS: build failed"));
        assertTrue(inline.allowedTools().contains("bash"));
    }

    @Test
    void executeReturnsPromptAndAllowedToolsForBuiltInPromptCommand() throws Exception {
        var tool = new SkillTool();
        var output = tool.execute(new SkillTool.Input("init", "project"), toolContextWithPromptBackedCommand());

        var inline = assertInstanceOf(SkillTool.Output.InlineResult.class, output);
        assertTrue(inline.success());
        assertTrue(inline.description().contains("Inspect repository first for: project"));
        assertTrue(inline.allowedTools().contains("bash"));
        assertTrue(inline.allowedTools().contains("ask_user_question"));
    }

    private static ToolContext toolContext() {
        var state = new BootstrapState(new AppState(
            false,
            "MiniMax-M2.5",
            Path.of("").toAbsolutePath().normalize().toString(),
            false,
            false,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of()),
            List.of()
        ));
        return new ToolContext(state, PermissionMode.BYPASS);
    }

    private static ToolContext toolContextWithCustomSkill() {
        var state = new BootstrapState(new AppState(
            false,
            "MiniMax-M2.5",
            Path.of("").toAbsolutePath().normalize().toString(),
            false,
            false,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of()),
            List.of()
        ));
        var registry = new CommandRegistry(List.of(
            new com.coderhino.commands.MarkdownCommandDefinition(
                new com.coderhino.commands.MarkdownPromptDefinition(
                    "triage",
                    null,
                    "Diagnose failures",
                    "Diagnose the issue.",
                    List.of("bash", "read"),
                    null,
                    false,
                    false,
                    com.coderhino.commands.MarkdownPromptDefinition.DefinitionType.SKILL,
                    com.coderhino.commands.MarkdownPromptDefinition.Scope.PROJECT,
                    com.coderhino.commands.MarkdownPromptDefinition.Namespace.CLAUDE,
                    Path.of("/tmp/SKILL.md"),
                    Path.of("/tmp")
                )
            )
        ));
        return new ToolContext(state, PermissionMode.BYPASS, null, null, registry);
    }

    private static ToolContext toolContextWithPromptBackedCommand() {
        var state = new BootstrapState(new AppState(
            false,
            "MiniMax-M2.5",
            Path.of("").toAbsolutePath().normalize().toString(),
            false,
            false,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of()),
            List.of()
        ));
        var registry = new CommandRegistry(List.of(
            new PromptBackedSkill("init", "Initialize Claude", "Inspect repository first for: $ARGUMENTS", List.of("bash", "ask_user_question"))
        ));
        return new ToolContext(state, PermissionMode.BYPASS, null, null, registry);
    }

    private record PromptBackedSkill(
        String name,
        String description,
        String promptTemplate,
        List<String> allowedTools
    ) implements PromptBackedCommand {
        @Override
        public String prompt(String args) {
            return promptTemplate.replace("$ARGUMENTS", args == null ? "" : args);
        }
    }
}
