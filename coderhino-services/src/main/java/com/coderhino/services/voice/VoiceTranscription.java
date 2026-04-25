package com.coderhino.services.voice;

import java.time.Instant;

public record VoiceTranscription(
    String sessionId,
    String text,
    double confidence,
    Instant timestamp
) {}
