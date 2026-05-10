# Wayfinder Guild · AI Engineering Portfolio

> 本项目用于个人学习、求职展示与大模型应用工程实践。

Wayfinder Guild 是一个基于 **Spring Boot 3.4 + Java 21 + Spring AI + DeepSeek + Vue 3 + Phaser** 的 AI 工程师作品集与 Agentic AI 能力展示平台。项目以温暖星海旅行小镇为体验入口，展示旅行规划 Agent、SyManus 工具 Agent、RAG、Skills、Eval Harness、Guardrails 与 Agent Trace 等工程能力。

其中旅行规划仍然是核心业务领域，因此代码中的 `TravelPlan`、`TravelRagService` 等领域命名会保留；对外产品品牌统一使用 Wayfinder。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 多轮旅行对话 | 基于 Spring AI ChatClient，支持同步与 SSE 流式对话 |
| 结构化旅行方案 | `POST /api/travel/plan` 输出 `TravelPlan`，便于后续卡片展示和报告生成 |
| Skills 系统 | 通过 `src/main/resources/skills/**/SKILL.md` 按场景加载旅行技能 |
| 轻量多智能体编排 | `RequirementCollector -> ItineraryPlanner -> BudgetEstimator -> RiskAdvisor -> ReportComposer` |
| RAG 知识库问答 | 支持 Markdown 旅行知识文档与 VectorStore 检索增强 |
| 工具调用与 MCP | 支持搜索、抓取、下载、文件、PDF、终端、图片搜索 MCP 等工具能力 |
| Guardrails | 输入、工具、URL、文件路径、终端命令、模型输出安全控制 |
| Agent Trace | 记录意图识别、Skill 加载、检索、规划、预算、风险、报告等关键步骤 |
| Eval Harness | 通过配置化 case 自动评估旅行方案质量，降低 Prompt/Agent 退化风险 |

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4, Java 21, Maven |
| 大模型 | Spring AI, DeepSeek OpenAI-compatible API |
| 对话记忆 | MessageWindowChatMemory, 自定义 FileBasedChatMemory |
| RAG | Spring AI VectorStore, PgVector, Markdown Document Reader |
| Agent | 自研 ReActAgent, ToolCallAgent, SyManus |
| 工具 | Spring AI `@Tool`, Hutool, Jsoup, iText 9 |
| MCP | Spring AI MCP Client, 独立 `sy-image-search-mcp` 子模块 |
| 前端 | Vue 3, Vite, Axios |
| 测试 | JUnit 5, Mockito, Spring Boot Test |

## 后端架构

结构化旅行规划主链路：

```text
WayfinderTravelController
  -> WayfinderTravelFacade
    -> TravelOrchestratorService
      -> RequirementCollectorService
      -> ItineraryPlannerService
        -> TravelPlanService
          -> SkillLoaderService
          -> ChatClient / DeepSeek
      -> BudgetEstimatorService
      -> RiskAdvisorService
      -> ReportComposerService
```

支撑能力：

```text
GuardrailService      输入/工具/输出护栏
AgentTraceService     Agent 执行过程追踪
TravelEvalHarness     旅行规划质量评估
SkillLoaderService    Markdown Skills 加载与选择
```

更完整的设计说明见：[docs/AGENTIC-TRAVEL-BACKEND.md](docs/AGENTIC-TRAVEL-BACKEND.md)。

## 目录结构

```text
sy-ai-agent/
├─ src/main/java/com/seewhy/syaiagent/
│  ├─ controller/       # HTTP 接口
│  ├─ app/              # WayfinderTravelFacade 兼容门面
│  ├─ service/          # Travel Chat / Plan / RAG / Tool / MCP 服务
│  ├─ orchestrator/     # 轻量多智能体编排与专家服务
│  ├─ skill/            # Skill 数据模型与加载服务
│  ├─ guardrail/        # 输入、工具、输出护栏
│  ├─ trace/            # Agent Trace 事件与服务
│  ├─ eval/             # TravelEvalHarness
│  ├─ agent/            # ReActAgent / ToolCallAgent / SyManus
│  ├─ tools/            # 搜索、抓取、下载、文件、PDF、终端等工具
│  └─ rag/              # 文档加载、向量库、查询改写
├─ src/main/resources/
│  ├─ skills/           # 旅行 Skills 配置
│  ├─ document/         # RAG Markdown 文档
│  └─ application.yml
├─ evals/               # 旅行评估样例
├─ docs/                # 架构、测试、MCP、面试说明
├─ frontend/            # Vue 3 + Vite 前端
└─ sy-image-search-mcp/ # 可选图片搜索 MCP 服务
```

## Skills 示例

当前内置旅行 Skills：

- `family-trip-planning`
- `japan-travel`
- `budget-travel`
- `relaxed-travel`
- `food-citywalk`

规范文件：[src/main/resources/skills/SKILL.md](src/main/resources/skills/SKILL.md)

## 主要 API

默认后端地址：`http://localhost:8123/api`

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/travel/chat` | POST | 普通旅行对话 |
| `/travel/chat/stream` | GET | SSE 流式旅行对话 |
| `/travel/plan` | POST | 结构化旅行规划 |
| `/travel/report` | POST | 结构化旅行报告 |
| `/travel/rag` | POST | RAG 知识库问答 |
| `/travel/manus/chat` | GET | SyManus 智能体流式对话 |
| `/travel/trace/{chatId}` | GET | 查询 Agent Trace |
| `/travel/trace/{chatId}/stream` | GET | SSE Trace 事件流 |
| `/travel/health` | GET | 健康检查 |

### 结构化旅行规划示例

请求：

```http
POST /api/travel/plan
Content-Type: application/json
```

```json
{
  "message": "我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点",
  "chatId": "demo-japan-family"
}
```

期望响应结构：

```json
{
  "summary": "...",
  "destination": "日本",
  "departure": "6月",
  "days": 7,
  "travelers": 3,
  "budget": {
    "total": 20000,
    "currency": "CNY",
    "items": []
  },
  "itineraryDays": [],
  "transportation": [],
  "accommodation": [],
  "risks": [],
  "alternatives": [],
  "loadedSkills": [
    "family-trip-planning",
    "japan-travel",
    "relaxed-travel",
    "budget-travel"
  ]
}
```

Trace 验证：

```http
GET /api/travel/trace/demo-japan-family
```

## 本地运行

### 环境要求

- JDK 21
- Maven 3.6+
- Node.js 18+，仅前端需要

### 配置

敏感信息不要提交到 Git。推荐使用环境变量：

```bash
DEEPSEEK_API_KEY=your_key
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_CHAT_MODEL=deepseek-chat
SEARCH_API_KEY=your_search_key
DB_URL=jdbc:postgresql://localhost:5432/your_db
DB_USERNAME=postgres
DB_PASSWORD=your_password
```

也可以复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml`，本地填写配置。`application-local.yml` 已在 `.gitignore` 中排除。

### 启动后端

```bash
mvn spring-boot:run
```

访问：

- 健康检查：`http://localhost:8123/api/travel/health`
- Swagger：`http://localhost:8123/api/swagger-ui.html`
- Knife4j：`http://localhost:8123/api/doc.html`

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 测试

默认快速测试：

```bash
mvn test
```

包含 integration 测试：

```bash
mvn test -P with-integration-tests
```

运行 Eval Runner，默认会触发模型调用，请确保 DeepSeek 配置可用：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--travel.eval.enabled=true
```

测试与发布验证说明见：[docs/VERIFY.md](docs/VERIFY.md) 与 [docs/TEST-REPORT-WAYFINDER.md](docs/TEST-REPORT-WAYFINDER.md)。

## 相关文档

- [Agentic Travel Backend 架构说明](docs/AGENTIC-TRAVEL-BACKEND.md)
- [Wayfinder Guild PRD](docs/PRD-WAYFINDER-GUILD.md)
- [Wayfinder 技术设计](docs/TECH-DESIGN-WAYFINDER.md)
- [部署指南](docs/DEPLOYMENT.md)
- [RAG 成本策略](docs/RAG-COST-STRATEGY.md)
- [命名规范](docs/NAMING-GUIDE.md)
- [命名审计](docs/NAMING-AUDIT.md)
- [安全配置说明](SECURITY.md)

## 安全说明

- 不要把真实 API Key、数据库密码、搜索服务 Key 提交到仓库。
- 工具调用已接入 Guardrails，但真实生产环境仍建议进一步接入鉴权、审计、限流和人工确认。
- 旅行建议中的签证、天气、政策、价格、营业时间等信息需要以官方和实时信息为准。

## License

本项目采用 [MIT License](LICENSE)。

## Wayfinder Guild Launch Docs

Wayfinder Guild is the production-ready portfolio layer of this project: a warm cosmic travel RPG site for demonstrating Agent, RAG, Skills, Eval, Trace, Guardrails, and backend architecture.

### RAG cost-control modes

Public production does not require PgVector or a cloud database. Configure RAG with:

```env
TRAVEL_RAG_MODE=demo
```

Supported values:

- `demo` - stable public RAG explain response with no database cost.
- `lightweight` - searches local `src/main/resources/document/*.md` snippets.
- `pgvector` - optional Owner Live / local deep demo mode using PgVector `VectorStore`.

If `pgvector` is selected but VectorStore is unavailable, the API degrades to lightweight Markdown retrieval and does not fail the request with a 500.

Useful launch and interview documents:

- [Production Deployment Guide](docs/DEPLOYMENT.md)
- [Verification Guide](docs/VERIFY.md)
- [Wayfinder PRD](docs/PRD-WAYFINDER-GUILD.md)
- [Wayfinder Technical Design](docs/TECH-DESIGN-WAYFINDER.md)
- [Wayfinder Test Report](docs/TEST-REPORT-WAYFINDER.md)
- [Release Notes v0.1.0](docs/RELEASE-NOTES-v0.1.0.md)
- [RAG Cost Strategy](docs/RAG-COST-STRATEGY.md)
- [Agentic Travel Backend Notes](docs/AGENTIC-TRAVEL-BACKEND.md)

Release governance and launch review:

- PRD: target users, scope, non-goals, acceptance criteria, Demo Mode / Owner Live Mode strategy
- Technical design: frontend, backend, Agentic Travel, RPG metadata, Rust CLI, security, degradation, deployment topology
- Test report: automated verification, manual smoke route, and known risks
- Release notes: v0.1.0 content, config, rollback, and post-launch checks
- Gray release plan: local acceptance, staging, small-scope access, public launch, metrics, and rollback conditions

## Wayfinder CLI

`tools/wayfinder-cli` is a Rust developer toolchain for checking Wayfinder Guild metadata before demos, deployments, and release candidates.

Commands:

- `doctor` - run all static checks and print a resource summary
- `lint-skills` - validate `src/main/resources/skills/**/SKILL.md`
- `lint-rpg` - validate `src/main/resources/rpg/*.json`
- `lint-evals` - validate `evals/travel-cases.json`
- `lint-prompts` - validate `src/main/resources/prompts/rpg/*.st`
- `lint-naming` - validate Wayfinder Guild naming governance
- `summary` - print counts for Skills, RPG areas/NPCs/projects/modules, eval cases, and prompt templates

Example:

```powershell
cd tools/wayfinder-cli
cargo test
cargo run -- doctor --workspace ..\..
cargo run -- lint-naming --workspace ..\..
```

If PowerShell has not picked up Rust in `PATH`, use:

```powershell
C:\Users\cycle\.cargo\bin\cargo.exe run -- doctor --workspace ..\..
```
