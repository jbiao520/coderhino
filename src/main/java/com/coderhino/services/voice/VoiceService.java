package com.coderhino.services.voice;

public interface VoiceService {

    void enable();

    void disable();

    boolean isEnabled();

    VoiceTranscription processAudio(byte[] audioData);

    VoiceTranscription processText(String text);

    void shutdown();

    default VoiceMode currentMode() {
        return isEnabled() ? VoiceMode.CONTINUOUS : VoiceMode.DISABLED;
    }

    default String serviceName() {
        return "voice-service";
    }
}
