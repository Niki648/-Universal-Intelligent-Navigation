# 项目总结（简历风格 + 表述修正）

## 一、简历风格项目描述（可直接用于简历）

- **自主实现对话记忆持久化**：为解决服务重启后多轮对话记忆丢失问题，基于文件系统与 Kryo 序列化库设计了 `FileBasedChatMemory`，实现 Spring AI `ChatMemory` 接口，按会话 ID 将对话状态序列化存储为 `.kryo` 文件并支持快速加载，保障用户对话的连续性；Kryo 将对象序列化为字节数组，便于后续扩展至 Redis 等存储。
- **设计实现 RAG 检索增强模块**：自定义 VectorStore 配置与检索流程，结合 Spring AI Embedding 模型将知识库文本存储于 PGvector 向量数据库（HNSW 索引、余弦距离），通过 Filter 元数据过滤与相似度阈值实现多维度过滤与语义相似度检索，并封装为 `RetrievalAugmentationAdvisor`，提升智能体回答的准确性与相关性。
- **构建具备自主规划能力的 AI 智能体**：基于 ReAct（Reasoning + Acting）框架实现 `ReActAgent` → `ToolCallAgent` → SyManus 层次结构，使智能体能自主分解任务、决策工具调用链；通过最大步数限制（maxSteps）与 `AgentState` 状态机（IDLE/RUNNING/FINISHED/ERROR），避免智能体陷入逻辑死循环，并在达到步数或完成时正确收尾。
- **实现灵活可扩展的工具调用框架**：采用 Spring `@Configuration` + `@Bean` 构建集中式工具注册中心 `ToolRegistration`，通过 `ToolCallbacks.from()` 与 Spring 注入实现工具的松耦合注册；利用 `@Tool` / `@ToolParam` 注解封装网页搜索、网页抓取、资源下载、文件读写、终端执行、PDF 生成、图片搜索（Pexels）、任务终止等能力。
- **开发 MCP 服务扩展 AI 能力边界**：基于 Spring AI MCP Server 独立子模块 `sy-image-search-mcp` 集成 Pexels 图片 API，提供图片搜索 MCP 服务，使主智能体通过 MCP 客户端调用并整合图片检索能力；通过 `application-stdio` / `application-sse` 等 Profile 支持 Stdio 与 SSE 两套传输模式，适配本地开发与云端/远程部署。
- **应用架构与可部署性**：采用 Spring Boot 3 + Spring AI 主应用与独立 MCP 服务子模块的架构，主应用与 MCP 服务均可打包为可执行 JAR 独立运行；主应用提供 SSE 流式输出（如 `/travel/chat/stream`），便于前后端集成与后续容器化部署。
- **质量保障与可测性**：搭建分层测试体系（工具单测 + Mock 外部 API + 场景化集成测试），使用 JUnit 5、Mockito、SpringBootTest 覆盖核心工具与多轮对话、多工具串联场景；通过 Mock Http 请求/响应解决对外部 Pexels API 的依赖，保证用例在无网或 CI 环境下稳定执行；设计请求/响应日志 Advisor 与 Agent 状态与步数，提升可观测与可排查能力。

---

## 二、对话记忆：Spring AI 默认方式 vs 你的持久化方案（准确表述）

### Spring AI 默认方式及其局限

- Spring AI 默认通过 **`MessageChatMemoryAdvisor`** 实现多轮对话记忆：该 **Advisor（拦截器）** 在调用链中把**近期对话消息**（通常是完整的一条条 User/Assistant Message）加入当次请求的上下文中，从而让模型“记得”历史。
- 底层依赖的 `ChatMemory` 实现多为 **内存型**（如 `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`），即对话历史只存在于 JVM 内存。
- **弊端**：服务重启、进程崩溃、超时重启、扩容缩容等都会导致内存清空，**多轮对话记忆全部丢失**，无法满足“会话可恢复、长期连续”的真实业务需求。

### 你的方案：FileBasedChatMemory

- 实现 Spring AI 的 **`ChatMemory`** 接口，用 **文件系统 + Kryo** 做持久化：
  - **按会话 ID** 将 `List<Message>` 序列化为二进制文件（如 `{conversationId}.kryo`），落盘到指定目录（如 `data/manus-chat/`）。
  - 下次请求时根据 `conversationId` **反序列化** 读出历史消息，再交给 Agent 作为上下文（在 `BaseAgent` 中加载进 `messageList`），实现**跨进程、跨重启**的多轮对话记忆。
- 这样即使用户刷新页面、服务重启或部署新版本，只要会话 ID 不变，历史仍可从文件恢复，实现**真正的多轮对话持久化**。

**注意**：简历/口述中若写“把对话历史用拦截器拦截成**关键字**加入提示词”，容易让人误解。更准确的说法是：默认 Advisor 是把**近期对话消息（完整消息列表）**加入提示词上下文，而不是先做“关键词抽取”再加入；你的改进点在于**存储介质**（内存 → 文件/Kryo），而不是“从关键字改成完整历史”。

---

## 三、Kryo 与 Redis 的“巧思”（正确版）

- **Kryo** 把 Java 对象（如 `List<Message>`）序列化成 **字节数组（byte[]）**，写入 `.kryo` 文件即写入二进制内容。
- **Redis** 的 value 类型有：string、list、hash、set、zset。其中 **string** 类型可以存**任意字节序列**（包括二进制），所以在 Java 里把 `byte[]` 当作 Redis 的 value 存入/取出是常见做法。
- 你提到的“**不能存储自然语言**”需要修正：Redis 的 string 类型**完全可以**存自然语言（UTF-8 文本）。这里的重点不是“自然语言能不能进 Redis”，而是：
  - **ChatMemory 里要存的是 Java 对象**（如 `List<Message>`），不能直接把对象放进 Redis；
  - 需要先**序列化**成字节，再以 string（或 binary-safe string）形式存进 Redis；
  - 你选用 Kryo → byte[]，天然就得到“可写入 Redis value”的格式，为以后从**文件存储**迁到 **Redis**（或同时支持）做好了准备，**扩展上限更高**，能更好应对多实例、分布式场景。

**一句话总结**：Kryo 把对象变成 byte[]，既适合写文件，也适合作为 Redis 的 value，为后续接入 Redis、做分布式会话记忆留好了接口。

---

## 四、你表述中需要修正的几点

| 原表述 | 问题 | 建议修正 |
|--------|------|----------|
| “把对话的历史用拦截器拦截成**关键字**加入提示词中” | Spring AI 的 MessageChatMemoryAdvisor 一般是把**近期完整消息**加入上下文，而不是先抽“关键字”再加入。 | 改为：“通过拦截器将**近期对话消息**加入提示词上下文”或“将对话历史以**消息列表**形式注入当次请求的 prompt”。 |
| “redis 里……**不能存储自然语言**” | Redis 的 string 类型可以存任意 UTF-8 文本（自然语言）。 | 改为：“Redis 的 value 需为字符串/字节等类型，**无法直接存储 Java 对象**；需先将对象序列化为字节再存入，Kryo 序列化得到的 byte[] 可直接作为 Redis value，便于后续接入 Redis。” |
| “字节数组是可以作为 value 值**传入 Java** 的” | 在“为 Redis 做准备”的语境下，更合理的是“作为 **Redis** 的 value”。在 Java 里本来就是 byte[]，不存在“传入 Java”的说法。 | 改为：“字节数组可以直接作为 **Redis** 的 value 存储”或“便于在 Java 中写入 **Redis**”。 |

---

## 五、小结

- **项目总结**：已按你原来的简历风格整理成可直接使用的条目，并保持与当前代码一致（如 `FileBasedChatMemory`、ReAct、RAG、MCP、测试等）。
- **对话记忆**：强调“默认是内存 + 拦截器注入近期消息”，你的贡献是“同接口下换成文件 + Kryo 持久化，实现跨重启的连续对话”。
- **Kryo 与 Redis**：强调“对象 → Kryo → byte[] → 可写文件/可写 Redis”，不说“Redis 不能存自然语言”，而说“Redis 不能直接存 Java 对象，字节数组适合做 Redis value，为后续接入 Redis 做了准备”。

按上述修正后，技术描述会更准确，面试时也更容易讲清楚设计和扩展思路。
