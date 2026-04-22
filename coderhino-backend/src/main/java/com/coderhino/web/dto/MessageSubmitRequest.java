package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public class MessageSubmitRequest {

    @JsonProperty("input")
    @JsonAlias("message")
    private String input;

    @JsonProperty("model")
    private String model;

    @JsonProperty("providerId")
    private String providerId;

    @JsonProperty("buildMode")
    private Boolean buildMode;

    @JsonProperty("planMode")
    private Boolean planMode;

    @JsonProperty("modelMode")
    private String modelMode;

    @JsonProperty("visiblePrompt")
    private String visiblePrompt;

    public MessageSubmitRequest() {
    }

    public MessageSubmitRequest(String input) {
        this.input = input;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public Boolean getBuildMode() {
        return buildMode;
    }

    public void setBuildMode(Boolean buildMode) {
        this.buildMode = buildMode;
    }

    public Boolean getPlanMode() {
        return planMode;
    }

    public void setPlanMode(Boolean planMode) {
        this.planMode = planMode;
    }

    public String getModelMode() {
        return modelMode;
    }

    public void setModelMode(String modelMode) {
        this.modelMode = modelMode;
    }

    public String getVisiblePrompt() {
        return visiblePrompt;
    }

    public void setVisiblePrompt(String visiblePrompt) {
        this.visiblePrompt = visiblePrompt;
    }
}
