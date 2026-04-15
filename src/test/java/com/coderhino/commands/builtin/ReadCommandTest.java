package com.coderhino.commands.builtin;

import com.coderhino.services.auth.UserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadCommandTest {

    @Test
    void resolveInputTreatsMissingPathAsLiteralText(@TempDir Path tempDir) {
        var resolved = ReadCommand.resolveInput("hello from test", tempDir);

        assertEquals("hello from test", resolved.text());
        assertEquals("Read aloud text.", resolved.successMessage());
        assertEquals(null, resolved.failureMessage());
    }

    @Test
    void resolveInputReadsExistingFile(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("note.txt");
        Files.writeString(file, "read me please");

        var resolved = ReadCommand.resolveInput("note.txt", tempDir);

        assertEquals("read me please", resolved.text());
        assertTrue(resolved.successMessage().contains("Read aloud file:"));
        assertEquals(null, resolved.failureMessage());
    }

    @Test
    void resolveInputRejectsUnreadablePathType(@TempDir Path tempDir) {
        var resolved = ReadCommand.resolveInput(tempDir.getFileName().toString(), tempDir.getParent());

        assertEquals(null, resolved.text());
        assertTrue(resolved.failureMessage().contains("File is not readable"));
    }

    @Test
    void readCommandIsExcludedFromModelContext() {
        var command = new ReadCommand();

        assertFalse(command.includeInModelContext());
        assertTrue(command.webCompatible());
    }

    @Test
    void parseBackendSelectionRecognizesBackendSubcommand() {
        assertEquals("status", ReadCommand.parseBackendSelection("backend"));
        assertEquals("status", ReadCommand.parseBackendSelection("backend   "));
        assertEquals("chat-tts", ReadCommand.parseBackendSelection("backend chat-tts"));
        assertEquals("edge-tts", ReadCommand.parseBackendSelection("backend EDGE-TTS"));
        assertEquals(null, ReadCommand.parseBackendSelection("hello world"));
    }

    @Test
    void selectBackendPersistsConfiguredBackend(@TempDir Path tempDir) {
        var result = ReadCommand.selectBackend("chat-tts", tempDir);

        assertTrue(result.success());
        assertEquals("/read backend set to chat-tts.", result.message());
        assertEquals("chat-tts", UserSettings.load(tempDir).getReadTtsBackend());
    }

    @Test
    void selectBackendReportsStatusUsingSavedBackend(@TempDir Path tempDir) {
        var settings = UserSettings.load(tempDir);
        settings.setReadTtsBackend("chat-tts");
        settings.save(tempDir);

        var result = ReadCommand.selectBackend("status", tempDir);

        assertTrue(result.success());
        assertTrue(result.message().contains("/read backend: chat-tts"));
    }

    @Test
    void readBackendDefaultsToChatTtsWhenUnset(@TempDir Path tempDir) {
        assertEquals("chat-tts", ReadCommand.readBackend(tempDir));
    }

    @Test
    void readBackendFallsBackToChatTtsWhenSettingIsUnsupported(@TempDir Path tempDir) {
        var settings = UserSettings.load(tempDir);
        settings.setReadTtsBackend("festival");
        settings.save(tempDir);

        assertEquals("chat-tts", ReadCommand.readBackend(tempDir));
    }

    @Test
    void selectBackendRejectsUnknownBackend(@TempDir Path tempDir) {
        var result = ReadCommand.selectBackend("festival", tempDir);

        assertFalse(result.success());
        assertTrue(result.message().contains("Unknown /read backend: festival"));
    }

    @Test
    void selectBackendStatusReportsChatTtsAsDefault(@TempDir Path tempDir) {
        var result = ReadCommand.selectBackend("status", tempDir);

        assertTrue(result.success());
        assertTrue(result.message().contains("/read backend: chat-tts (default: chat-tts)"));
    }

    @Test
    void chatTtsScriptUsesHardcodedSpeakerVectorArgument() {
        var script = ReadCommand.chatTtsScript();

        assertTrue(script.contains("np.fromstring(sys.argv[3], sep=',', dtype=np.float16).copy()"));
        assertTrue(script.contains("InferCodeParams(spk_emb=chat.speaker._encode(spk_tensor))"));
        assertTrue(script.contains("chat.infer([text], params_infer_code=params_infer_code)"));
    }

    @Test
    void normalizeChatTtsInputPreservesReportedCountAndDurationPhrase() {
        var normalized = ReadCommand.normalizeChatTtsInput("600次模型调用 / 5小时");

        assertEquals("六百次模型调用 ， 五小时", normalized);
    }

    @Test
    void normalizeChatTtsInputHandlesAdditionalMixedChineseNumericPhrase() {
        var normalized = ReadCommand.normalizeChatTtsInput("预计12分钟后重试2次");

        assertEquals("预计十二分钟后重试二次", normalized);
    }

    @Test
    void normalizeChatTtsInputSeparatesLetterPrefixedModelVersionInChineseText() {
        var normalized = ReadCommand.normalizeChatTtsInput("MiniMax M2.7 中的M 2.7 is not reading properly");

        assertEquals("MiniMax M 二点七 中的M 二点七 is not reading properly", normalized);
    }

    @Test
    void normalizeChatTtsInputLeavesPlainEnglishTextUnchanged() {
        var normalized = ReadCommand.normalizeChatTtsInput("retry in 5 minutes");

        assertEquals("retry in 5 minutes", normalized);
    }

    @Test
    void normalizeChatTtsInputDoesNotRewriteBackendSelectionKeywords() {
        assertEquals("backend edge-tts", ReadCommand.normalizeChatTtsInput("backend edge-tts"));
    }

    @Test
    void planSpeechSegmentsKeepsShortInputAsSingleSegment() {
        assertEquals(List.of("short sentence for reading"), ReadCommand.planSpeechSegments("short sentence for reading"));
    }

    @Test
    void planSpeechSegmentsSplitsLongInputAtNaturalBoundaries() {
        var text = String.join(" ", java.util.Collections.nCopies(20, "This sentence is deliberately long enough to encourage a chunk split."));

        var segments = ReadCommand.planSpeechSegments(text);

        assertTrue(segments.size() > 1);
        assertEquals(text, String.join(" ", segments));
    }

    @Test
    void generateAudioAssetKeepsShortInputOnSingleSegmentPath(@TempDir Path tempDir) throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();

        var generated = ReadCommand.generateAudioAsset(
            "hello world",
            tempDir,
            tempDir,
            (text, audioFile, cwd, backend, configDir) -> {
                calls.incrementAndGet();
                Files.writeString(audioFile, text);
            }
        );

        assertEquals(1, calls.get());
        assertTrue(Files.exists(generated.audioFile()));
        assertEquals("hello world", Files.readString(generated.audioFile()));
    }

    @Test
    void generateAudioAssetMergesChunkedSegmentsInOrder(@TempDir Path tempDir) throws Exception {
        var longText = String.join(" ", java.util.Collections.nCopies(20, "This sentence is deliberately long enough to encourage a chunk split."));

        var generated = ReadCommand.generateAudioAsset(
            longText,
            tempDir,
            tempDir,
            (text, audioFile, cwd, backend, configDir) -> {
                try {
                    writeTestWave(audioFile, text);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        );

        assertTrue(generated.audioFile().getFileName().toString().endsWith(".wav"));
        assertEquals(String.join("", ReadCommand.planSpeechSegments(longText)), readWavePayload(generated.audioFile()));
    }

    @Test
    void generateAudioAssetFailsWholeRequestWhenSegmentGenerationFails(@TempDir Path tempDir) {
        var longText = String.join(" ", java.util.Collections.nCopies(20, "This sentence is deliberately long enough to encourage a chunk split."));

        var error = assertThrows(IOException.class, () -> ReadCommand.generateAudioAsset(
            longText,
            tempDir,
            tempDir,
            (text, audioFile, cwd, backend, configDir) -> {
                if (text.contains("encourage")) {
                    throw new IOException("boom");
                }
                try {
                    writeTestWave(audioFile, text);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        ));

        assertEquals("boom", error.getMessage());
    }

    @Test
    void hardcodedSpeakerVectorContainsExpectedDimensionCount() throws Exception {
        var field = ReadCommand.class.getDeclaredField("CHAT_TTS_SPEAKER_VECTOR");
        field.setAccessible(true);
        var vector = (String) field.get(null);

        assertEquals(768, Arrays.stream(vector.split(",")).map(String::trim).filter(s -> !s.isEmpty()).count());
    }

    @Test
    void sharedChatTtsAssetDirUsesConfigDirectory(@TempDir Path tempDir) {
        assertEquals(tempDir.resolve("chat-tts").resolve("asset"), ReadCommand.sharedChatTtsAssetDir(tempDir));
    }

    @Test
    void ensureSharedChatTtsRuntimeRootReturnsExistingSharedAssets(@TempDir Path tempDir) throws Exception {
        var assetDir = createCompleteChatTtsAssetDir(ReadCommand.sharedChatTtsAssetDir(tempDir));

        var runtimeRoot = ReadCommand.ensureSharedChatTtsRuntimeRoot(tempDir, java.util.List.of(tempDir.resolve("unused-source")));

        assertEquals(tempDir.resolve("chat-tts"), runtimeRoot);
        assertTrue(ReadCommand.hasCompleteChatTtsAssets(assetDir));
    }

    @Test
    void ensureSharedChatTtsRuntimeRootBootstrapsFromExistingSource(@TempDir Path tempDir) throws Exception {
        var sourceAssetDir = createCompleteChatTtsAssetDir(tempDir.resolve("paperbot-source"));

        var runtimeRoot = ReadCommand.ensureSharedChatTtsRuntimeRoot(tempDir, java.util.List.of(sourceAssetDir));

        var sharedAssetDir = ReadCommand.sharedChatTtsAssetDir(tempDir);
        assertEquals(tempDir.resolve("chat-tts"), runtimeRoot);
        assertTrue(ReadCommand.hasCompleteChatTtsAssets(sharedAssetDir));
        assertEquals("embed", Files.readString(sharedAssetDir.resolve("Embed.safetensors")));
        assertEquals("gpt", Files.readString(sharedAssetDir.resolve("gpt").resolve("config.json")));
    }

    @Test
    void ensureSharedChatTtsRuntimeRootFailsWhenSharedAssetsAreIncomplete(@TempDir Path tempDir) throws Exception {
        var sharedAssetDir = Files.createDirectories(ReadCommand.sharedChatTtsAssetDir(tempDir));
        Files.writeString(sharedAssetDir.resolve("Embed.safetensors"), "embed");

        var error = assertThrows(IOException.class,
            () -> ReadCommand.ensureSharedChatTtsRuntimeRoot(tempDir, java.util.List.of(createCompleteChatTtsAssetDir(tempDir.resolve("source")))));

        assertTrue(error.getMessage().contains("is incomplete"));
        assertTrue(error.getMessage().contains(sharedAssetDir.toString()));
    }

    @Test
    void ensureSharedChatTtsRuntimeRootFailsWhenNoBootstrapSourceExists(@TempDir Path tempDir) {
        var missingSource = tempDir.resolve("missing-source");

        var error = assertThrows(IOException.class,
            () -> ReadCommand.ensureSharedChatTtsRuntimeRoot(tempDir, java.util.List.of(missingSource)));

        assertTrue(error.getMessage().contains("was not found"));
        assertTrue(error.getMessage().contains(ReadCommand.sharedChatTtsAssetDir(tempDir).toString()));
    }

    private static Path createCompleteChatTtsAssetDir(Path assetDir) throws Exception {
        Files.createDirectories(assetDir);
        Files.createDirectories(assetDir.resolve("gpt"));
        Files.createDirectories(assetDir.resolve("tokenizer"));
        Files.writeString(assetDir.resolve("Decoder.safetensors"), "decoder");
        Files.writeString(assetDir.resolve("DVAE.safetensors"), "dvae");
        Files.writeString(assetDir.resolve("Embed.safetensors"), "embed");
        Files.writeString(assetDir.resolve("Vocos.safetensors"), "vocos");
        Files.writeString(assetDir.resolve("gpt").resolve("config.json"), "gpt");
        Files.writeString(assetDir.resolve("tokenizer").resolve("tokenizer.json"), "tokenizer");
        return assetDir;
    }

    private static void writeTestWave(Path file, String text) throws Exception {
        var payload = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int dataSize = payload.length;
        var buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(24000);
        buffer.putInt(24000);
        buffer.putShort((short) 1);
        buffer.putShort((short) 8);
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        buffer.put(payload);
        Files.write(file, buffer.array());
    }

    private static String readWavePayload(Path file) throws Exception {
        var bytes = Files.readAllBytes(file);
        return new String(Arrays.copyOfRange(bytes, 44, bytes.length), java.nio.charset.StandardCharsets.UTF_8);
    }

}
