package com.coderhino.web.dto;

import java.util.List;

public record CommandDto(
    String name,
    String description,
    List<String> aliases,
    boolean webCompatible,
    boolean promptBacked
) {
}
