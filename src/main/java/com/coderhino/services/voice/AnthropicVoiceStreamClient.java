package com.coderhino.services.voice;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnthropicVoiceStreamClient {

    private static final String KEEPALIVE_MSG = "{\"type\":\"KeepAlive\"}";
    private static final String CLOSE_STREAM_MSG = "{\"type\":\"CloseStream\"}";
    private static final long KEEPALIVE_INTERVAL_MS = 8_000L;

    private final String apiKey;
    private final VoiceStreamCallbacks callbacks;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean connected;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> keepaliveFuture;

    public AnthropicVoiceStreamClient(String apiKey, VoiceStreamCallbacks callbacks) {
        this.apiKey = apiKey;
        this.callbacks = callbacks;
        this.objectMapper = new ObjectMapper();
        this.connected = new AtomicBoolean(false);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "voice-stream-keepalive");
            t.setDaemon(true);
            return t;
        });
    }

    public void connect(String wsEndpoint) {
        var listener = new Listener();
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsEndpoint), listener);
    }

    public void sendAudioFrame(byte[] pcmData) {
        if (!connected.get() || webSocket == null || pcmData == null) {
            return;
        }
        webSocket.sendBinary(ByteBuffer.wrap(pcmData), true);
    }

    public void sendKeepalive() {
        if (!connected.get() || webSocket == null) {
            return;
        }
        webSocket.sendText(KEEPALIVE_MSG, true);
    }

    public void closeStream(FinalizeSource source) {
        if (webSocket == null) {
            return;
        }
        connected.set(false);
        stopKeepalive();
        webSocket.sendText(CLOSE_STREAM_MSG, true)
                .thenRun(() -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, source.name()));
    }

    public boolean isConnected() {
        return connected.get();
    }

    public static boolean isVoiceStreamAvailable(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    private void startKeepalive() {
        keepaliveFuture = scheduler.scheduleAtFixedRate(
                this::sendKeepalive,
                KEEPALIVE_INTERVAL_MS,
                KEEPALIVE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void stopKeepalive() {
        if (keepaliveFuture != null) {
            keepaliveFuture.cancel(false);
            keepaliveFuture = null;
        }
    }

    private void handleTextMessage(String message) {
        try {
            var node = objectMapper.readTree(message);
            var typeNode = node.get("type");
            if (typeNode == null) {
                return;
            }
            var type = typeNode.asText();
            if ("transcript".equals(type)) {
                var textNode = node.get("text");
                var finalNode = node.get("is_final");
                var text = textNode != null ? textNode.asText("") : "";
                var isFinal = finalNode != null && finalNode.asBoolean(false);
                callbacks.onTranscript(text, isFinal);
            }
        } catch (Exception e) {
            callbacks.onError(e, true);
        }
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder textAccumulator = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            connected.set(true);
            var authMsg = "{\"type\":\"auth\",\"authorization\":\"Bearer " + apiKey + "\"}";
            ws.sendText(authMsg, true)
                    .thenRun(() -> {
                        startKeepalive();
                        callbacks.onReady();
                    });
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textAccumulator.append(data);
            if (last) {
                var fullMessage = textAccumulator.toString();
                textAccumulator.setLength(0);
                handleTextMessage(fullMessage);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ws.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connected.set(false);
            stopKeepalive();
            callbacks.onError(error, true);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connected.set(false);
            stopKeepalive();
            callbacks.onClose();
            return null;
        }
    }
}
