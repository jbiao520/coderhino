package com.coderhino.services.voice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

public final class SoxVoiceRecorder implements VoiceRecorder {

    private final AtomicReference<Process> activeProcess = new AtomicReference<>();
    private final AtomicReference<ByteArrayOutputStream> buffer = new AtomicReference<>();
    private final AtomicReference<Thread> readerThread = new AtomicReference<>();

    @Override
    public boolean isAvailable() {
        try {
            var pb = new ProcessBuilder("sox", "--version");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            int exit = proc.waitFor();
            return exit == 0;
        } catch (Exception e) {
            try {
                var pb = new ProcessBuilder("which", "sox");
                pb.redirectErrorStream(true);
                var proc = pb.start();
                int exit = proc.waitFor();
                return exit == 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    @Override
    public void startRecording() {
        try {
            var bos = new ByteArrayOutputStream();
            buffer.set(bos);

            var pb = new ProcessBuilder(
                    "sox", "-d", "-t", "raw", "-r", "16000", "-e", "signed", "-b", "16", "-c", "1", "-");
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
            }, "sox-reader");
            reader.setDaemon(true);
            reader.start();
            readerThread.set(reader);
        } catch (Exception e) {
            System.err.println("SoxVoiceRecorder: failed to start sox process — " + e.getMessage());
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
            System.err.println("SoxVoiceRecorder: error stopping recording — " + e.getMessage());
        }
        return new byte[0];
    }
}
