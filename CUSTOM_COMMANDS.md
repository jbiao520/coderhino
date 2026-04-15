# Custom Commands And Skills

The Java runtime now discovers markdown-backed custom definitions from the same shared catalog used by REPL slash commands, the web command API, and the `skill` tool.

## Supported Layouts

- User commands: `~/.claude/commands/**/*.md`
- User skills: `~/.claude/skills/<name>/SKILL.md`
- User OpenCode commands: `~/.opencode/command/**/*.md`
- User OpenCode skills: `~/.opencode/skills/<name>/SKILL.md`
- Project commands: `<cwd-up-to-git-root>/.claude/commands/**/*.md`
- Project skills: `<cwd-up-to-git-root>/.claude/skills/<name>/SKILL.md`
- Project OpenCode commands: `<cwd-up-to-git-root>/.opencode/command/**/*.md`
- Project OpenCode skills: `<cwd-up-to-git-root>/.opencode/skills/<name>/SKILL.md`

Project directories nearer to the current working directory override higher-level project directories. User-level definitions override project-level definitions. Built-in command names remain reserved.

## Supported Frontmatter

The current implementation supports only this frontmatter subset:

```markdown
---
name: Friendly display name
description: Short description
allowed-tools: [bash, read]
user-invocable: true
when_to_use: Use when this task matches
disable-model-invocation: false
---
```

Unsupported frontmatter is ignored. Malformed supported values cause that definition to be skipped.

## Command Files

Command names come from the relative file path under the command root.

- `~/.claude/commands/review.md` -> `/review`
- `~/.claude/commands/opsx/apply.md` -> `/opsx:apply`
- `~/.opencode/command/opsx-apply.md` -> `/opsx-apply`

Slash-command arguments are exposed in prompt bodies through `$ARGUMENTS`, `$ARGUMENTS[0]`, `$0`, and related indexed placeholders. If no placeholder is present, the runtime appends `ARGUMENTS: ...` to the prompt.

## Skill Directories

Skills are directory-based and must use `SKILL.md`.

- `~/.claude/skills/debug/SKILL.md` -> skill `debug`
- `project/.opencode/skills/openspec-apply-change/SKILL.md` -> skill `openspec-apply-change`

Skills with `user-invocable: false` stay out of slash-command listings and direct slash-command execution, but can still be available to the model unless `disable-model-invocation: true` is set.

## Legacy JSON Skills

The old `.claudecode-skills/*.json` store is now legacy-only and no longer drives normal skill discovery. Runtime lookup prefers markdown-backed definitions from `.claude` and `.opencode`.
