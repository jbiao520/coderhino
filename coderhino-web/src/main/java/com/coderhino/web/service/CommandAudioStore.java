package com.coderhino.web.service;

import com.coderhino.web.dto.CommandAudioDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CommandAudioStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, StoredAudio> audioByToken = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public CommandAudioStore() {
        this(DEFAULT_TTL, Clock.systemUTC());
    }

    CommandAudioStore(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public CommandAudioDto store(Path audioFile) {
        cleanupExpired();
        var token = UUID.randomUUID().toString();
        audioByToken.put(token, new StoredAudio(audioFile, now().plus(ttl)));
        return new CommandAudioDto(token, "/api/commands/audio/" + token);
    }

    public Optional<Path> resolve(String token) {
        cleanupExpired();
        var stored = audioByToken.get(token);
        if (stored == null) {
            return Optional.empty();
        }
        if (!Files.exists(stored.path())) {
            delete(token);
            return Optional.empty();
        }
        return Optional.of(stored.path());
    }

    public void delete(String token) {
        var removed = audioByToken.remove(token);
        if (removed == null) {
            return;
        }
        try {
            Files.deleteIfExists(removed.path());
        } catch (IOException ignored) {
        }
    }

    public void cleanupExpired() {
        var current = now();
        audioByToken.forEach((token, stored) -> {
            if (stored.expiresAt().isAfter(current)) {
                return;
            }
            delete(token);
        });
    }

    private Instant now() {
        return clock.instant();
    }

    private record StoredAudio(Path path, Instant expiresAt) {
    }
}
