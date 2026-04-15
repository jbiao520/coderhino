package com.coderhino.commands.builtin;

import com.coderhino.commands.PromptBackedCommand;

import java.util.List;

public final class InitCommand implements PromptBackedCommand {
    private static final List<String> ALLOWED_TOOLS = List.of(
        "ask_user_question",
        "bash",
        "glob",
        "grep",
        "read_file",
        "write_file",
        "edit_file",
        "tool_search"
    );

    private static final String INIT_PROMPT = """
Set up Claude onboarding for this repository.

Your job is to run a repository-aware initialization workflow for the Java port of Code Rhino. Prefer the smallest correct setup that helps future Claude sessions avoid mistakes.

## Phase 1: Ask what to set up

Use `ask_user_question` to learn the desired setup scope before writing files.

- Ask which Claude guidance files to set up.
  Choices: `Project CLAUDE.md` | `Personal CLAUDE.local.md` | `Both project + personal`
  Project guidance is team-shared repository guidance.
  Personal guidance is private, user-specific context for this repository.

- Ask whether to also set up optional reusable artifacts.
  Choices: `Skills + hooks` | `Skills only` | `Hooks only` | `Neither, just CLAUDE.md`
  Only propose artifact types the user opted into.

## Phase 2: Inspect the repository before asking follow-up questions

Use repository evidence first. Read and inspect relevant files before asking anything else.

Survey for:
- Manifest/build files such as `pom.xml`, `build.gradle*`, `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`, `Makefile`
- `README*`, CI config, formatter/linter config, and repository-specific tooling docs
- Existing `CLAUDE.md`, `.claude/CLAUDE.md`, `CLAUDE.local.md`, `AGENTS.md`, `.cursor/rules`, `.cursorrules`, `.github/copilot-instructions.md`, `.windsurfrules`, `.clinerules`, `.mcp.json`
- Existing `.claude/skills`, `.opencode/skills`, and `.coderhino/hooks.json`

Infer when possible:
- Build, test, lint, and format commands
- Languages, frameworks, package managers, project layout
- Non-obvious workflow constraints or gotchas
- Existing Claude-specific setup that should be preserved or improved

Ask follow-up questions only for information the codebase cannot answer reliably.

## Phase 3: Fill the remaining gaps

Ask targeted follow-up questions only for unresolved setup details.

- If project guidance is requested, ask only about non-obvious team conventions, gotchas, or commands that were not inferable from the repository.
- If personal guidance is requested, ask about the user's role, familiarity, sandbox details, test accounts, and communication preferences.

## Phase 4: Synthesize a concise setup proposal

Before writing optional additions, present a short proposal that maps each suggestion to the correct artifact type and respects the user's chosen scope.

Supported artifact targets for the Java port:
- `CLAUDE.md` at repository root for team-shared guidance
- `CLAUDE.local.md` at repository root for personal guidance
- Markdown skills under `.claude/skills/<name>/SKILL.md` or `.opencode/skills/<name>/SKILL.md`
- Hook guidance or hook files aligned with `.coderhino/hooks.json`

Do not promise unsupported storage conventions. If an upstream TypeScript workflow would rely on unsupported behavior, fall back to one of the supported artifact targets above.

## Phase 5: Create or improve project CLAUDE.md

If project guidance was requested:
- Create a minimal `CLAUDE.md` when none exists.
- If `CLAUDE.md` already exists, treat it as input to improve rather than silently overwrite it.
- Every line must justify its existence by preventing Claude from making mistakes in this repository.
- Include only non-obvious commands, conventions, workflow quirks, architecture notes, or setup details that matter.
- Do not add generic coding advice or obvious language defaults.

Prefix any new project file with:

```md
# CLAUDE.md

This file provides guidance to Code Rhino when working with this repository.
```

## Phase 6: Create optional personal guidance, skills, and hooks only if requested

- `CLAUDE.local.md`: keep it short and personal. Include only user-specific details that materially improve responses.
- Skills: create markdown skills only when the user opted into skills and the workflow is reusable.
- Hooks: if the user opted into hooks, prefer guidance or configuration compatible with `.coderhino/hooks.json`.

If the user did not opt into a category, do not create it.

## Phase 7: Be accurate about outcomes

- Never claim a file was created if you only analyzed the repository or proposed changes.
- If you improve an existing file, say that you updated or refined it.
- Keep the output concise and factual.
""";

    @Override
    public String name() {
        return "init";
    }

    @Override
    public String description() {
        return "Initialize Claude in the current project";
    }

    @Override
    public String prompt(String args) {
        return INIT_PROMPT;
    }

    @Override
    public List<String> allowedTools() {
        return ALLOWED_TOOLS;
    }
}
