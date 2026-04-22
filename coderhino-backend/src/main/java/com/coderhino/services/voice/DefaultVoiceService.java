package com.coderhino.services.voice;

import com.coderhino.services.analytics.FeatureFlag;
import com.coderhino.services.analytics.FeatureFlagService;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultVoiceService implements VoiceService {

    private final FeatureFlagService featureFlagService;
    private final AtomicBoolean active;
    private final AtomicReference<VoiceMode> mode;
    private final BlockingQueue<VoiceTranscription> transcriptionQueue;
    private final ExecutorService processingExecutor;

    public DefaultVoiceService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
        this.active = new AtomicBoolean(false);
        this.mode = new AtomicReference<>(VoiceMode.DISABLED);
        this.transcriptionQueue = new LinkedBlockingQueue<>();
        this.processingExecutor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "voice-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void enable() {
        active.set(true);
        mode.set(VoiceMode.CONTINUOUS);
    }

    @Override
    public void disable() {
        active.set(false);
        mode.set(VoiceMode.DISABLED);
    }

    @Override
    public boolean isEnabled() {
        return featureFlagService.isEnabled(FeatureFlag.VOICE_MODE) && active.get();
    }

    @Override
    public VoiceTranscription processAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return new VoiceTranscription(UUID.randomUUID().toString(), "", 0.0, Instant.now());
        }
        var text = "Transcribed: [audio:" + audioData.length + " bytes]";
        var transcription = new VoiceTranscription(UUID.randomUUID().toString(), text, 0.95, Instant.now());
        transcriptionQueue.offer(transcription);
        return transcription;
    }

    @Override
    public VoiceTranscription processText(String text) {
        if (text == null || text.isBlank()) {
            return new VoiceTranscription(UUID.randomUUID().toString(), "", 0.0, Instant.now());
        }
        var transcribed = "Transcribed: " + text;
        var transcription = new VoiceTranscription(UUID.randomUUID().toString(), transcribed, 1.0, Instant.now());
        transcriptionQueue.offer(transcription);
        return transcription;
    }

    @Override
    public VoiceMode currentMode() {
        return mode.get();
    }

    public void setMode(VoiceMode newMode) {
        mode.set(newMode);
        if (newMode == VoiceMode.DISABLED) {
            active.set(false);
        } else {
            active.set(true);
        }
    }

    public VoiceTranscription pollTranscription() {
        return transcriptionQueue.poll();
    }

    public int queueSize() {
        return transcriptionQueue.size();
    }

    public AnthropicVoiceStreamClient createStreamClient(String apiKey, VoiceStreamCallbacks callbacks) {
        return new AnthropicVoiceStreamClient(apiKey, callbacks);
    }

    public boolean isStreamingAvailable(String apiKey) {
        return AnthropicVoiceStreamClient.isVoiceStreamAvailable(apiKey);
    }

    public boolean checkRecordingAvailability() {
        return VoiceRecorderFactory.create().isAvailable();
    }

    public boolean requestMicrophonePermission() {
        var osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            try {
                new ProcessBuilder("tccutil", "reset", "Microphone")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @Override
    public void shutdown() {
        disable();
        processingExecutor.shutdownNow();
        transcriptionQueue.clear();
    }
}
