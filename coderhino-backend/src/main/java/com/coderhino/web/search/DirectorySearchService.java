package com.coderhino.web.search;

import com.coderhino.web.dto.SearchResult;
import com.coderhino.web.dto.SearchResult.MatchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DirectorySearchService {

    private static final Logger log = LoggerFactory.getLogger(DirectorySearchService.class);
    private static final int MAX_DEPTH = 3;
    private static final int MAX_RESULTS = 50;
    private static final Set<String> EXCLUDED_DIRS = Set.of(
        ".git", ".svn", ".hg", ".bzr", ".jj", ".sl",
        "node_modules", ".gradle", "target", "__pycache__",
        ".cache", ".config", ".local", ".npm", ".yarn",
        "build", "dist", ".next", ".nuxt"
    );

    public List<SearchResult> searchDirectories(String query) {
        return searchDirectories(Path.of(System.getProperty("user.home")), query);
    }

    List<SearchResult> searchDirectories(Path homeDir, String query) {
        if (!isAccessibleDirectory(homeDir)) {
            return List.of();
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();

        Map<MatchType, List<SearchResult>> grouped = new EnumMap<>(MatchType.class);
        for (MatchType type : MatchType.values()) {
            grouped.put(type, new ArrayList<>());
        }

        try {
            walkDirectories(homeDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(homeDir) && isExcluded(dir, homeDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (!dir.equals(homeDir)) {
                        addMatch(dir, normalizedQuery, grouped);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (isSkippableTraversalException(exc)) {
                        log.debug("Skipping inaccessible path {}: {}", file, exc.getMessage());
                        return FileVisitResult.CONTINUE;
                    }
                    throw exc;
                }
            });
        } catch (IOException e) {
            log.error("Error searching directories under {}", homeDir, e);
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        results.addAll(grouped.get(MatchType.EXACT));
        results.addAll(grouped.get(MatchType.STARTS_WITH));
        results.addAll(grouped.get(MatchType.CONTAINS));

        if (results.size() > MAX_RESULTS) {
            return results.subList(0, MAX_RESULTS);
        }
        return results;
    }

    void walkDirectories(Path homeDir, SimpleFileVisitor<Path> visitor) throws IOException {
        Files.walkFileTree(homeDir, Set.of(), MAX_DEPTH, visitor);
    }

    private void addMatch(Path path, String normalizedQuery, Map<MatchType, List<SearchResult>> grouped) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return;
        }

        String dirName = fileName.toString();
        String dirNameLower = dirName.toLowerCase(Locale.ROOT);

        MatchType matchType = null;
        if (dirNameLower.equals(normalizedQuery)) {
            matchType = MatchType.EXACT;
        } else if (dirNameLower.startsWith(normalizedQuery)) {
            matchType = MatchType.STARTS_WITH;
        } else if (dirNameLower.contains(normalizedQuery)) {
            matchType = MatchType.CONTAINS;
        }

        if (matchType != null) {
            String absolutePath = path.toAbsolutePath().normalize().toString();
            grouped.get(matchType).add(new SearchResult(absolutePath, dirName, matchType));
        }
    }

    private boolean isExcluded(Path path, Path basePath) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }

        String dirName = fileName.toString();
        if (dirName.startsWith(".")) {
            return true;
        }

        Path relative = basePath.relativize(path);
        for (int i = 0; i < relative.getNameCount(); i++) {
            String component = relative.getName(i).toString();
            if (EXCLUDED_DIRS.contains(component)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAccessibleDirectory(Path path) {
        try {
            return Files.isDirectory(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    boolean isSkippableTraversalException(IOException exception) {
        if (exception instanceof AccessDeniedException) {
            return true;
        }

        if (exception instanceof FileSystemException fileSystemException) {
            String reason = fileSystemException.getReason();
            if (reason == null) {
                return false;
            }

            String normalizedReason = reason.toLowerCase(Locale.ROOT);
            return normalizedReason.contains("operation not permitted")
                || normalizedReason.contains("permission denied");
        }

        return false;
    }
}
