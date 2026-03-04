# 寰宇智导 · AI 旅行规划与智能体系统

基于 **Spring AI** 与 **阿里云百炼/通义** 大模型的智能旅行规划系统，提供多轮对话规划、RAG 知识库问答、结构化报告生成，以及具备自主规划能力的 **SyManus 超级智能体**（搜索、网页抓取、PDF 生成、文件读写等工具调用）。支持 **MCP（Model Context Protocol）** 扩展：可接入自研图片搜索 MCP 服务与高德地图 MCP 等，按需启用。

---

## 一、项目简介与亮点

本项目将大模型对话、RAG 检索增强、ReAct 式工具调用整合在同一套后端中，适合作为 **Spring Boot + Spring AI + 大模型应用** 的求职/毕设项目展示：

- **寰宇智导（TravelMaster）**：专业旅行规划对话，分阶段引导收集需求，支持多轮记忆、流式输出、结构化旅行报告与知识库问答。
- **SyManus 智能体**：基于 ReAct（Reasoning and Acting）的通用助手，可自主选择并组合多种工具完成复杂任务（如搜索 → 抓取 → 生成 PDF）。
- **技术栈覆盖**：Spring Boot 3、Spring AI、大模型 API 集成、向量检索（RAG）、SSE 流式响应、Vue 3 前端、PDF 生成（iText、中文字体支持）、**MCP 客户端与自研 MCP 服务**等。

---

## 二、技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4、Java 21 |
| AI / 大模型 | Spring AI、阿里云百炼 DashScope（通义千问） |
| 对话与记忆 | Spring AI ChatClient、MessageWindowChatMemory |
| RAG | Spring AI VectorStore、Markdown 文档加载、关键词增强 |
| 智能体 | 自研 ReAct 循环、ToolCallAgent、SyManus |
| 文档与 PDF | iText 9（itext-core、font-asian 中文字体） |
| 前端 | Vue 3、Vue Router、Vite、Axios |
| MCP | Spring AI MCP Client、自研 sy-image-search-mcp（Pexels 图片搜索）、mcp-servers.json（stdio/SSE） |
| 其他 | Knife4j/OpenAPI 3、Hutool、Jsoup |

---

## 三、功能概览

### 3.1 寰宇智导（旅行规划）

- **多轮对话规划**：分阶段询问目的地、时间、预算、偏好，再给出行程建议与预算分配。
- **流式输出**：支持 SSE 流式接口，前端可逐字展示回复。
- **结构化报告**：根据对话生成旅行规划报告（标题、建议、目的地、时长、预算、行程框架）。
- **RAG 知识库问答**：基于本地 Markdown 旅行文档（如热门目的地、预算建议）做检索增强回答。

### 3.2 SyManus 超级智能体

- **ReAct 式推理**：先思考再行动，按步骤选择工具并执行，最多 10 步内完成目标。
- **可用工具**：WebSearchTool、WebScrapingTool、ResourceDownloadTool、FileOperationTool、PDFGenerationTool、TerminalOperationTool、TerminateTool。

### 3.3 MCP 服务支持

主应用通过 **Spring AI MCP Client** 可按需接入外部 MCP 服务，扩展能力而不改主代码：

| MCP 服务 | 说明 | 配置方式 |
|----------|------|----------|
| **sy-image-search-mcp** | 自研图片搜索（Pexels API），供 AI 调用「搜索图片」能力 | SSE：先启动子服务（端口 8127）；或 stdio：由主应用按 `mcp-servers.json` 拉起 jar |
| **amap-maps** | 高德地图 MCP（需 Node/npx），提供地图相关能力 | 在 `mcp-servers.json` 中配置，启动前设置环境变量 `AMAP_MAPS_API_KEY` |

- 主应用默认 **未启用** MCP 客户端；启用方式见 [docs/MCP-SETUP.md](docs/MCP-SETUP.md)。
- 配置文件：`src/main/resources/mcp-servers.json`（stdio 模式下的服务列表）。

### 3.4 主要 API 一览

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/travel/chat` | POST | 旅行对话（同步） |
| `/api/travel/chat/stream` | GET | 旅行对话（SSE 流式） |
| `/api/travel/manus/chat` | GET | SyManus 智能体对话（SSE 流式） |
| `/api/travel/report` | POST | 生成旅行规划报告 |
| `/api/travel/rag` | POST | 旅行知识库 RAG 问答 |
| `/api/travel/quick` | POST | 快速旅行咨询 |
| `/api/travel/health` | GET | 健康检查 |

API 文档：`http://localhost:8123/api/swagger-ui.html`

---

## 四、项目结构（核心）

```
sy-ai-agent/
├── src/main/java/com/seewhy/syaiagent/
│   ├── controller/TravelController.java    # 旅行/智能体 HTTP 接口
│   ├── app/TravelMaster.java               # 寰宇智导：对话、报告、RAG
│   ├── agent/ (ReActAgent, ToolCallAgent, SyManus)
│   ├── tools/ (WebSearch, WebScraping, PDF, File, Download, Terminal, Terminate)
│   └── rag/ (DocumentLoader, VectorStore, QueryRewriter)
├── src/main/resources/
│   ├── application.yml                     # 主配置（含可选 MCP 客户端配置）
│   ├── mcp-servers.json                    # MCP stdio 模式服务列表
│   ├── application-local.yml.example       # 本地配置示例
│   ├── application-local.yml               # 本地真实配置（已 gitignore）
│   └── document/ (travel_guide.md, budget_tips.md)
├── sy-image-search-mcp/                   # 自研 MCP 服务：Pexels 图片搜索
│   ├── src/.../tools/ImageSearchTool.java
│   └── application.yml、application-local.yml.example
├── frontend/ (Vue 3, TravelChat, ManusChat)
├── docs/
│   ├── MCP-SETUP.md                        # MCP 配置与检测说明
│   └── screenshots/
└── README.md
```

---

## 五、快速开始

### 环境要求

JDK 21、Maven 3.6+、Node.js 18+（前端）

### 配置敏感信息

**方式一（推荐本地）**：复制 `application-local.yml.example` 为 `application-local.yml`，填入数据库、DASHSCOPE_API_KEY、SEARCH_API_KEY。

**方式二**：设置环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`DASHSCOPE_API_KEY`、`SEARCH_API_KEY`。详见 [SECURITY.md](SECURITY.md)。

### 启动

```bash
# 后端
mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

默认后端端口 8123，上下文 `/api`。健康检查：`GET http://localhost:8123/api/travel/health`。

**可选：启用 MCP 服务**

- 若使用 **SSE 模式**（推荐）：先单独启动图片搜索 MCP，再在 `application.yml` 中取消注释 MCP SSE 连接配置。
- 若使用 **stdio 模式**：先构建 `sy-image-search-mcp` 的 jar，再在 `application.yml` 中取消注释 MCP stdio 与 `mcp-servers.json` 配置。

详细步骤、环境变量（如 `PEXELS_API_KEY`、`AMAP_MAPS_API_KEY`）见 [docs/MCP-SETUP.md](docs/MCP-SETUP.md)。

---

## 六、运行效果与截图

将截图放入 `docs/screenshots/`，命名见 [docs/screenshots/README.md](docs/screenshots/README.md)。在 README 中取消对应图片行的注释即可展示。

<!-- ![旅行规划对话](docs/screenshots/travel-chat.png) -->
<!-- ![旅行报告](docs/screenshots/travel-report.png) -->
<!-- ![RAG 问答](docs/screenshots/rag-qa.png) -->
<!-- ![SyManus 工具调用](docs/screenshots/manus-tools.png) -->
<!-- ![PDF 中文](docs/screenshots/pdf-chinese.png) -->

---

## 七、相关文档

- [SECURITY.md](SECURITY.md)：敏感信息配置说明。
- [docs/MCP-SETUP.md](docs/MCP-SETUP.md)：MCP 服务配置、启动方式与检测说明。
- `src/main/resources/fonts/README.txt`：PDF 中文字体配置。

---

本项目仅供学习与求职展示使用。使用阿里云百炼/通义等 API 请遵守其服务条款。
