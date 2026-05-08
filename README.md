# 寰宇智导 · Agentic Travel Planning Backend

> 本项目用于个人学习、求职展示与大模型应用工程实践。

寰宇智导是一个基于 **Spring Boot 3.4 + Java 21 + Spring AI + DeepSeek** 的垂直旅行规划智能体系统。项目从普通旅行聊天系统升级为具备 **Skills、结构化输出、评估 Harness、Guardrails、Agent Trace、轻量多智能体编排** 的 Agentic Travel Planning Backend。

前端使用 Vue 3 + Vite。本轮重点在后端能力建设，前端页面暂未改动。

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
TravelController
  -> TravelMaster
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
│  ├─ app/              # TravelMaster 兼容门面
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

测试与打包说明见：[docs/打包与测试说明.md](docs/打包与测试说明.md)。

## 相关文档

- [Agentic Travel Backend 架构说明](docs/AGENTIC-TRAVEL-BACKEND.md)
- [Agentic Travel Backend 面试讲解稿](docs/INTERVIEW-AGENTIC-TRAVEL-BACKEND.md)
- [MCP 图片搜索服务接入](docs/MCP-SETUP.md)
- [RAG / Agent 深度说明](docs/ARCH-DEEPDIVE-RAG-AGENT.md)
- [面试深挖 Q&A](docs/INTERVIEW-DEEPDIVE-QA.md)
- [项目简历表述](docs/PROJECT-SUMMARY-RESUME.md)
- [安全配置说明](SECURITY.md)

## 安全说明

- 不要把真实 API Key、数据库密码、搜索服务 Key 提交到仓库。
- 工具调用已接入 Guardrails，但真实生产环境仍建议进一步接入鉴权、审计、限流和人工确认。
- 旅行建议中的签证、天气、政策、价格、营业时间等信息需要以官方和实时信息为准。

## License

本项目采用 [MIT License](LICENSE)。
