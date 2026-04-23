package com.coderhino.web.dto;

import java.util.List;

public record CommandExecuteRequest(
    String command,
    List<String> arguments,
    String sessionId
) {
}
