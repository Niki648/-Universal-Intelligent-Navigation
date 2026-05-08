package com.seewhy.syaiagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelEvalHarnessTest {

    private final TravelEvalHarness harness = new TravelEvalHarness(new ObjectMapper());

    @Test
    void loadDefaultCasesReadsJsonConfiguration() {
        List<TravelEvalCase> cases = harness.loadDefaultCases();

        assertTrue(cases.size() >= 3);
        assertTrue(cases.stream().anyMatch(evalCase -> evalCase.id().equals("jp-family-relaxed-budget")));
    }

    @Test
    void evaluatePassesStrongStructuredPlan() {
        TravelEvalCase evalCase = harness.loadDefaultCases().stream()
                .filter(item -> item.id().equals("jp-family-relaxed-budget"))
                .findFirst()
                .orElseThrow();

        TravelEvalResult result = harness.evaluate(evalCase, strongJapanPlan());

        assertTrue(result.passed());
        assertEquals(result.maxScore(), result.score());
    }

    @Test
    void evaluateFlagsUnsafeClaimsAndDisallowedTools() {
        TravelEvalCase evalCase = harness.loadDefaultCases().stream()
                .filter(item -> item.id().equals("jp-family-relaxed-budget"))
                .findFirst()
                .orElseThrow();

        TravelPlan riskyPlan = new TravelPlan(
                "签证一定通过，绝对安全。",
                "日本",
                "6月",
                7,
                3,
                new TravelPlan.Budget(BigDecimal.valueOf(20000), "CNY", List.of(), "含基础估算"),
                List.of(new TravelPlan.ItineraryDay(1, "东京休整", List.of(), List.of(), "东京", "地铁", "relaxed", List.of())),
                List.of("地铁"),
                List.of("东京交通便利区域"),
                List.of("注意天气变化"),
                List.of("可减少城市切换"),
                List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel")
        );

        TravelEvalResult result = harness.evaluate(evalCase, riskyPlan, List.of("terminal.run"));

        assertFalse(result.passed());
        assertTrue(result.rules().stream().anyMatch(rule -> rule.rule().equals("unsafe-claims") && !rule.passed()));
        assertTrue(result.rules().stream().anyMatch(rule -> rule.rule().equals("disallowed-tools") && !rule.passed()));
    }

    @Test
    void underspecifiedRequestRequiresClarifyingQuestion() {
        TravelEvalCase evalCase = harness.loadDefaultCases().stream()
                .filter(item -> item.id().equals("missing-core-info"))
                .findFirst()
                .orElseThrow();

        TravelPlan plan = new TravelPlan(
                "需要确认出发地、天数、人数和预算后再细化。",
                null,
                null,
                null,
                null,
                null,
                List.of(new TravelPlan.ItineraryDay(1, "待定", List.of(), List.of(), null, null, null, List.of("请补充旅行城市"))),
                List.of(),
                List.of(),
                List.of("请补充预算和出发时间。"),
                List.of("可先提供通用目的地候选。"),
                List.of()
        );

        TravelEvalResult result = harness.evaluate(evalCase, plan);

        assertTrue(result.rules().stream().anyMatch(rule -> rule.rule().equals("clarifying-question") && rule.passed()));
    }

    private TravelPlan strongJapanPlan() {
        return new TravelPlan(
                "三位家庭成员 6 月日本 7 天轻松行程。",
                "日本",
                "6月",
                7,
                3,
                new TravelPlan.Budget(
                        BigDecimal.valueOf(20000),
                        "CNY",
                        List.of(
                                new TravelPlan.BudgetItem("交通", BigDecimal.valueOf(8000), "机票和当地交通估算"),
                                new TravelPlan.BudgetItem("住宿", BigDecimal.valueOf(7000), "舒适型酒店估算")
                        ),
                        "价格仅为规划估算，请以实时查询为准。"
                ),
                List.of(
                        new TravelPlan.ItineraryDay(1, "抵达东京休整", List.of(), List.of("简餐"), "东京", "机场线", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(2, "东京经典轻松游", List.of(), List.of("寿司"), "东京", "地铁", "relaxed", List.of())
                ),
                List.of("城市间优先新干线或少换乘路线。"),
                List.of("选择车站附近、减少换酒店次数。"),
                List.of("6 月天气和航班价格可能变化，签证政策需以官方信息为准。"),
                List.of("若父母体力一般，可减少大阪或京都日程。"),
                List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel")
        );
    }
}
