package com.seewhy.syaiagent.skill;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLoaderServiceTest {

    private final SkillLoaderService skillLoaderService =
            new SkillLoaderService(new PathMatchingResourcePatternResolver());

    @Test
    void loadAllSkillsReadsConfiguredMarkdownSkills() {
        List<Skill> skills = skillLoaderService.loadAllSkills();

        assertTrue(skills.size() >= 5);
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("family-trip-planning")));
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("japan-travel")));
    }

    @Test
    void selectSkillsMatchesExampleTravelRequest() {
        List<String> selectedSkillIds = skillLoaderService
                .selectSkills("我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点")
                .stream()
                .map(Skill::id)
                .toList();

        assertEquals(List.of(
                "family-trip-planning",
                "japan-travel",
                "relaxed-travel",
                "budget-travel"
        ), selectedSkillIds);
    }
}
