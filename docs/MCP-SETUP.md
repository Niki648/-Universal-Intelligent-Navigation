# MCP 服务配置与检测说明

## 1. sy-image-search-mcp（图片搜索 MCP）

### 两种运行方式

| 方式 | 说明 | 配置 |
|------|------|------|
| **SSE** | 独立进程，主应用通过 HTTP 连接 | 主应用 `application.yml` 中取消注释 `spring.ai.mcp.client.sse.connections.server1.url: http://localhost:8127` |
| **stdio** | 主应用按 `mcp-servers.json` 启动子进程，通过标准输入输出通信 | 主应用启用 `spring.ai.mcp.client.stdio.servers-configuration: classpath:mcp-servers.json` |

### 本地检测步骤

**1）构建 jar**

```bash
mvn -f sy-image-search-mcp/pom.xml clean package -DskipTests
```

**2）SSE 模式启动（先启动 MCP，再启动主应用）**

```bash
# PowerShell 注意 -D 参数用双引号
java "-Dspring.profiles.active=sse,local" -jar sy-image-search-mcp/target/sy-image-search-mcp-0.0.1-SNAPSHOT.jar
```

成功时日志类似：

- `Tomcat started on port 8127`
- `Registered tools: 1`
- `Started SyImageSearchMcpApplication in ... seconds`

**3）stdio 模式（由主应用按 mcp-servers.json 自动拉起）**

- 主应用需在 `application.yml` 中**取消注释** MCP 相关配置（见下方）。
- 主应用启动时工作目录为项目根目录，jar 路径 `sy-image-search-mcp/target/sy-image-search-mcp-0.0.1-SNAPSHOT.jar` 会相对该目录解析。
- 需先执行步骤 1 构建出 jar，主应用才能成功 spawn 该进程。

### Pexels API Key

- 通过环境变量 `PEXELS_API_KEY` 或 `sy-image-search-mcp/src/main/resources/application-local.yml` 中的 `pexels.api-key` 配置。
- 未配置时调用图片搜索会报错提示。

---

## 2. 主应用中启用 MCP 客户端

当前主应用里 MCP 配置为**注释状态**。若要启用 MCP，在 `src/main/resources/application.yml` 中：

**方式 A：仅用 SSE 连接（推荐，先手动启动 sy-image-search-mcp）**

取消注释并保留：

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            server1:
              url: http://localhost:8127
#        stdio:
#          servers-configuration: classpath:mcp-servers.json
```

**方式 B：仅用 stdio（按 mcp-servers.json 自动启动子进程）**

取消注释并保留：

```yaml
spring:
  ai:
    mcp:
      client:
#        sse:
#          connections:
#            server1:
#              url: http://localhost:8127
        stdio:
          servers-configuration: classpath:mcp-servers.json
```

**注意**：SSE 与 stdio 二选一即可；同时启用可能产生两套连接。

---

## 3. mcp-servers.json 中的 amap-maps

- 高德地图 MCP 使用 `npx -y @amap/amap-maps-mcp-server`，需已安装 Node.js/npx。
- 密钥已从 JSON 中移除，请在**启动主应用前**设置环境变量 `AMAP_MAPS_API_KEY`，子进程会继承。

---

## 4. 检测结果小结

| 项目 | 状态 |
|------|------|
| sy-image-search-mcp 构建 | 通过（jar 已生成） |
| sy-image-search-mcp SSE 启动 | 通过（端口 8127 监听，Registered tools: 1） |
| stdio 启动 | 依赖主应用取消注释 MCP 配置并先构建好 jar |
| 主应用 MCP 客户端 | 当前为注释状态，需按上文取消注释后重启主应用 |
