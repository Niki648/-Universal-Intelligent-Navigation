# Agentic Travel Backend 面试讲解稿

## 30 秒版本

这个项目原本是一个 Spring AI 旅行聊天系统。我把它升级成了一个垂直旅行规划智能体后端：通过 Skills 按场景加载旅行能力，用结构化 `TravelPlan` 输出稳定承接前端卡片和报告；再用 Eval Harness 防止 Prompt 和 Agent 改动后质量退化，用 Guardrails 控制输入、工具和输出风险，用 Agent Trace 记录智能体执行过程，最后通过轻量 Orchestrator 把需求收集、行程规划、预算估算、风险提醒和报告整理拆成可维护的专家服务。

## 3 分钟版本

项目定位是一个垂直旅行规划智能体系统，后端基于 Spring Boot 3.4、Java 21、Spring AI 和 DeepSeek，支持多轮对话、结构化行程、RAG、工具调用、MCP 和 SSE。

我重点做了五层升级。

第一层是 Skills。以前旅行规划主要依赖一个大 Prompt，后续很难维护。我把家庭旅行、日本旅行、预算旅行、轻松旅行、美食 citywalk 等能力拆成 Markdown Skill，并用 `SkillLoaderService` 根据用户输入动态选择和拼接。

第二层是结构化输出。新增 `TravelPlan` 模型，包含目的地、出发时间、天数、人数、预算、每日行程、交通、住宿、风险、替代方案和已加载 Skills。这样后续前端可以直接做卡片展示，报告生成和评估也有稳定数据结构。

第三层是 Eval Harness。我在 `evals/travel-cases.json` 里配置旅行评估用例，用 `TravelEvalHarness` 从补问缺失信息、结构化行程、预算合理性、风险提醒、绝对化承诺、禁用工具调用和 Skills 加载等维度打分，避免 Prompt 调整后质量退化。

第四层是 Guardrails。`GuardrailService` 负责空请求、超长请求、Prompt Injection、非旅行场景降级；工具侧限制文件路径、终端命令和 URL 下载；输出侧软化“签证一定通过”“绝对安全”这类不负责任表述，并对天气、签证、政策类信息补充不确定性提示。

第五层是 Agent Trace 和轻量编排。`AgentTraceService` 会记录用户意图识别、Skill 加载、RAG 检索、工具调用、行程生成、预算校验、风险检查和报告生成。`TravelOrchestratorService` 用轻量服务组合实现多专家协作：RequirementCollector、ItineraryPlanner、BudgetEstimator、RiskAdvisor、ReportComposer。没有引入重型多 Agent 框架，但保留了清晰边界和扩展空间。

## 8 分钟深挖版本

### 为什么不直接堆 Prompt？

旅行规划是典型的垂直场景，不同用户需求差异很大。家庭旅行关注体力、换酒店次数和医疗便利；日本旅行关注交通、礼仪、季节和签证风险；预算旅行关注费用拆分和不确定性。如果所有规则写进一个大 Prompt，会越来越难维护，也很难测试。

所以我把 Prompt 能力拆成 Skills。每个 Skill 是一个带 front matter 的 `SKILL.md`，包含 id、name、description、tags、triggers 和 priority。服务根据用户输入选择相关 Skills，并把 Skill 内容拼进结构化规划 Prompt。

### 为什么要结构化输出？

聊天文本适合阅读，但不适合工程化承接。旅行规划后续会有卡片展示、预算图表、报告生成、质量评估和 Trace 展示，所以必须有稳定 DTO。

`TravelPlan` 采用 Java record，核心字段包括：

- `summary`
- `destination`
- `departure`
- `days`
- `travelers`
- `budget`
- `itineraryDays`
- `transportation`
- `accommodation`
- `risks`
- `alternatives`
- `loadedSkills`

如果模型结构化解析失败，`TravelPlanService` 会返回降级结构，而不是直接让接口失败。

### Eval Harness 解决什么问题？

大模型应用最大的问题之一是“今天看起来对，明天改了 Prompt 就退化”。所以我加了 deterministic eval。

`TravelEvalHarness` 不依赖另一个模型打分，而是先做规则型检查：

- 是否补问缺失信息。
- 是否有结构化行程。
- 预算总额和币种是否合理。
- 是否包含风险提醒。
- 是否出现“绝对安全”“签证一定通过”等不负责任承诺。
- 是否调用了不该调用的工具。
- 是否加载预期 Skills。

这让后续改 Prompt、改 Skills、改 Orchestrator 时，至少有一条低成本回归线。

### Guardrails 怎么落地？

Guardrails 分三类。

输入侧：检查空请求、超长请求、Prompt Injection 和非旅行场景。非旅行请求不会硬聊，而是降级提示系统专注旅行规划。

工具侧：文件工具只能写入白名单目录且禁止路径穿越；终端工具有 allowlist 和 blacklist；下载工具只允许 http/https，并拦截 localhost 和内网地址。

输出侧：对不负责任表述做软化，例如把“签证一定通过”改成“签证结果需以官方审核为准”。如果涉及天气、签证、政策，也会增加实时/官方确认提醒。

### Trace 有什么价值？

Trace 的目标不是日志替代品，而是面向 Agent 可观测性。后端记录：

- 用户意图识别
- Skill 加载
- RAG 检索
- 工具调用
- 行程生成
- 预算校验
- 风险检查
- 报告生成

目前提供：

- `GET /api/travel/trace/{chatId}`
- `GET /api/travel/trace/{chatId}/stream`

后续前端可以直接展示“正在检索 / 正在规划 / 正在检查预算 / 正在生成报告”。

### 为什么是轻量 Orchestrator？

我没有引入复杂多 Agent 框架，因为当前业务还不需要。更合理的是用 Spring Service 先把专家边界拆出来：

```text
RequirementCollector
-> ItineraryPlanner
-> BudgetEstimator
-> RiskAdvisor
-> ReportComposer
```

这样复杂度可控，测试容易写，后续如果某个专家需要升级成独立 Agent 或异步任务，也有清晰边界。

## 可以继续优化的点

- 把 Eval Runner 做成独立 Maven profile 或 CLI command。
- 把 Agent Trace 持久化到数据库或日志系统。
- 给 `/api/travel/plan` 增加 Mock 模式，方便没有 API Key 的演示环境。
- 前端展示 `TravelPlan` 卡片和 Trace 时间线。
- 将 Guardrails 的规则外置到配置文件。
- 增加更多旅行 Skills 和评估集。
