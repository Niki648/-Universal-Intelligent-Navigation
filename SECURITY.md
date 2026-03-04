# 敏感信息与安全配置

本仓库**不包含**任何 API Key、数据库密码等敏感信息。请按以下方式配置后再运行项目。

## 方式一：环境变量（推荐用于 CI/生产）

在运行前设置以下环境变量：

| 变量名 | 说明 |
|--------|------|
| `DB_URL` | 数据库连接 URL，如 `jdbc:postgresql://host:5432/dbname` |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `DASHSCOPE_API_KEY` | 阿里云百炼 / DashScope API Key |
| `SEARCH_API_KEY` | 搜索 API Key（主应用） |
| `PEXELS_API_KEY` | Pexels 图片搜索 API Key（sy-image-search-mcp 子模块） |
| `AMAP_MAPS_API_KEY` | 高德地图 MCP 服务 API Key（启用 amap-maps 时需在启动前设置，子进程会继承） |

示例（Windows PowerShell）：

```powershell
$env:DB_URL = "jdbc:postgresql://your-host:5432/your_db"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
$env:DASHSCOPE_API_KEY = "your-dashscope-key"
$env:SEARCH_API_KEY = "your-search-api-key"
```

## 方式二：本地配置文件（适合本地开发）

1. **主应用**：复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入数据库、DashScope、search-api 等。
2. **sy-image-search-mcp 子模块**：复制 `sy-image-search-mcp/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入 `pexels.api-key`。
3. **不要**将任何 `application-local.yml` 提交到 Git（已通过 `.gitignore` 忽略）。

主应用与 sy-image-search-mcp 均已配置加载 `local` profile，会自动加载对应 `application-local.yml`。

**MCP 高德地图**：`mcp-servers.json` 中已移除密钥。启用 amap-maps 时，请在启动主应用前设置环境变量 `AMAP_MAPS_API_KEY`，子进程会继承该环境变量。

## 已脱敏内容

- `application.yml` 中敏感项已改为环境变量占位符（如 `${DB_PASSWORD}`、`${DASHSCOPE_API_KEY}`）。
- 若曾将真实密钥提交过，请在 GitHub 上**轮换**这些 Key 并更新本地/服务器配置。
