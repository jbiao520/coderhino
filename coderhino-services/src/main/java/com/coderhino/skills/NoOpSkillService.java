package com.coderhino.skills;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * No-op implementation of {@link SkillService}.
 * All operations are non-throwing and deterministic.
 */
public final class NoOpSkillService implements SkillService {

    @Override
    public String executeSkill(String id, String input) {
        return "";
    }

    @Override
    public List<SkillDescriptor> list() {
        return Collections.emptyList();
    }

    @Override
    public Optional<SkillDescriptor> findById(String id) {
        return Optional.empty();
    }

    @Override
    public String serviceName() {
        return "skill-service";
    }
}
