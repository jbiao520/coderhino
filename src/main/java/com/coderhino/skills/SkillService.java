package com.coderhino.skills;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for executing and querying skills.
 */
public interface SkillService {

    String executeSkill(String id, String input);

    List<SkillDescriptor> list();

    Optional<SkillDescriptor> findById(String id);

    default void remove(String name) {}

    default String serviceName() {
        return "skill-service";
    }
}
