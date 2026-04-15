package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

public final class MemoryCommand implements CommandDefinition {
    private static final String MEMORY_SUBDIR = ".coderhino/memories";

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public String description() {
        return "Manage Claude memory files";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        String cwd = state.cwd();
        Path memoryDir = resolveMemoryDir(cwd);

        String trimmed = args.trim();

        if (trimmed.isEmpty() || "list".equals(trimmed)) {
            listMemoryFiles(context, memoryDir);
        } else if ("open".equals(trimmed) || "edit".equals(trimmed)) {
            openOrEditMemoryFile(context, memoryDir, null);
        } else if (trimmed.startsWith("open ") || trimmed.startsWith("edit ")) {
            String filename = trimmed.startsWith("open ")
                ? trimmed.substring("open ".length()).trim()
                : trimmed.substring("edit ".length()).trim();
            openOrEditMemoryFile(context, memoryDir, filename);
        } else if (trimmed.startsWith("add ")) {
            String filename = trimmed.substring("add ".length()).trim();
            addMemoryFile(context, memoryDir, filename);
        } else if (trimmed.startsWith("rm ")) {
            String filename = trimmed.substring("rm ".length()).trim();
            removeMemoryFile(context, memoryDir, filename);
        } else {
            context.out().printf("Unknown memory command: %s%n", trimmed);
            context.out().printf("Usage: /memory [list|open|edit|add <filename>|rm <filename>]%n");
        }
    }

    public Path resolveMemoryDir(String cwd) {
        return Path.of(cwd).resolve(MEMORY_SUBDIR);
    }

    List<Path> listMemoryFiles(Path memoryDir) {
        if (!Files.exists(memoryDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(memoryDir)) {
            return paths
                .filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void listMemoryFiles(CommandContext context, Path memoryDir) {
        List<Path> files = listMemoryFiles(memoryDir);
        if (files.isEmpty()) {
            context.out().printf("No memory files found. Use /memory add <filename> to create one.%n");
        } else {
            context.out().println("Memory files:");
            for (Path file : files) {
                String filename = file.getFileName().toString();
                context.out().printf("  - %s%n", filename);
            }
        }
    }

    private void openOrEditMemoryFile(CommandContext context, Path memoryDir, String filename) {
        try {
            Files.createDirectories(memoryDir);
        } catch (IOException e) {
            context.err().printf("Failed to create memory directory: %s%n", e.getMessage());
            return;
        }

        if (filename == null || filename.isEmpty()) {
            List<Path> files = listMemoryFiles(memoryDir);
            if (files.isEmpty()) {
                context.out().printf("No memory files found. Use /memory add <filename> to create one.%n");
                return;
            }
            context.out().println("Available memory files:");
            for (Path file : files) {
                context.out().printf("  - %s%n", file.getFileName().toString());
            }
            context.out().println("Use /memory open <filename> to open a specific file.");
            return;
        }

        if (!filename.endsWith(".md")) {
            filename = filename + ".md";
        }
        Path targetFile = memoryDir.resolve(filename);

        if (!Files.exists(targetFile)) {
            try {
                Files.createFile(targetFile);
                context.out().printf("Created new memory file: %s%n", filename);
            } catch (IOException e) {
                context.err().printf("Failed to create memory file: %s%n", e.getMessage());
                return;
            }
        }

        openInEditor(context, targetFile);
    }

    private void addMemoryFile(CommandContext context, Path memoryDir, String filename) {
        if (!filename.endsWith(".md")) {
            filename = filename + ".md";
        }

        Path targetFile = memoryDir.resolve(filename);

        try {
            Files.createDirectories(memoryDir);
        } catch (IOException e) {
            context.err().printf("Failed to create memory directory: %s%n", e.getMessage());
            return;
        }

        if (Files.exists(targetFile)) {
            context.out().printf("Memory file already exists: %s%n", filename);
            return;
        }

        try {
            String template = "# " + filename.replace(".md", "") + "\n\n";
            Files.writeString(targetFile, template, StandardOpenOption.CREATE_NEW);
            context.out().printf("Created memory file: %s%n", filename);
        } catch (IOException e) {
            context.err().printf("Failed to create memory file: %s%n", e.getMessage());
        }
    }

    private void removeMemoryFile(CommandContext context, Path memoryDir, String filename) {
        if (!filename.endsWith(".md")) {
            filename = filename + ".md";
        }

        Path targetFile = memoryDir.resolve(filename);

        if (!Files.exists(targetFile)) {
            context.out().printf("Memory file not found: %s%n", filename);
            return;
        }

        try {
            Files.delete(targetFile);
            context.out().printf("Deleted memory file: %s%n", filename);
        } catch (IOException e) {
            context.err().printf("Failed to delete memory file: %s%n", e.getMessage());
        }
    }

    private void openInEditor(CommandContext context, Path file) {
        String editor = System.getenv("EDITOR");
        if (editor == null || editor.isEmpty()) {
            editor = System.getenv("VISUAL");
        }
        if (editor == null || editor.isEmpty()) {
            editor = "vi";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(editor, file.toAbsolutePath().toString());
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                context.err().printf("Editor exited with code: %d%n", exitCode);
            }
        } catch (IOException e) {
            context.err().printf("Failed to open editor: %s%n", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.err().printf("Editor interrupted%n");
        }
    }
}
