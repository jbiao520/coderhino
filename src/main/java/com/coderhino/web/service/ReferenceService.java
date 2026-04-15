package com.coderhino.web.service;

import com.coderhino.web.dto.ReferenceDto;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReferenceService {

    private static final String RESOURCE_PATTERN = "classpath*:assets/references/*";

    private final ResourcePatternResolver resourcePatternResolver;

    public ReferenceService(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<ReferenceDto> listReferences() throws IOException {
        return Arrays.stream(resourcePatternResolver.getResources(RESOURCE_PATTERN))
            .filter(Resource::isReadable)
            .map(this::toReference)
            .flatMap(Optional::stream)
            .sorted(Comparator.comparing(ReferenceDto::label, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private Optional<ReferenceDto> toReference(Resource resource) {
        try {
            var filename = resource.getFilename();
            if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
                return Optional.empty();
            }
            var stem = filename.substring(0, filename.length() - 3);
            try (InputStream inputStream = resource.getInputStream()) {
                var markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(new ReferenceDto(stem, toLabel(stem), markdown));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load reference asset: " + resource, e);
        }
    }

    private String toLabel(String stem) {
        return Arrays.stream(stem.split("[-_]+"))
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .reduce((left, right) -> left + " " + right)
            .orElse(stem);
    }
}
