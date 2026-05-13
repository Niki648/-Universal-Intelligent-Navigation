package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.RagRetrievedDocument;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DemoRagExplainResponses {

    private static final Map<String, DemoDoc> DOCS = Map.of(
            "japan-family-trip", new DemoDoc(
                    "japan-family-trip",
                    "日本家庭旅行轻松节奏规划",
                    "classpath:document/japan-family-trip.md",
                    "家庭日本行优先少换酒店、少跨城、住在交通节点附近；每天设置一个主目标和一个可取消的轻量活动，预算需要保留弹性。",
                    List.of("日本", "家庭旅行", "轻松", "住宿")
            ),
            "budget-travel-planning", new DemoDoc(
                    "budget-travel-planning",
                    "低预算旅行规划与成本控制",
                    "classpath:document/budget-travel-planning.md",
                    "预算先定上限，再拆成必须花、想体验、可取消和应急金；住宿、交通和餐饮都要按体验价值和变更能力一起判断。",
                    List.of("预算", "住宿", "交通", "餐饮")
            ),
            "family-travel-risk-checklist", new DemoDoc(
                    "family-travel-risk-checklist",
                    "家庭旅行风险核对清单",
                    "classpath:document/family-travel-risk-checklist.md",
                    "家庭旅行风险可以拆成健康、证件、交通、住宿、沟通和财务；老人小孩要额外关注药品、保险、走散和临时就医预案。",
                    List.of("家庭旅行", "老人", "小孩", "风险")
            ),
            "japan-visa-and-entry", new DemoDoc(
                    "japan-visa-and-entry",
                    "日本签证与入境核验提醒",
                    "classpath:document/japan-visa-and-entry.md",
                    "签证、入境材料、护照有效期和政策口径可能变化；出行前应以官方渠道和服务方实时信息为准。",
                    List.of("日本", "签证", "入境", "核验")
            ),
            "japan-transport-pass", new DemoDoc(
                    "japan-transport-pass",
                    "日本交通券与 JR Pass 选择思路",
                    "classpath:document/japan-transport-pass.md",
                    "交通券选择先看路线，而不是先看券名；长距离新干线次数、使用天数和线路覆盖决定 JR Pass 或区域 Pass 是否值得。",
                    List.of("日本", "JR Pass", "交通券", "IC卡")
            ),
            "rainy-day-backup-plan", new DemoDoc(
                    "rainy-day-backup-plan",
                    "雨天旅行备选方案设计",
                    "classpath:document/rainy-day-backup-plan.md",
                    "雨天备选应提前准备室内文化场馆、商业综合体或地下街、轻松餐饮休息点，并减少换乘和长距离步行。",
                    List.of("雨天", "备选方案", "室内活动", "天气")
            ),
            "elderly-and-child-friendly-travel", new DemoDoc(
                    "elderly-and-child-friendly-travel",
                    "老人小孩友好旅行节奏",
                    "classpath:document/elderly-and-child-friendly-travel.md",
                    "老人和小孩同行时，应降低步行强度、留出午休和早回酒店窗口，并优先选择电梯、无障碍和餐饮便利的住宿。",
                    List.of("老人", "小孩", "节奏", "住宿")
            ),
            "travel-safety-and-insurance", new DemoDoc(
                    "travel-safety-and-insurance",
                    "旅行安全与保险提醒",
                    "classpath:document/travel-safety-and-insurance.md",
                    "保险不等于没有风险；需要提前确认保障范围、除外责任、理赔材料、医疗网络和紧急联系人。",
                    List.of("安全", "保险", "医疗", "应急")
            )
    );

    private DemoRagExplainResponses() {
    }

    static RagExplainResponse build(String originalQuery, String chatId) {
        String query = originalQuery == null || originalQuery.isBlank()
                ? "What should I consider for a relaxed family trip to Japan?"
                : originalQuery.strip();
        String id = chatId == null || chatId.isBlank() ? "demo-rag-local" : chatId;
        boolean chinese = containsChinese(query);
        DemoIntent intent = detectIntent(query);
        List<RagRetrievedDocument> documents = documentsFor(intent);
        return new RagExplainResponse(
                id,
                "demo",
                query,
                rewriteFor(intent, chinese),
                documents,
                answerFor(intent, chinese),
                false,
                null
        );
    }

    private static DemoIntent detectIntent(String query) {
        String normalized = normalize(query);
        if (containsAny(normalized, "jr pass", "交通券", "通票", "ic卡", "ic card", "rail pass", "pass")) {
            return DemoIntent.TRANSPORT_PASS;
        }
        if (containsAny(normalized, "下雨", "雨天", "rain", "weather", "备选", "indoor")) {
            return DemoIntent.RAINY_DAY;
        }
        if (containsAny(normalized, "老人", "小孩", "孩子", "风险", "保险", "药品", "走散", "elderly", "child", "children", "risk", "insurance")) {
            return DemoIntent.FAMILY_RISK;
        }
        if (containsAny(normalized, "低预算", "控制住宿", "控制", "餐饮", "成本", "budget", "low budget", "cost", "food")) {
            return DemoIntent.BUDGET;
        }
        if (containsAny(normalized, "日本", "japan", "父母", "parents", "轻松", "relaxed", "7天", "7 day", "7 days")) {
            return DemoIntent.JAPAN_FAMILY;
        }
        return DemoIntent.GENERAL;
    }

    private static List<RagRetrievedDocument> documentsFor(DemoIntent intent) {
        return switch (intent) {
            case JAPAN_FAMILY -> toRetrieved(List.of(
                    scored("japan-family-trip", 0.94),
                    scored("budget-travel-planning", 0.88),
                    scored("family-travel-risk-checklist", 0.84),
                    scored("japan-visa-and-entry", 0.78)
            ));
            case TRANSPORT_PASS -> toRetrieved(List.of(
                    scored("japan-transport-pass", 0.95),
                    scored("budget-travel-planning", 0.82),
                    scored("japan-family-trip", 0.76)
            ));
            case RAINY_DAY -> toRetrieved(List.of(
                    scored("rainy-day-backup-plan", 0.95),
                    scored("japan-family-trip", 0.82),
                    scored("family-travel-risk-checklist", 0.77)
            ));
            case BUDGET -> toRetrieved(List.of(
                    scored("budget-travel-planning", 0.95),
                    scored("japan-transport-pass", 0.80),
                    scored("japan-family-trip", 0.76)
            ));
            case FAMILY_RISK -> toRetrieved(List.of(
                    scored("family-travel-risk-checklist", 0.96),
                    scored("elderly-and-child-friendly-travel", 0.88),
                    scored("travel-safety-and-insurance", 0.84),
                    scored("rainy-day-backup-plan", 0.74)
            ));
            case GENERAL -> toRetrieved(List.of(
                    scored("japan-family-trip", 0.82),
                    scored("budget-travel-planning", 0.78),
                    scored("family-travel-risk-checklist", 0.74)
            ));
        };
    }

    private static String rewriteFor(DemoIntent intent, boolean chinese) {
        if (!chinese) {
            return switch (intent) {
                case JAPAN_FAMILY -> "Japan June 7 days 3 travelers parents family relaxed trip 20000 CNY budget lodging transport risks";
                case TRANSPORT_PASS -> "Japan transport pass JR Pass regional pass IC card intercity transit local transit route coverage value";
                case RAINY_DAY -> "Japan rainy day backup plan indoor activities museums malls aquarium transport adjustment family travel";
                case BUDGET -> "low budget travel lodging transport food cost breakdown contingency value refundable booking";
                case FAMILY_RISK -> "elderly children family travel risks health documents transport lodging insurance medicine emergency lost contact";
                case GENERAL -> "travel knowledge base itinerary budget transport lodging risk reminders";
            };
        }
        return switch (intent) {
            case JAPAN_FAMILY -> "日本 6月 7天 3人 父母 家庭 轻松旅行 20000 CNY 预算 住宿 交通 风险";
            case TRANSPORT_PASS -> "日本 交通券 JR Pass 区域 Pass IC卡 城际交通 市内交通 是否划算 路线覆盖";
            case RAINY_DAY -> "日本 下雨天 雨天 备选方案 室内活动 博物馆 商场 水族馆 交通调整 家庭旅行";
            case BUDGET -> "低预算 旅行 住宿 交通 餐饮 成本拆分 应急金 性价比 可取消";
            case FAMILY_RISK -> "老人 小孩 家庭旅行 风险 健康 证件 交通 住宿 保险 药品 应急 走散";
            case GENERAL -> "旅行知识库 行程 预算 交通 住宿 风险提醒";
        };
    }

    private static String answerFor(DemoIntent intent, boolean chinese) {
        if (!chinese) {
            return switch (intent) {
                case JAPAN_FAMILY -> "Keep the 7-day Japan family trip to 1-2 cities, reduce hotel changes and long transfers, and stay near transit. Split the budget into long-distance transport, lodging, local transport, food, tickets, and contingency. Demo Mode uses fixed local Markdown snippets; verify live prices, visa rules, weather, transport schedules, and opening hours before booking.";
                case TRANSPORT_PASS -> "Choose transport passes from the route, not from the pass name. JR Pass value depends on long-distance rail frequency, travel days, and covered lines; for mostly city travel, IC cards or a small regional pass may be more flexible. For parents or children, convenience and fewer transfers can matter more than small savings. Check current fare rules before buying.";
                case RAINY_DAY -> "Prepare rainy-day backups before the trip: indoor culture venues, malls or underground streets, and easy meal or rest stops. Reduce transfers and walking when it rains, keep one main activity, and classify outdoor spots by whether they still work in wet weather. Verify live weather, transport status, reservations, and venue opening hours.";
                case BUDGET -> "Set a budget ceiling first, then split it into must-pay items, optional experiences, cancellable items, and contingency. Do not choose lodging by price alone; include transport time, cancellation policy, room fit, and safety. Reduce city changes, combine nearby sights, and use a mix of convenience stores, local shops, markets, and a few special meals. Real prices still need current checks.";
                case FAMILY_RISK -> "For elderly travelers and children, check six risk areas: health, documents, transport, lodging, communication, and money. Prepare medicines, prescriptions, insurance details, document copies, emergency contacts, and a simple lost-contact plan. Avoid late arrivals and frequent transfers; choose lodging with elevators, accessible routes, and nearby pharmacies or clinics. Verify medical, insurance, and transport information before travel.";
                case GENERAL -> "This demo searches fixed local Markdown snippets and returns a grounded summary for the closest travel-planning topic. Use the retrieved sources below as the explanation path, and verify live policies, prices, weather, and opening hours before booking.";
            };
        }
        return switch (intent) {
            case JAPAN_FAMILY -> "建议把 7 天控制在 1-2 个城市，减少换酒店和长距离移动；住宿尽量靠近交通，给父母预留午休或早回酒店的窗口。预算按长途交通、住宿、当地交通、餐饮、门票和应急金拆分。Demo Mode 使用固定本地 Markdown 片段展示 RAG 解释链；实际价格、签证规则、天气、交通时刻和景点开放时间请在预订前实时核验。";
            case TRANSPORT_PASS -> "交通券先看路线，不是先看券名。JR Pass 是否划算主要取决于长距离新干线次数、使用天数和覆盖线路；如果主要是市内移动，IC 卡或少量区域 Pass 往往更灵活。带父母孩子时，少换乘、少拖行李和可预约座位也要计入价值判断。票价和规则会变化，购买前请核验官方信息。";
            case RAINY_DAY -> "雨天备选要提前做：每个城市准备室内文化场馆、商业综合体或地下街、轻松餐饮休息点。下雨时减少换乘和步行，保留一个主活动即可，并把户外景点分成“雨中可接受”和“不适合雨中进行”。实时天气、交通运行、预约规则和场馆开放时间请以官方或当日信息为准。";
            case BUDGET -> "先定预算上限，再拆成必须花、想体验、可取消和应急金。住宿不要只看低价，还要看交通时间、取消政策、房间条件和同行人是否合适；交通上减少跨城和重复往返通常比研究复杂通票更有效。餐饮可以用便利店、本地小店、市场轻食和一两顿特色餐组合控制成本。机票、酒店、汇率和门票价格请实时核验。";
            case FAMILY_RISK -> "带老人小孩旅行，建议按健康、证件、交通、住宿、沟通、财务和应急来核对。出发前准备常用药、处方、保险信息、证件复印件和紧急联系人；交通上避免深夜抵达、过短换乘和频繁拖行李转站；住宿重点看电梯、无障碍、床型、洗衣、附近药店和医院。再约定走散集合点、手机没电方案和不舒服时的就医预案。实时医疗、保险、交通和天气信息请以官方或服务方渠道为准。";
            case GENERAL -> "Demo Mode 会在固定本地 Markdown 片段里做确定性检索，并把最相关的文档、改写查询和简短回答展示出来。请把下面的来源当作可解释链路；实时政策、价格、天气和开放时间仍需预订前核验。";
        };
    }

    private static List<RagRetrievedDocument> toRetrieved(List<ScoredDoc> scoredDocs) {
        return scoredDocs.stream()
                .map(scored -> {
                    DemoDoc doc = DOCS.get(scored.documentId());
                    return new RagRetrievedDocument(
                            doc.title(),
                            doc.source(),
                            doc.snippet(),
                            scored.score(),
                            doc.id(),
                            doc.tags(),
                            "2026-05-10",
                            "curated-demo-markdown"
                    );
                })
                .toList();
    }

    private static ScoredDoc scored(String documentId, double score) {
        return new ScoredDoc(documentId, score);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip().replace(" ", "");
    }

    private static boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private enum DemoIntent {
        JAPAN_FAMILY,
        TRANSPORT_PASS,
        RAINY_DAY,
        BUDGET,
        FAMILY_RISK,
        GENERAL
    }

    private record DemoDoc(String id, String title, String source, String snippet, List<String> tags) {
    }

    private record ScoredDoc(String documentId, double score) {
    }
}
