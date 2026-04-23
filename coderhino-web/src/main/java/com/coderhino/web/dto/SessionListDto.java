package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SessionListDto {

    @JsonProperty("sessions")
    private List<SessionDto> sessions;

    @JsonProperty("count")
    private int count;

    public SessionListDto() {
        this.sessions = new ArrayList<>();
    }

    public SessionListDto(List<SessionDto> sessions) {
        this.sessions = new ArrayList<>(sessions);
        this.count = sessions.size();
    }

    public List<SessionDto> getSessions() { return sessions; }
    public void setSessions(List<SessionDto> sessions) {
        this.sessions = sessions;
        this.count = sessions != null ? sessions.size() : 0;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
