package com.coderhino.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class MarkdownPromptLoader {
    public List<MarkdownPromptDefinition> load(Path cwd) {
        Path normalizedCwd = normalize(cwd == null ? Path.of("") : cwd);
        Map<String, MarkdownPromptDefinition> definitions = new LinkedHashMap<>();

        for (var source : sourcesInAscendingPrecedence(normalizedCwd)) {
            for (var definition : loadFromSource(source)) {
                definitions.put(definition.name(), definition);
            }
        }

        return List.copyOf(definitions.values());
    }

    private List<SourceSpec> sourcesInAscendingPrecedence(Path cwd) {
        List<SourceSpec> sources = new ArrayList<>();
        List<Path> projectDirs = projectSearchRoots(cwd);
        Path home = normalize(Path.of(System.getProperty("user.home")));

        addProjectSources(sources, projectDirs, ".opencode/command", MarkdownPromptDefinition.DefinitionType.COMMAND, MarkdownPromptDefinition.Namespace.OPENCODE);
        addProjectSources(sources, projectDirs, ".opencode/skills", MarkdownPromptDefinition.DefinitionType.SKILL, MarkdownPromptDefinition.Namespace.OPENCODE);
        addProjectSources(sources, projectDirs, ".claude/commands", MarkdownPromptDefinition.DefinitionType.COMMAND, MarkdownPromptDefinition.Namespace.CLAUDE);
        addProjectSources(sources, projectDirs, ".claude/skills", MarkdownPromptDefinition.DefinitionType.SKILL, MarkdownPromptDefinition.Namespace.CLAUDE);

        sources.add(new SourceSpec(home.resolve(".opencode/command"), MarkdownPromptDefinition.Scope.USER, MarkdownPromptDefinition.Namespace.OPENCODE, MarkdownPromptDefinition.DefinitionType.COMMAND));
        sources.add(new SourceSpec(home.resolve(".opencode/skills"), MarkdownPromptDefinition.Scope.USER, MarkdownPromptDefinition.Namespace.OPENCODE, MarkdownPromptDefinition.DefinitionType.SKILL));
        sources.add(new SourceSpec(home.resolve(".claude/commands"), MarkdownPromptDefinition.Scope.USER, MarkdownPromptDefinition.Namespace.CLAUDE, MarkdownPromptDefinition.DefinitionType.COMMAND));
        sources.add(new SourceSpec(home.resolve(".claude/skills"), MarkdownPromptDefinition.Scope.USER, MarkdownPromptDefinition.Namespace.CLAUDE, MarkdownPromptDefinition.DefinitionType.SKILL));
        return sources;
    }

    private void addProjectSources(
        List<SourceSpec> sources,
        List<Path> projectRoots,
        String relativeSubdir,
        MarkdownPromptDefinition.DefinitionType definitionType,
        MarkdownPromptDefinition.Namespace namespace
    ) {
        for (Path root : projectRoots) {
            sources.add(new SourceSpec(root.resolve(relativeSubdir), MarkdownPromptDefinition.Scope.PROJECT, namespace, definitionType));
        }
    }

    private List<Path> projectSearchRoots(Path cwd) {
        ArrayDeque<Path> stack = new ArrayDeque<>();
        Path home = normalize(Path.of(System.getProperty("user.home")));
        Path boundary = findProjectBoundary(cwd);
        Path current = cwd;

        while (current != null && !normalize(current).equals(home)) {
            stack.push(current);
            if (normalize(current).equals(boundary)) {
                break;
            }
            current = current.getParent();
        }
        return List.copyOf(stack);
    }

    private Path findProjectBoundary(Path cwd) {
        Path home = normalize(Path.of(System.getProperty("user.home")));
        Path current = cwd;
        Path last = cwd;
        while (current != null && !normalize(current).equals(home)) {
            last = current;
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return last;
    }

    private List<MarkdownPromptDefinition> loadFromSource(SourceSpec source) {
        if (!Files.isDirectory(source.directory())) {
            return List.of();
        }
        return source.definitionType() == MarkdownPromptDefinition.DefinitionType.COMMAND
            ? loadCommands(source)
            : loadSkills(source);
    }

    private List<MarkdownPromptDefinition> loadCommands(SourceSpec source) {
        try (Stream<Path> stream = Files.walk(source.directory())) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".md"))
                .sorted(Comparator.comparing(path -> source.directory().relativize(path).toString()))
                .map(path -> parseDefinition(path, source, resolveCommandName(source.directory(), path), path.getParent()))
                .flatMap(Optional::stream)
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private List<MarkdownPromptDefinition> loadSkills(SourceSpec source) {
        try (Stream<Path> stream = Files.walk(source.directory())) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                .sorted(Comparator.comparing(path -> source.directory().relativize(path.getParent()).toString()))
                .map(path -> parseDefinition(path, source, resolveSkillName(source.directory(), path), path.getParent()))
                .flatMap(Optional::stream)
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Optional<MarkdownPromptDefinition> parseDefinition(Path path, SourceSpec source, String name, Path baseDirectory) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            ParsedMarkdown parsed = parseMarkdown(raw);
            if (parsed == null) {
                return Optional.empty();
            }

            String normalizedName = normalizeName(name);
            if (normalizedName.isBlank()) {
                return Optional.empty();
            }

            String description = parsed.frontmatter().containsKey("description")
                ? String.valueOf(parsed.frontmatter().get("description")).trim()
                : extractDescription(parsed.body(), source.definitionType() == MarkdownPromptDefinition.DefinitionType.SKILL ? "Skill" : "Custom command");
            if (description.isBlank()) {
                description = source.definitionType() == MarkdownPromptDefinition.DefinitionType.SKILL ? "Skill" : "Custom command";
            }

            List<String> allowedTools = parseAllowedTools(parsed.frontmatter().get("allowed-tools"));
            Boolean userInvocable = parseBoolean(parsed.frontmatter().get("user-invocable"), true);
            Boolean disableModelInvocation = parseBoolean(parsed.frontmatter().get("disable-model-invocation"), false);
            if (userInvocable == null || disableModelInvocation == null) {
                return Optional.empty();
            }

            return Optional.of(new MarkdownPromptDefinition(
                normalizedName,
                stringValue(parsed.frontmatter().get("name")),
                description,
                parsed.body().strip(),
                allowedTools,
                stringValue(parsed.frontmatter().get("when_to_use")),
                userInvocable,
                disableModelInvocation,
                source.definitionType(),
                source.scope(),
                source.namespace(),
                path,
                baseDirectory
            ));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private ParsedMarkdown parseMarkdown(String raw) {
        if (!raw.startsWith("---" + System.lineSeparator()) && !raw.startsWith("---\n")) {
            return new ParsedMarkdown(Map.of(), raw);
        }

        int frontmatterEnd = raw.indexOf(System.lineSeparator() + "---", 4);
        if (frontmatterEnd < 0) {
            frontmatterEnd = raw.indexOf("\n---", 4);
        }
        if (frontmatterEnd < 0) {
            return null;
        }

        int closingLineEnd = raw.indexOf('\n', frontmatterEnd + 4);
        String frontmatterBlock = raw.substring(4, frontmatterEnd);
        String body = closingLineEnd >= 0 ? raw.substring(closingLineEnd + 1) : "";
        Map<String, Object> frontmatter = parseFrontmatterBlock(frontmatterBlock);
        return frontmatter == null ? null : new ParsedMarkdown(frontmatter, body);
    }

    private Map<String, Object> parseFrontmatterBlock(String block) {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        String activeListKey = null;

        for (String rawLine : block.split("\\r?\\n")) {
            String line = rawLine.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("  ") || line.startsWith("\t")) {
                if (activeListKey != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("- ")) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    List<String> values = (List<String>) frontmatter.get(activeListKey);
                    values.add(unquote(trimmed.substring(2).trim()));
                }
                continue;
            }

            activeListKey = null;
            int separator = line.indexOf(':');
            if (separator <= 0) {
                return null;
            }

            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!isSupportedFrontmatterKey(key)) {
                continue;
            }

            if (value.isEmpty()) {
                if ("allowed-tools".equals(key)) {
                    List<String> tools = new ArrayList<>();
                    frontmatter.put(key, tools);
                    activeListKey = key;
                } else {
                    frontmatter.put(key, "");
                }
                continue;
            }

            if ("allowed-tools".equals(key)) {
                frontmatter.put(key, parseAllowedToolsValue(value));
                continue;
            }

            frontmatter.put(key, unquote(value));
        }

        return frontmatter;
    }

    private boolean isSupportedFrontmatterKey(String key) {
        return switch (key) {
            case "name", "description", "allowed-tools", "user-invocable", "when_to_use", "disable-model-invocation" -> true;
            default -> false;
        };
    }

    private List<String> parseAllowedTools(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(tool -> !tool.isBlank())
                .toList();
        }
        return parseAllowedToolsValue(String.valueOf(value));
    }

    private List<String> parseAllowedToolsValue(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            String[] tokens = inner.split(",");
            List<String> tools = new ArrayList<>();
            for (String token : tokens) {
                String tool = unquote(token.trim());
                if (!tool.isBlank()) {
                    tools.add(tool);
                }
            }
            return List.copyOf(tools);
        }
        return Stream.of(trimmed.split(","))
            .map(String::trim)
            .map(MarkdownPromptLoader::unquote)
            .filter(tool -> !tool.isBlank())
            .toList();
    }

    private static Boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true" -> true;
            case "false" -> false;
            default -> null;
        };
    }

    private static String extractDescription(String body, String fallback) {
        for (String line : body.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.replaceFirst("^#+\\s*", "").trim();
            }
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
            }
        }
        return fallback;
    }

    private String resolveCommandName(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        String withoutExtension = relative.endsWith(".md") ? relative.substring(0, relative.length() - 3) : relative;
        return withoutExtension.replace('/', ':');
    }

    private String resolveSkillName(Path root, Path file) {
        String relative = root.relativize(file.getParent()).toString().replace('\\', '/');
        return relative.replace('/', ':');
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private record SourceSpec(
        Path directory,
        MarkdownPromptDefinition.Scope scope,
        MarkdownPromptDefinition.Namespace namespace,
        MarkdownPromptDefinition.DefinitionType definitionType
    ) {
    }

    private record ParsedMarkdown(Map<String, Object> frontmatter, String body) {
    }
}
