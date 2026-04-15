package com.coderhino.web.service;

import com.coderhino.commands.builtin.ReadCommand;
import com.coderhino.state.BootstrapState;
import com.coderhino.web.dto.CommandExecuteResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class ReadCommandWebService {

    private final CommandAudioStore commandAudioStore;

    public ReadCommandWebService(CommandAudioStore commandAudioStore) {
        this.commandAudioStore = commandAudioStore;
    }

    public CommandExecuteResponse execute(String prompt, List<String> args, BootstrapState targetState) {
        ReadCommand.GeneratedAudio generatedAudio = null;
        try {
            var cwd = Path.of(targetState.get().cwd());
            var rawInput = String.join(" ", args).trim();
            var backendSelection = ReadCommand.parseBackendSelection(rawInput);
            if (backendSelection != null) {
                var result = ReadCommand.selectBackend(backendSelection, Path.of(System.getProperty("user.home"), ".coderhino"));
                return new CommandExecuteResponse(prompt, result.message(), result.success(), "read");
            }
            generatedAudio = ReadCommand.generateAudioAsset(rawInput, cwd);
            var audio = commandAudioStore.store(generatedAudio.audioFile());
            return new CommandExecuteResponse(prompt, generatedAudio.successMessage(), true, "read", audio);
        } catch (IOException e) {
            deleteQuietly(generatedAudio);
            return new CommandExecuteResponse(prompt, e.getMessage(), false, "read");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(generatedAudio);
            return new CommandExecuteResponse(prompt, "/read was interrupted.", false, "read");
        }
    }

    private static void deleteQuietly(ReadCommand.GeneratedAudio generatedAudio) {
        if (generatedAudio == null) {
            return;
        }
        try {
            Files.deleteIfExists(generatedAudio.audioFile());
        } catch (IOException ignored) {
        }
    }
}
