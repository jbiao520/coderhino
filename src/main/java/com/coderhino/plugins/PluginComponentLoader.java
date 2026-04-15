package com.coderhino.plugins;

import com.coderhino.skills.SkillDescriptor;
import com.coderhino.skills.SkillService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PluginComponentLoader {
    private final SkillService skillService;

    public PluginComponentLoader(SkillService skillService) {
        this.skillService = skillService;
    }

    public void loadComponents(PluginManifest manifest) {
        if (!manifest.isEnabled()) {
            return;
        }
        List<String> skillPaths = manifest.getSkills();
        if (skillPaths != null) {
            for (String relativePath : skillPaths) {
                Path skillFile = manifest.getPath().resolve(relativePath);
                String filename = skillFile.getFileName().toString();
                String skillName = filename.contains(".")
                        ? filename.substring(0, filename.lastIndexOf('.'))
                        : filename;
                String skillId = manifest.getId() + ":" + skillName;
                if (skillService.findById(skillId).isPresent()) {
                    continue; // idempotent — skip if already registered
                }
                try {
                    Files.readString(skillFile);
                } catch (IOException e) {
                    // skill file missing — skip silently
                    continue;
                }
                var descriptor = new SkillDescriptor(skillId, skillName, manifest.getName(), skillFile.toString(), List.of(), manifest.getId());
                // Plugin skills still persist through the legacy JSON store until plugin markdown registration is unified.
                if (skillService instanceof com.coderhino.skills.FileSystemSkillService fsSkill) {
                    fsSkill.saveSkill(descriptor);
                }
            }
        }
        // Log deferred command/agent registration
        if (manifest.getCommands() != null && !manifest.getCommands().isEmpty()) {
            System.out.println("[plugin] Plugin " + manifest.getId() + " declares " + manifest.getCommands().size() + " command(s) - registration not yet implemented");
        }
    }

    public int unloadComponents(String pluginId) {
        List<SkillDescriptor> allSkills = skillService.list();
        String prefix = pluginId + ":";
        int count = 0;
        for (SkillDescriptor skill : allSkills) {
            if (skill.id().startsWith(prefix)) {
                skillService.remove(skill.id());
                count++;
            }
        }
        return count;
    }
}
