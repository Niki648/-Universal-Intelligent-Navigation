package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgSkillMatch(
        String id,
        String name,
        String rpgName,
        String level,
        String category,
        String description,
        List<String> triggers,
        String matchedReason
) {
}
