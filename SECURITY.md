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
| `SEARCH_API_KEY` | 搜索 API Key |

示例（Windows PowerShell）：

```powershell
$env:DB_URL = "jdbc:postgresql://your-host:5432/your_db"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
$env:DASHSCOPE_API_KEY = "your-dashscope-key"
$env:SEARCH_API_KEY = "your-search-api-key"
```

## 方式二：本地配置文件（适合本地开发）

1. 复制示例配置：
   ```bash
   cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
   ```
2. 编辑 `application-local.yml`，填入真实的数据库地址、API Key 等。
3. **不要**将 `application-local.yml` 提交到 Git（已加入 `.gitignore`）。

项目已设置 `spring.profiles.active: local`，会自动加载 `application-local.yml` 覆盖默认占位符。

## 已脱敏内容

- `application.yml` 中敏感项已改为环境变量占位符（如 `${DB_PASSWORD}`、`${DASHSCOPE_API_KEY}`）。
- 若曾将真实密钥提交过，请在 GitHub 上**轮换**这些 Key 并更新本地/服务器配置。
