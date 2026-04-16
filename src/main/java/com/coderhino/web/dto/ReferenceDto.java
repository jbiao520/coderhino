package com.coderhino.web.dto;

public record ReferenceDto(
    String id,
    String label,
    String filename,
    String source,
    String markdown
) {

    public ReferenceDto(String id, String label, String markdown) {
        this(id, label, id + ".md", null, markdown);
    }
}
