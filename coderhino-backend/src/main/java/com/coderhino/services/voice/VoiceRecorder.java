package com.coderhino.services.voice;

/**
 * Abstraction over platform-specific audio capture backends.
 * <p>
 * Implementations must handle all internal errors without propagating
 * unchecked exceptions to callers.
 */
public interface VoiceRecorder {

    /**
     * Start capturing audio from the default input device.
     * Implementations should begin accumulating raw PCM bytes in the background.
     */
    void startRecording();

    /**
     * Stop capturing audio and return accumulated raw PCM bytes.
     *
     * @return raw 16-bit signed little-endian PCM at 16 kHz mono, or {@code new byte[0]} on error
     */
    byte[] stopRecording();

    /**
     * Returns {@code true} if this recorder's underlying tool/library is available
     * on the current host without any additional setup.
     *
     * @return availability status
     */
    boolean isAvailable();
}
