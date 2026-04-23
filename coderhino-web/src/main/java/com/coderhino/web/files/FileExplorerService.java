package com.coderhino.web.files;

import com.coderhino.web.dto.DirectoryListing;
import com.coderhino.web.dto.FileContent;
import com.coderhino.web.dto.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class FileExplorerService {

    private static final Logger log = LoggerFactory.getLogger(FileExplorerService.class);
    private static final long MAX_FILE_SIZE_BYTES = 1048576L;

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
        ".class", ".jar", ".png", ".jpg", ".jpeg", ".gif", ".pdf",
        ".zip", ".tar", ".gz", ".exe", ".dll", ".so", ".o", ".pyc",
        ".woff", ".woff2", ".ttf", ".eot", ".ico", ".bmp", ".svg",
        ".mp3", ".mp4", ".avi", ".mov", ".wav", ".flac", ".webp",
        ".7z", ".rar", ".bz2", ".xz", ".dmg", ".iso", ".npy"
    );

    private static final Set<String> EXCLUDED_NAMES = Set.of(
        ".git", ".svn", ".hg", ".bzr", ".idea", ".vscode",
        "node_modules", "target", "build", "dist", "__pycache__",
        ".gradle", ".cache", ".next", ".nuxt"
    );

    public DirectoryListing listDirectory(Path projectRoot, String relativePath) throws IOException {
        Path resolved = sandbox(projectRoot, relativePath);

        if (!Files.isDirectory(resolved)) {
            throw new IOException("Not a directory: " + relativePath);
        }

        List<FileNode> children;
        try (Stream<Path> stream = Files.list(resolved)) {
            children = stream
                .filter(p -> !isExcluded(p.getFileName().toString()))
                .map(p -> toFileNode(p, projectRoot))
                .sorted(DIRECTORY_FIRST_THEN_NAME)
                .toList();
        }

        return new DirectoryListing(relativePath, children);
    }

    public FileContent readFileContent(Path projectRoot, String relativePath) throws IOException {
        Path resolved = sandbox(projectRoot, relativePath);

        if (!Files.isRegularFile(resolved)) {
            throw new IOException("Not a file: " + relativePath);
        }

        long size = Files.size(resolved);
        String name = resolved.getFileName().toString();

        if (isBinaryExtension(name)) {
            return new FileContent(name, relativePath, null, size, false, true);
        }

        boolean truncated = size > MAX_FILE_SIZE_BYTES;
        String content;
        if (truncated) {
            content = Files.readString(resolved, StandardCharsets.UTF_8).substring(0, (int) MAX_FILE_SIZE_BYTES);
        } else {
            content = Files.readString(resolved, StandardCharsets.UTF_8);
        }

        return new FileContent(name, relativePath, content, size, truncated, false);
    }

    Path sandbox(Path projectRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            relativePath = "";
        }

        Path canonicalRoot = projectRoot.toAbsolutePath().normalize();
        Path resolved = canonicalRoot.resolve(relativePath).normalize();

        if (!resolved.startsWith(canonicalRoot)) {
            throw new SecurityException("Path escapes project root");
        }

        return resolved;
    }

    private boolean isExcluded(String name) {
        return EXCLUDED_NAMES.contains(name);
    }

    private boolean isBinaryExtension(String fileName) {
        String lower = fileName.toLowerCase();
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex < 0) return false;
        return BINARY_EXTENSIONS.contains(lower.substring(dotIndex));
    }

    private FileNode toFileNode(Path path, Path projectRoot) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            String name = path.getFileName().toString();
            String relPath = projectRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize()).toString();
            boolean isDir = attrs.isDirectory();
            long size = isDir ? 0 : attrs.size();
            long lastModified = attrs.lastModifiedTime().toMillis();
            return new FileNode(name, relPath, isDir, size, lastModified);
        } catch (IOException e) {
            log.warn("Failed to read attributes for {}", path, e);
            String name = path.getFileName().toString();
            return new FileNode(name, name, false, 0, 0);
        }
    }

    private static final Comparator<FileNode> DIRECTORY_FIRST_THEN_NAME = (a, b) -> {
        if (a.isDirectory() && !b.isDirectory()) return -1;
        if (!a.isDirectory() && b.isDirectory()) return 1;
        return a.getName().compareToIgnoreCase(b.getName());
    };
}
