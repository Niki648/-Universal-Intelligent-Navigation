package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAdvisorServiceTest {

    private final RiskAdvisorService service = new RiskAdvisorService(new GuardrailService());

    @Test
    void adviseAddsMissingFieldRiskAndSanitizesUnsafeClaims() {
        TravelPlan plan = new TravelPlan("签证一定通过", "日本", null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        TravelRequirement requirement = new TravelRequirement("去日本旅行", true, List.of("days", "budget"), TravelTaskType.STRUCTURED_PLAN);

        TravelPlan result = service.advise(plan, requirement);

        assertFalse(result.summary().contains("签证一定通过"));
        assertTrue(result.risks().stream().anyMatch(risk -> risk.contains("days") && risk.contains("budget")));
    }
}
