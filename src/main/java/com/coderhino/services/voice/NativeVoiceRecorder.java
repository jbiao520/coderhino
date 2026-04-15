package com.coderhino.services.voice;

public final class NativeVoiceRecorder implements VoiceRecorder {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void startRecording() {
        System.err.println("NativeVoiceRecorder: no native audio library available");
    }

    @Override
    public byte[] stopRecording() {
        return new byte[0];
    }
}
