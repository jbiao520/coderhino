package com.coderhino.services.voice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class ArecordVoiceRecorder implements VoiceRecorder {

    private final AtomicReference<Process> activeProcess = new AtomicReference<>();
    private final AtomicReference<ByteArrayOutputStream> buffer = new AtomicReference<>();
    private final AtomicReference<Thread> readerThread = new AtomicReference<>();

    @Override
    public boolean isAvailable() {
        if (new File("/proc/asound/cards").exists()) {
            return true;
        }
        try {
            var pb = new ProcessBuilder("arecord", "--version");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            int exit = proc.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void startRecording() {
        try {
            var bos = new ByteArrayOutputStream();
            buffer.set(bos);

            var pb = new ProcessBuilder(
                    "arecord", "-f", "S16_LE", "-r", "16000", "-c", "1", "-t", "raw");
            pb.redirectErrorStream(false);
            var proc = pb.start();
            activeProcess.set(proc);

            var stdout = proc.getInputStream();
            var reader = new Thread(() -> {
                try {
                    var chunk = new byte[4096];
                    int n;
                    while ((n = stdout.read(chunk)) != -1) {
                        synchronized (bos) {
                            bos.write(chunk, 0, n);
                        }
                    }
                } catch (Exception ignored) {
                }
            }, "arecord-reader");
            reader.setDaemon(true);
            reader.start();
            readerThread.set(reader);
        } catch (Exception e) {
            System.err.println("ArecordVoiceRecorder: failed to start arecord process — " + e.getMessage());
        }
    }

    @Override
    public byte[] stopRecording() {
        try {
            var proc = activeProcess.getAndSet(null);
            if (proc != null) {
                proc.destroy();
            }
            var reader = readerThread.getAndSet(null);
            if (reader != null) {
                reader.join(2000);
            }
            var bos = buffer.getAndSet(null);
            if (bos != null) {
                synchronized (bos) {
                    return bos.toByteArray();
                }
            }
        } catch (Exception e) {
            System.err.println("ArecordVoiceRecorder: error stopping recording — " + e.getMessage());
        }
        return new byte[0];
    }
}
