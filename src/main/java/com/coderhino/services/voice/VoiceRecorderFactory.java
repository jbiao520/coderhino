package com.coderhino.services.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoiceRecorderFactory {

    private VoiceRecorderFactory() {
    }

    public static VoiceRecorder create() {
        var osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac")) {
            return new SoxVoiceRecorder();
        }

        if (osName.contains("win")) {
            return new NativeVoiceRecorder();
        }

        if (isWsl()) {
            System.err.println("VoiceRecorderFactory: WSL detected — native audio not available, using NativeVoiceRecorder");
            return new NativeVoiceRecorder();
        }

        var arecord = new ArecordVoiceRecorder();
        if (arecord.isAvailable()) {
            return arecord;
        }
        return new SoxVoiceRecorder();
    }

    private static boolean isWsl() {
        try {
            var content = Files.readString(Path.of("/proc/version"));
            return content.toLowerCase().contains("microsoft");
        } catch (IOException e) {
            return false;
        }
    }
}
