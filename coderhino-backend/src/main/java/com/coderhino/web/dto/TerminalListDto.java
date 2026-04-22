package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TerminalListDto {

    @JsonProperty("terminals")
    private List<TerminalDto> terminals;

    public TerminalListDto() {
    }

    public TerminalListDto(List<TerminalDto> terminals) {
        this.terminals = terminals;
    }

    public List<TerminalDto> getTerminals() {
        return terminals;
    }
}
