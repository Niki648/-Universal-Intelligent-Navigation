package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.guardrail.GuardrailResult;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class RequirementCollectorService {

    private static final String CHINESE_NUMBER = "[一二两三四五六七八九十]+";
    private static final Pattern DAYS_PATTERN = Pattern.compile(
            "(\\d+|" + CHINESE_NUMBER + ")\\s*(天|日|晚|day|days)|\\d+\\s*[- ]?day",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAVELERS_PATTERN = Pattern.compile(
            "(\\d+|" + CHINESE_NUMBER + ")\\s*(人|位|个|traveler|travelers|people|persons|adults|kids|children)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(预算|budget|(\\d+|" + CHINESE_NUMBER + ")\\s*(万|元|块|cny|rmb|usd|jpy))",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> DESTINATION_HINTS = List.of(
            "日本", "东京", "大阪", "京都", "成都", "北京", "上海", "广州", "深圳", "杭州", "云南", "新疆",
            "japan", "tokyo", "osaka", "kyoto", "chengdu", "beijing", "shanghai"
    );

    private final GuardrailService guardrailService;

    public RequirementCollectorService(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    public TravelRequirement collect(String message) {
        GuardrailResult guardrailResult = guardrailService.inspectTravelInput(message);
        if (!guardrailResult.allowed()) {
            throw new IllegalArgumentException(guardrailResult.message());
        }
        String normalized = guardrailResult.normalizedInput();
        List<String> missingFields = detectMissingFields(normalized);
        return new TravelRequirement(
                normalized,
                guardrailResult.travelRelated(),
                missingFields,
                detectTaskType(normalized)
        );
    }

    private List<String> detectMissingFields(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        if (DESTINATION_HINTS.stream().noneMatch(lower::contains)) {
            missing.add("destination");
        }
        if (!DAYS_PATTERN.matcher(lower).find()) {
            missing.add("days");
        }
        if (!TRAVELERS_PATTERN.matcher(lower).find()) {
            missing.add("travelers");
        }
        if (!BUDGET_PATTERN.matcher(lower).find()) {
            missing.add("budget");
        }
        return missing;
    }

    private TravelTaskType detectTaskType(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("报告") || lower.contains("report")) {
            return TravelTaskType.REPORT;
        }
        return TravelTaskType.STRUCTURED_PLAN;
    }
}
