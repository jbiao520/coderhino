package com.coderhino.services.voice;

public interface VoiceStreamCallbacks {

    void onTranscript(String text, boolean isFinal);

    void onError(Throwable error, boolean canRetry);

    void onClose();

    void onReady();
}
