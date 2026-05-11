package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.rpg.RpgEvalSampleResult;
import com.seewhy.syaiagent.trace.AgentTraceEvent;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WayfinderDemoService {

    private final boolean enabled;

    public WayfinderDemoService(@Value("${wayfinder.demo.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TravelPlan demoTravelPlan() {
        return new TravelPlan(
                "A relaxed seven-day Japan family trip with a clear budget, slower pacing, and visible risk reminders.",
                "Japan",
                "Shanghai",
                7,
                3,
                new TravelPlan.Budget(
                        BigDecimal.valueOf(20000),
                        "CNY",
                        List.of(
                                new TravelPlan.BudgetItem("Flights", BigDecimal.valueOf(7500), "Estimate for three travelers."),
                                new TravelPlan.BudgetItem("Hotels", BigDecimal.valueOf(6300), "Family-friendly midrange stays."),
                                new TravelPlan.BudgetItem("Local transport and food", BigDecimal.valueOf(4200), "Flexible daily estimate.")
                        ),
                        "Demo estimate only. Real prices require current flight and hotel checks."
                ),
                List.of(
                        new TravelPlan.ItineraryDay(1, "Arrival and easy settling in",
                                List.of(new TravelPlan.Activity("Afternoon", "Arrive in Osaka", "Check in, short neighborhood walk, early rest.", "Osaka", "medium", List.of("Keep the first day light."))),
                                List.of("Simple local dinner"), "Osaka family hotel", "Airport rail", "relaxed", List.of("Avoid late-night transfers.")),
                        new TravelPlan.ItineraryDay(2, "Kyoto classic route",
                                List.of(new TravelPlan.Activity("Morning", "Fushimi Inari", "Visit early and choose a partial route for parents.", "Kyoto", "low", List.of("Stop before the full mountain trail if tired."))),
                                List.of("Kyoto home-style lunch"), "Kyoto hotel", "JR/local train", "relaxed", List.of("Check weather and walking distance."))
                ),
                List.of("JR/local train", "Taxi fallback for parents when tired"),
                List.of("Midrange family hotel near transit"),
                List.of("Weather, hotel prices, opening hours, and visa policies may change; confirm official sources before booking."),
                List.of("Reduce Kyoto temples if parents prefer fewer walks.", "Swap one city day for an onsen rest day."),
                List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel")
        );
    }

    public RagExplainResponse demoRagExplain(String originalQuery, String chatId) {
        return DemoRagExplainResponses.build(originalQuery, chatId);
    }

    public List<AgentTraceEvent> demoTrace(String chatId) {
        String id = chatId == null || chatId.isBlank() ? "demo-japan-family" : chatId;
        Instant base = Instant.parse("2026-05-10T08:00:00Z");
        return List.of(
                event(id, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.COMPLETED, "Recognized a relaxed Japan family trip request.", base, Map.of("travelers", 3, "destination", "Japan")),
                event(id, AgentTraceStep.SKILL_LOADING, AgentTraceStatus.COMPLETED, "Loaded matching travel skills.", base.plusSeconds(2), Map.of("loadedSkills", List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel"))),
                event(id, AgentTraceStep.ITINERARY_GENERATION, AgentTraceStatus.COMPLETED, "Generated a structured itinerary draft.", base.plusSeconds(5), Map.of("days", 7)),
                event(id, AgentTraceStep.BUDGET_CHECK, AgentTraceStatus.COMPLETED, "Budget structure checked.", base.plusSeconds(7), Map.of("currency", "CNY", "estimate", 20000)),
                event(id, AgentTraceStep.RISK_CHECK, AgentTraceStatus.COMPLETED, "Risk reminders and uncertainty wording applied.", base.plusSeconds(9), Map.of("riskCount", 1))
        );
    }

    public List<RpgEvalSampleResult> demoEvalResults() {
        return List.of(
                new RpgEvalSampleResult("clarifying-question", true, 10, 10, "No follow-up required for the Japan family case."),
                new RpgEvalSampleResult("structured-itinerary", true, 20, 20, "TravelPlan contains itineraryDays for UI rendering."),
                new RpgEvalSampleResult("budget-reasonableness", true, 15, 15, "Budget total, currency, and itemized estimates are present."),
                new RpgEvalSampleResult("risk-reminders", true, 15, 15, "Visa, weather, price, and schedule uncertainty are surfaced."),
                new RpgEvalSampleResult("unsafe-claims", true, 20, 20, "No absolute safety, visa, weather, or price guarantees detected."),
                new RpgEvalSampleResult("disallowed-tools", true, 10, 10, "No terminal, file-write, or resource-download calls observed."),
                new RpgEvalSampleResult("expected-skills", true, 10, 10, "Expected skill IDs are present in loadedSkills.")
        );
    }

    private AgentTraceEvent event(String chatId, AgentTraceStep step, AgentTraceStatus status, String message, Instant timestamp, Map<String, Object> metadata) {
        return new AgentTraceEvent(UUID.randomUUID().toString(), chatId, step, status, message, metadata, timestamp);
    }
}
