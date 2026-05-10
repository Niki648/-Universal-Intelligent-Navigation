# Wayfinder Guild Deployment Guide

This guide keeps the public portfolio deployment small and cost-controlled.

## Recommended Public Runtime

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8123
WAYFINDER_DEMO_ENABLED=true
TRAVEL_RAG_MODE=demo
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=INFO
LOGGING_LEVEL_COM_SEEWHY_SYAIAGENT=INFO
```

With this setup:

- PgVector is not required.
- No cloud vector database is required.
- RAG Library shows Demo Mode instead of an error.
- Live model and database cost stay under owner control.

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
```

If PgVector is unavailable, `/api/travel/rag` and `/api/travel/rag/explain` degrade to lightweight retrieval and log a warning instead of failing the request with a 500.

## Topology

```text
Visitor
  -> HTTPS / Nginx
     -> static frontend
     -> /api reverse proxy to 127.0.0.1:8123/api
  -> Spring Boot JAR
```

Redis is not required for v0.1.0. Keep it deferred until rate limiting, trace cache, session sharing, or multi-instance deployment becomes necessary.
