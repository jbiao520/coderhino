package com.coderhino.services.voice;

/**
 * Voice input mode for the voice pipeline.
 * <p>
 * Mirrors the TypeScript {@code voiceModeEnabled.ts} feature-flag gated enablement.
 */
public enum VoiceMode {

    /**
     * Voice input is disabled. No audio capture or transcription occurs.
     */
    DISABLED,

    /**
     * Voice input is active only while the user holds a key/button.
     * Releases end the recording and trigger transcription.
     */
    PUSH_TO_TALK,

    /**
     * Voice input is always-on. The service continuously listens and
     * transcribes speech in real time.
     */
    CONTINUOUS
}
