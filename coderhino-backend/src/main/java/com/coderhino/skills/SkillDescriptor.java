package com.coderhino.skills;

import java.util.List;

public record SkillDescriptor(String id, String name, String description, String filePath, List<String> steps, String pluginId) {

    public SkillDescriptor(String id, String name, String description, String filePath) {
        this(id, name, description, filePath, List.of(), null);
    }

    public SkillDescriptor(String id, String name, String description, String filePath, List<String> steps) {
        this(id, name, description, filePath, steps, null);
    }
}
