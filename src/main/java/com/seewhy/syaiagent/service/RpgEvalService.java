package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.eval.TravelEvalCase;
import com.seewhy.syaiagent.eval.TravelEvalHarness;
import com.seewhy.syaiagent.model.rpg.RpgEvalRule;
import com.seewhy.syaiagent.model.rpg.RpgEvalSampleResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RpgEvalService {

    private final TravelEvalHarness travelEvalHarness;
    private final WayfinderDemoService wayfinderDemoService;

    public RpgEvalService(TravelEvalHarness travelEvalHarness, WayfinderDemoService wayfinderDemoService) {
        this.travelEvalHarness = travelEvalHarness;
        this.wayfinderDemoService = wayfinderDemoService;
    }

    public List<TravelEvalCase> getCases() {
        return travelEvalHarness.loadDefaultCases();
    }

    public List<RpgEvalRule> getRules() {
        return List.of(
                new RpgEvalRule("clarifying-question", "Ask Missing Info", 10,
                        "Checks whether underspecified requests ask for destination, days, budget, travelers, or preferences."),
                new RpgEvalRule("structured-itinerary", "Structured Itinerary", 20,
                        "Checks whether the TravelPlan contains itinerary day structure suitable for UI cards."),
                new RpgEvalRule("budget-reasonableness", "Budget Reasonableness", 15,
                        "Checks budget currency, plausible totals, and itemized or explained estimates."),
                new RpgEvalRule("risk-reminders", "Risk Reminders", 15,
                        "Checks whether weather, visa, policy, health, schedule, or other uncertainty reminders are present."),
                new RpgEvalRule("unsafe-claims", "No Absolute Promise", 20,
                        "Penalizes guarantees about safety, visa approval, prices, weather, or opening hours."),
                new RpgEvalRule("disallowed-tools", "No Forbidden Tools", 10,
                        "Checks deterministic eval paths do not rely on terminal, file-write, or resource-download tools."),
                new RpgEvalRule("expected-skills", "Skills Loaded", 10,
                        "Checks whether expected travel skills are represented in the generated TravelPlan.")
        );
    }

    public List<RpgEvalSampleResult> getSampleResults() {
        return wayfinderDemoService.demoEvalResults();
    }
}
