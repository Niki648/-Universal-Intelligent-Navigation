package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskAdvisorService {

    private final GuardrailService guardrailService;

    public RiskAdvisorService(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    public TravelPlan advise(TravelPlan plan, TravelRequirement requirement) {
        TravelPlan sanitized = guardrailService.sanitizeTravelPlanOutput(plan);
        List<String> risks = new ArrayList<>(sanitized.risks());
        if (!requirement.missingFields().isEmpty()) {
            addIfAbsent(risks, "仍需补充关键信息：" + String.join(", ", requirement.missingFields()) + "，否则行程和预算只能作为草案。");
        }
        if (risks.isEmpty()) {
            risks.add("天气、交通、开放时间和价格可能变化，请在出发前进行实时确认。");
        }
        return new TravelPlan(
                sanitized.summary(),
                sanitized.destination(),
                sanitized.departure(),
                sanitized.days(),
                sanitized.travelers(),
                sanitized.budget(),
                sanitized.itineraryDays(),
                sanitized.transportation(),
                sanitized.accommodation(),
                risks,
                sanitized.alternatives(),
                sanitized.loadedSkills()
        );
    }

    private void addIfAbsent(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
