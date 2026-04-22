package com.coderhino.web.dto;

public record CommandExecuteResponse(
    String prompt,
    String output,
    boolean success,
    String commandName,
    CommandAudioDto audio
) {
    public CommandExecuteResponse(String prompt, String output, boolean success, String commandName) {
        this(prompt, output, success, commandName, null);
    }
}
