package com.seewhy.syaiagent.orchestrator;

import java.util.List;

public record TravelRequirement(
        String message,
        boolean travelRelated,
        List<String> missingFields,
        TravelTaskType taskType
) {
    public TravelRequirement {
        message = message == null ? "" : message.strip();
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        taskType = taskType == null ? TravelTaskType.STRUCTURED_PLAN : taskType;
    }
}
