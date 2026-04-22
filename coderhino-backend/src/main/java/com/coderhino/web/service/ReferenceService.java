package com.coderhino.web.service;

import com.coderhino.web.dto.ReferenceDto;
import com.coderhino.web.settings.SettingsPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class ReferenceService {

    private static final String RESOURCE_PATTERN = "classpath*:assets/references/*";

    private final ResourcePatternResolver resourcePatternResolver;
    private final SettingsPersistenceService settingsPersistenceService;

    public ReferenceService(ResourcePatternResolver resourcePatternResolver) {
        this(resourcePatternResolver, null);
    }

    @Autowired
    public ReferenceService(ResourcePatternResolver resourcePatternResolver, SettingsPersistenceService settingsPersistenceService) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.settingsPersistenceService = settingsPersistenceService;
    }

    public List<ReferenceDto> listReferences() throws IOException {
        var idsInUse = new LinkedHashSet<String>();
        var references = new ArrayList<ReferenceDto>();
        references.addAll(listBundledReferences(idsInUse));
        references.addAll(listFilesystemReferences(idsInUse));
        return references.stream()
            .sorted(Comparator
                .comparing(ReferenceDto::filename, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(reference -> reference.source() == null ? "" : reference.source(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReferenceDto::label, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private List<ReferenceDto> listBundledReferences(Set<String> idsInUse) throws IOException {
        if (resourcePatternResolver == null) {
            return List.of();
        }
        return Arrays.stream(resourcePatternResolver.getResources(RESOURCE_PATTERN))
            .filter(Resource::isReadable)
            .map(resource -> toBundledReference(resource, idsInUse))
            .flatMap(Optional::stream)
            .toList();
    }

    private List<ReferenceDto> listFilesystemReferences(Set<String> idsInUse) {
        var references = new ArrayList<ReferenceDto>();
        for (var configuredPath : configuredReferenceSourcePaths()) {
            Path directory;
            try {
                directory = Path.of(configuredPath);
            } catch (Exception ignored) {
                continue;
            }
            if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
                continue;
            }
            try (var stream = Files.list(directory)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .filter(this::isMarkdownFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(path -> toFilesystemReference(directory, path, idsInUse))
                    .flatMap(Optional::stream)
                    .forEach(references::add);
            } catch (IOException ignored) {
            }
        }
        return references;
    }

    private Optional<ReferenceDto> toBundledReference(Resource resource, Set<String> idsInUse) {
        try {
            var filename = resource.getFilename();
            if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
                return Optional.empty();
            }
            var stem = filename.substring(0, filename.length() - 3);
            try (InputStream inputStream = resource.getInputStream()) {
                var markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(new ReferenceDto(uniqueId(stem, idsInUse), toLabel(stem), filename, "Bundled", markdown));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load reference asset: " + resource, e);
        }
    }

    private Optional<ReferenceDto> toFilesystemReference(Path directory, Path file, Set<String> idsInUse) {
        var filename = file.getFileName().toString();
        var stem = filename.substring(0, filename.length() - 3);
        try {
            var markdown = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(new ReferenceDto(
                uniqueId(stem, idsInUse),
                toLabel(stem),
                filename,
                directory.getFileName() != null ? directory.getFileName().toString() : directory.toString(),
                markdown
            ));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private List<String> configuredReferenceSourcePaths() {
        if (settingsPersistenceService == null) {
            return List.of();
        }
        var settings = settingsPersistenceService.load();
        if (settings.getReferenceSourcePaths() == null) {
            return List.of();
        }
        var distinctPaths = new LinkedHashSet<String>();
        for (var path : settings.getReferenceSourcePaths()) {
            if (path == null) {
                continue;
            }
            var normalized = path.trim();
            if (!normalized.isEmpty()) {
                distinctPaths.add(normalized);
            }
        }
        return List.copyOf(distinctPaths);
    }

    private boolean isMarkdownFile(Path path) {
        var filename = path.getFileName();
        return filename != null && filename.toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private String uniqueId(String baseId, Set<String> idsInUse) {
        var normalizedBaseId = Objects.requireNonNullElse(baseId, "reference");
        var candidate = normalizedBaseId;
        var suffix = 2;
        while (!idsInUse.add(candidate)) {
            candidate = normalizedBaseId + "-" + suffix;
            suffix += 1;
        }
        return candidate;
    }

    private String toLabel(String stem) {
        return Arrays.stream(stem.split("[-_]+"))
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .reduce((left, right) -> left + " " + right)
            .orElse(stem);
    }
}
