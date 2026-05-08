package com.seewhy.syaiagent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.TravelPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Slf4j
public class TravelEvalHarness {

    private static final String DEFAULT_CASES_PATH = "evals/travel-cases.json";
    private static final int PASS_THRESHOLD_PERCENT = 70;

    private final ObjectMapper objectMapper;

    public TravelEvalHarness(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TravelEvalCase> loadDefaultCases() {
        return loadCases(new FileSystemResource(DEFAULT_CASES_PATH));
    }

    public List<TravelEvalCase> loadCases(Resource resource) {
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load travel eval cases from " + resource, ex);
        }
    }

    public TravelEvalResult evaluate(TravelEvalCase evalCase, TravelPlan plan) {
        return evaluate(evalCase, plan, List.of());
    }

    public TravelEvalResult evaluate(TravelEvalCase evalCase, TravelPlan plan, List<String> observedToolCalls) {
        List<TravelEvalRuleResult> rules = new ArrayList<>();
        rules.add(checkClarifyingQuestions(evalCase, plan));
        rules.add(checkStructuredItinerary(evalCase, plan));
        rules.add(checkBudget(evalCase, plan));
        rules.add(checkRisks(plan));
        rules.add(checkUnsafeClaims(plan));
        rules.add(checkDisallowedTools(evalCase, observedToolCalls));
        rules.add(checkExpectedSkills(evalCase, plan));

        int score = rules.stream().mapToInt(TravelEvalRuleResult::score).sum();
        int maxScore = rules.stream().mapToInt(TravelEvalRuleResult::maxScore).sum();
        boolean passed = maxScore == 0 || score * 100 / maxScore >= PASS_THRESHOLD_PERCENT;
        return new TravelEvalResult(evalCase.id(), evalCase.name(), score, maxScore, passed, rules);
    }

    private TravelEvalRuleResult checkClarifyingQuestions(TravelEvalCase evalCase, TravelPlan plan) {
        if (!evalCase.requiresClarifyingQuestion()) {
            return pass("clarifying-question", 10, "No clarifying question required for this case.");
        }
        String text = searchableText(plan);
        boolean found = containsAny(text, "请补充", "请确认", "需要确认", "需要补充", "建议补充", "出发地", "预算", "天数", "人数", "?");
        return result("clarifying-question", found, 10,
                found ? "Missing information is surfaced." : "Underspecified request did not ask for missing information.");
    }

    private TravelEvalRuleResult checkStructuredItinerary(TravelEvalCase evalCase, TravelPlan plan) {
        int itinerarySize = plan == null ? 0 : plan.itineraryDays().size();
        Integer expectedDays = evalCase.expectedDays();
        boolean passed = expectedDays == null ? itinerarySize > 0 : itinerarySize >= Math.min(expectedDays, 2);
        return result("structured-itinerary", passed, 20,
                passed ? "Itinerary structure is present." : "Itinerary days are missing or too sparse.");
    }

    private TravelEvalRuleResult checkBudget(TravelEvalCase evalCase, TravelPlan plan) {
        if (evalCase.expectedBudgetTotal() == null) {
            return pass("budget-reasonableness", 15, "No fixed budget required for this case.");
        }
        TravelPlan.Budget budget = plan == null ? null : plan.budget();
        if (budget == null || budget.total() == null) {
            return fail("budget-reasonableness", 15, "Budget total is missing.");
        }
        boolean currencyOk = Objects.equals(evalCase.expectedCurrency(), budget.currency());
        BigDecimal ratio = budget.total()
                .divide(evalCase.expectedBudgetTotal(), 4, RoundingMode.HALF_UP);
        boolean amountOk = ratio.compareTo(BigDecimal.valueOf(0.6)) >= 0
                && ratio.compareTo(BigDecimal.valueOf(1.3)) <= 0;
        boolean itemized = !budget.items().isEmpty() || notBlank(budget.note());
        boolean passed = currencyOk && amountOk && itemized;
        return result("budget-reasonableness", passed, 15,
                passed ? "Budget is plausible and explained." : "Budget is missing currency, plausible total, or itemization.");
    }

    private TravelEvalRuleResult checkRisks(TravelPlan plan) {
        boolean passed = plan != null && !plan.risks().isEmpty();
        return result("risk-reminders", passed, 15,
                passed ? "Risk reminders are present." : "Risk reminders are missing.");
    }

    private TravelEvalRuleResult checkUnsafeClaims(TravelPlan plan) {
        String text = searchableText(plan);
        boolean hasUnsafeClaim = containsAny(text, "签证一定通过", "绝对安全", "保证安全", "一定不会", "100%安全", "价格一定", "天气一定");
        return result("unsafe-claims", !hasUnsafeClaim, 20,
                hasUnsafeClaim ? "Unsafe absolute claim detected." : "No obvious unsafe absolute claim detected.");
    }

    private TravelEvalRuleResult checkDisallowedTools(TravelEvalCase evalCase, List<String> observedToolCalls) {
        List<String> normalizedCalls = observedToolCalls == null ? List.of() : observedToolCalls.stream()
                .map(this::normalize)
                .toList();
        boolean found = evalCase.disallowedTools().stream()
                .map(this::normalize)
                .anyMatch(disallowed -> normalizedCalls.stream().anyMatch(call -> call.contains(disallowed)));
        return result("disallowed-tools", !found, 10,
                found ? "Disallowed tool call detected." : "No disallowed tool call detected.");
    }

    private TravelEvalRuleResult checkExpectedSkills(TravelEvalCase evalCase, TravelPlan plan) {
        if (evalCase.expectedSkills().isEmpty()) {
            return pass("expected-skills", 10, "No expected skills required for this case.");
        }
        List<String> loadedSkills = plan == null ? List.of() : plan.loadedSkills();
        boolean passed = loadedSkills.containsAll(evalCase.expectedSkills());
        return result("expected-skills", passed, 10,
                passed ? "Expected skills were loaded." : "Loaded skills do not include all expected skills.");
    }

    private TravelEvalRuleResult pass(String rule, int maxScore, String message) {
        return new TravelEvalRuleResult(rule, true, maxScore, maxScore, message);
    }

    private TravelEvalRuleResult fail(String rule, int maxScore, String message) {
        return new TravelEvalRuleResult(rule, false, 0, maxScore, message);
    }

    private TravelEvalRuleResult result(String rule, boolean passed, int maxScore, String message) {
        return passed ? pass(rule, maxScore, message) : fail(rule, maxScore, message);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String searchableText(TravelPlan plan) {
        if (plan == null) {
            return "";
        }
        return normalize(plan.toString());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
