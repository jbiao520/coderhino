package com.coderhino.services.voice.keyterms;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VoiceKeyterms {

    private final Set<String> keyterms;

    public VoiceKeyterms() {
        this(List.of("hey claude", "claude", "computer", "assistant"));
    }

    public VoiceKeyterms(List<String> terms) {
        var set = new LinkedHashSet<String>();
        for (var term : terms) {
            if (term != null && !term.isBlank()) {
                set.add(term.toLowerCase().trim());
            }
        }
        this.keyterms = Collections.unmodifiableSet(set);
    }

    public boolean containsKeyterm(String transcribedText) {
        if (transcribedText == null || transcribedText.isBlank()) {
            return false;
        }
        var lower = transcribedText.toLowerCase();
        for (var term : keyterms) {
            if (lower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public String detectKeyterm(String transcribedText) {
        if (transcribedText == null || transcribedText.isBlank()) {
            return null;
        }
        var lower = transcribedText.toLowerCase();
        for (var term : keyterms) {
            if (lower.contains(term)) {
                return term;
            }
        }
        return null;
    }

    public Set<String> keyterms() {
        return keyterms;
    }

    public VoiceKeyterms withAdditional(String term) {
        if (term == null || term.isBlank()) {
            return this;
        }
        var newList = new java.util.ArrayList<>(keyterms);
        newList.add(term.toLowerCase().trim());
        return new VoiceKeyterms(newList);
    }
}
