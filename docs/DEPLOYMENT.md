# Wayfinder Guild Deployment Guide

This guide keeps the public portfolio deployment small and cost-controlled.

## Recommended Public Runtime

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8123
WAYFINDER_DEMO_ENABLED=true
TRAVEL_RAG_MODE=demo
WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS=https://your-domain.example
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
KNIFE4J_ENABLE=false
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=INFO
LOGGING_LEVEL_COM_SEEWHY_SYAIAGENT=INFO
SEARCH_PROVIDER=disabled
```

With this setup:

- PgVector is not required.
- No cloud vector database is required.
- RAG Library shows Demo Mode instead of an error.
- Live model and database cost stay under owner control.
- `DEEPSEEK_API_KEY` can be omitted for a static public demo, but live chat endpoints require a real key or should be hidden/disabled at the proxy/UI layer.
- Swagger/Knife4j are closed unless explicitly enabled for a controlled demo.
- CORS only allows the configured frontend origin; same-origin deployments can keep this narrow.

## Lightweight RAG Runtime

```env
WAYFINDER_DEMO_ENABLED=false
TRAVEL_RAG_MODE=lightweight
```

This mode searches bundled Markdown documents from `src/main/resources/document/*.md`. It is suitable for a public site that should show real source snippets without paying for PgVector.

## Owner Live PgVector Runtime

Use only for local deep demos or controlled Owner Live Mode:

```env
WAYFINDER_DEMO_ENABLED=false
TRAVEL_RAG_MODE=pgvector
DEEPSEEK_API_KEY=...
DB_URL=jdbc:postgresql://127.0.0.1:5432/sy_ai_agent
DB_USERNAME=sy_ai_agent
DB_PASSWORD=...
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
KNIFE4J_ENABLE=true
```

If PgVector is unavailable, `/api/travel/rag` and `/api/travel/rag/explain` degrade to lightweight retrieval and log a warning instead of failing the request with a 500.

## MCP and Tool Exposure

Keep MCP disabled for the public portfolio unless you are running an Owner Live demo. When enabling MCP:

- Start `sy-image-search-mcp` with `MCP_SPRING_PROFILES_ACTIVE=sse` or `stdio` as needed.
- Set `PEXELS_API_KEY` only in the server environment or local `application-local.yml`.
- Set `AMAP_MAPS_API_KEY` before launching any AMap MCP child process.
- Do not expose terminal/file/download tools without authentication, rate limiting, and audit logs.

## Topology

```text
Visitor
  -> HTTPS / Nginx
     -> static frontend
     -> /api reverse proxy to 127.0.0.1:8123/api
  -> Spring Boot JAR
```

Redis is not required for v0.1.0. Keep it deferred until rate limiting, trace cache, session sharing, or multi-instance deployment becomes necessary.
