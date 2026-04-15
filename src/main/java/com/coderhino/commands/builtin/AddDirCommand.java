package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AddDirCommand implements CommandDefinition {
    private static final List<String> addedDirs = new CopyOnWriteArrayList<>();

    public static void clearStore() {
        addedDirs.clear();
    }

    public static List<String> addedDirs() {
        return List.copyOf(addedDirs);
    }

    @Override
    public String name() {
        return "add-dir";
    }

    @Override
    public String description() {
        return "Add a directory to the workspace context";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();

        if (args == null || args.isBlank()) {
            if (addedDirs.isEmpty()) {
                renderer.printLine("No extra directories added to workspace.");
            } else {
                renderer.printLine("Added directories (" + addedDirs.size() + "):");
                for (var dir : addedDirs) {
                    renderer.printLine("  " + dir);
                }
            }
            renderer.printLine("Usage: /add-dir <path>");
            return;
        }

        var path = Path.of(args.trim()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            renderer.printLine("Directory does not exist: " + path);
            return;
        }
        if (!Files.isDirectory(path)) {
            renderer.printLine("Path is not a directory: " + path);
            return;
        }

        if (addedDirs.contains(path.toString())) {
            renderer.printLine("Directory already in workspace: " + path);
            return;
        }

        addedDirs.add(path.toString());

        try (var stream = Files.list(path)) {
            var fileCount = stream.count();
            renderer.printLine("Added directory to workspace: " + path);
            renderer.printLine("  Files in directory: " + fileCount);
        } catch (Exception e) {
            renderer.printLine("Added directory to workspace: " + path);
            renderer.printLine("  (could not count files: " + e.getMessage() + ")");
        }
    }
}
