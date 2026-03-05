# 寰宇智导 · AI 旅行规划与智能体系统

> **说明**：本项目仅作个人学习与求职展示使用。

基于 **Spring AI** 与 **阿里云百炼/通义** 大模型的智能旅行规划系统，提供多轮对话规划、RAG 知识库问答、结构化报告生成，以及具备自主规划能力的 **SyManus 超级智能体**（搜索、图片搜索、网页抓取、PDF 生成、文件读写等工具调用）。支持 **MCP（Model Context Protocol）** 扩展，可选接入自研 **sy-image-search-mcp** 图片搜索服务（Pexels API）。

---

## 项目简介

基于 **Spring Boot 3 + Spring AI** 的智能旅行规划与 AI 智能体系统：集成大模型多轮对话、**RAG 检索增强**、**ReAct 式工具调用**（搜索/抓取/PDF/文件等），支持 SSE 流式输出与结构化报告生成，前端为 Vue 3 + Vite。

### 核心亮点

| 模块 | 说明 |
|------|------|
| **寰宇智导（TravelMaster）** | 专业旅行规划对话，分阶段引导收集需求，支持多轮记忆、流式输出、结构化旅行报告与知识库问答。 |
| **SyManus 智能体** | 基于 ReAct（Reasoning and Acting）的通用助手，可自主选择并组合多种工具完成复杂任务（如搜索 → 抓取 → 生成 PDF）。 |
| **技术栈** | Spring Boot 3、Spring AI、大模型 API、向量检索（RAG）、MCP 扩展（sy-image-search-mcp）、SSE 流式、Vue 3 前端、iText PDF（含中文字体）等。 |

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4、Java 21 |
| AI / 大模型 | Spring AI、阿里云百炼 DashScope（通义千问） |
| 对话与记忆 | Spring AI ChatClient、MessageWindowChatMemory、Kryo 持久化 |
| RAG | Spring AI VectorStore、Markdown 文档加载、关键词增强 |
| 智能体 | 自研 ReAct 循环、ToolCallAgent、SyManus |
| MCP 扩展 | Spring AI MCP Client、**sy-image-search-mcp**（Pexels 图片搜索，SSE/stdio 双模式） |
| 文档与 PDF | iText 9（itext-core、font-asian 中文字体） |
| 前端 | Vue 3、Vue Router、Vite、Axios |
| 其他 | Knife4j/OpenAPI 3、Hutool、Jsoup |

---

## 功能概览

### 寰宇智导（旅行规划）

- **多轮对话规划**：分阶段询问目的地、时间、预算、偏好，再给出行程建议与预算分配。
- **流式输出**：支持 SSE 流式接口，前端可逐字展示回复。
- **结构化报告**：根据对话生成旅行规划报告（标题、建议、目的地、时长、预算、行程框架）。
- **RAG 知识库问答**：基于本地 Markdown 旅行文档做检索增强回答。

### SyManus 超级智能体

- **ReAct 式推理**：先思考再行动，按步骤选择工具并执行，最多 10 步内完成目标。
- **可用工具**：WebSearchTool、**ImageSearchTool**（可对接 **sy-image-search-mcp** 使用 Pexels 图片搜索）、WebScrapingTool、ResourceDownloadTool、FileOperationTool、PDFGenerationTool、TerminalOperationTool、TerminateTool。

### sy-image-search-mcp（可选 MCP 服务）

- **定位**：独立 MCP 服务子模块，为 SyManus 提供**图片搜索**能力（基于 Pexels API）。
- **运行方式**：支持 **SSE**（独立进程，主应用 HTTP 连接）与 **stdio**（主应用按配置拉起子进程）两种模式。
- **技术**：Spring Boot、Spring AI MCP Server、端口 8127（SSE 模式）。启用方式与 Pexels API Key 配置见 [docs/MCP-SETUP.md](docs/MCP-SETUP.md)。

### 主要 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/travel/chat` | POST | 旅行对话（同步） |
| `/api/travel/chat/stream` | GET | 旅行对话（SSE 流式） |
| `/api/travel/manus/chat` | GET | SyManus 智能体对话（SSE 流式） |
| `/api/travel/report` | POST | 生成旅行规划报告 |
| `/api/travel/rag` | POST | 旅行知识库 RAG 问答 |
| `/api/travel/quick` | POST | 快速旅行咨询 |
| `/api/travel/health` | GET | 健康检查 |

- **API 文档（Swagger/Knife4j）**：启动后访问 `http://localhost:8123/api/doc.html` 或 `http://localhost:8123/api/swagger-ui.html`。
- **（可选）启用 MCP 图片搜索**：需单独构建并启动 **sy-image-search-mcp**，并在主应用 `application.yml` 中启用 MCP 客户端。详见 [docs/MCP-SETUP.md](docs/MCP-SETUP.md)。

---

## 项目结构

```
sy-ai-agent/
├── src/main/java/com/seewhy/syaiagent/
│   ├── controller/          # 旅行/智能体 HTTP 接口
│   ├── app/                  # 寰宇智导：TravelMaster（对话、报告、RAG）
│   ├── agent/                # ReActAgent、ToolCallAgent、SyManus
│   ├── tools/                # WebSearch、ImageSearch、WebScraping、PDF、File、Download、Terminal、Terminate
│   ├── rag/                  # DocumentLoader、VectorStore、QueryRewriter
│   ├── config/               # CORS、Manus 记忆等
│   └── chatmemory/           # 文件持久化对话记忆
├── src/main/resources/
│   ├── application.yml       # 主配置（敏感项用环境变量占位，MCP 可选）
│   ├── application-local.yml.example   # 本地配置示例
│   └── document/             # RAG 用 Markdown（如 travel_guide.md）
├── sy-image-search-mcp/     # 【可选】MCP 图片搜索服务（Pexels，SSE/stdio）
├── frontend/                 # Vue 3 + Vite（TravelChat、ManusChat）
├── docs/                     # 文档与截图说明（含 MCP-SETUP.md）
├── SECURITY.md               # 敏感信息配置说明
└── README.md
```

---

## 快速开始

### 环境要求

- **JDK 21**
- **Maven 3.6+**
- **Node.js 18+**（仅前端需要）

### 1. 配置敏感信息

**方式一（推荐本地开发）**  
复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入：

- 数据库：`spring.datasource.url / username / password`（若使用 RAG/向量库）
- `spring.ai.dashscope.api-key`：阿里云百炼 API Key
- `search-api.api-key`：搜索 API Key
- （可选）启用 **sy-image-search-mcp** 时：`PEXELS_API_KEY` 或子模块内 `application-local.yml` 的 `pexels.api-key`，见 [SECURITY.md](SECURITY.md)。

`application-local.yml` 已加入 `.gitignore`，不会提交到 Git。

**方式二（环境变量）**  
见 [SECURITY.md](SECURITY.md)，支持 `DASHSCOPE_API_KEY`、`SEARCH_API_KEY`、`DB_*` 等。

### 2. 启动后端

```bash
mvn spring-boot:run
```

默认端口 **8123**，上下文路径 **/api**。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

按终端提示访问（通常为 `http://localhost:5173`）。前端会请求 `http://localhost:8123/api`，需确保后端已启动且 CORS 已配置。

### 4. 验证

- 健康检查：`GET http://localhost:8123/api/travel/health`
- API 文档：`http://localhost:8123/api/doc.html` 或 `http://localhost:8123/api/swagger-ui.html`

---

## 运行效果与截图

截图和生成文件放在 `docs/screenshots/`，命名规范见 [docs/screenshots/README.md](docs/screenshots/README.md)。

---

## 相关文档

- [SECURITY.md](SECURITY.md)：敏感信息与安全配置说明。
- [docs/MCP-SETUP.md](docs/MCP-SETUP.md)：**sy-image-search-mcp**（MCP 图片搜索服务）的构建、SSE/stdio 启动与主应用接入说明。
- [docs/screenshots/README.md](docs/screenshots/README.md)：运行生成截图和文件说明。
- PDF 中文字体：若使用 iText 生成中文 PDF，需配置字体，见 `src/main/resources/fonts/`（如有）或 iText 文档。

---

## 开源协议与免责声明

- 本项目采用 [MIT License](LICENSE) 开源。
- 本项目仅供**学习与求职展示**使用。使用阿里云百炼/通义等第三方 API 时，请遵守其服务条款与使用规范。
