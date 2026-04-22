package com.coderhino.services.voice;

public final class NoOpVoiceService implements VoiceService {

    @Override
    public void enable() {
    }

    @Override
    public void disable() {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public VoiceTranscription processAudio(byte[] audioData) {
        return null;
    }

    @Override
    public VoiceTranscription processText(String text) {
        return null;
    }

    @Override
    public void shutdown() {
    }
}
