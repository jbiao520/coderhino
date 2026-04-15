package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PendingQuestionAnswerRequest {

    @JsonProperty("toolUseId")
    private String toolUseId;

    @JsonProperty("answer")
    private String answer;

    public String getToolUseId() {
        return toolUseId;
    }

    public void setToolUseId(String toolUseId) {
        this.toolUseId = toolUseId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
