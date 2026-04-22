package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionGitStatusDto {

    public static final String KIND_TRACKED = "tracked";
    public static final String KIND_UNVERSIONED = "unversioned";

    @JsonProperty("trackedChanges")
    private List<GitEntry> trackedChanges;

    @JsonProperty("unversionedFiles")
    private List<GitEntry> unversionedFiles;

    public SessionGitStatusDto() {
    }

    public SessionGitStatusDto(List<GitEntry> trackedChanges, List<GitEntry> unversionedFiles) {
        this.trackedChanges = trackedChanges != null ? List.copyOf(trackedChanges) : List.of();
        this.unversionedFiles = unversionedFiles != null ? List.copyOf(unversionedFiles) : List.of();
    }

    public List<GitEntry> getTrackedChanges() {
        return trackedChanges;
    }

    public void setTrackedChanges(List<GitEntry> trackedChanges) {
        this.trackedChanges = trackedChanges != null ? List.copyOf(trackedChanges) : List.of();
    }

    public List<GitEntry> getUnversionedFiles() {
        return unversionedFiles;
    }

    public void setUnversionedFiles(List<GitEntry> unversionedFiles) {
        this.unversionedFiles = unversionedFiles != null ? List.copyOf(unversionedFiles) : List.of();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GitEntry {
        @JsonProperty("kind")
        private String kind;

        @JsonProperty("path")
        private String path;

        @JsonProperty("status")
        private String status;

        public GitEntry() {
        }

        public GitEntry(String kind, String path, String status) {
            this.kind = kind;
            this.path = path;
            this.status = status;
        }

        public static GitEntry tracked(String path, String status) {
            return new GitEntry(KIND_TRACKED, path, status);
        }

        public static GitEntry unversioned(String path) {
            return new GitEntry(KIND_UNVERSIONED, path, null);
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
