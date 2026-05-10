# Test Report: Wayfinder Guild v0.1.0

## 1. Scope

This report covers the release candidate verification scope for Wayfinder Guild v0.1.0:

- Spring Boot backend tests.
- Vue/Vite frontend build.
- Rust Wayfinder CLI tests and doctor checks.
- Optional MCP image-search submodule tests.
- Manual smoke route for the portfolio experience.

## 2. Automated Verification Commands

Recommended commands:

```powershell
mvn test
cd frontend
npm run build
cd ..\tools\wayfinder-cli
cargo test
cargo run -- doctor --workspace ..\..
cd ..\..\sy-image-search-mcp
mvn test
```

If Cargo is not available in the current PowerShell `PATH`:

```powershell
C:\Users\cycle\.cargo\bin\cargo.exe test
C:\Users\cycle\.cargo\bin\cargo.exe run -- doctor --workspace ..\..
```

## 3. Backend Tests

Command:

```powershell
mvn test
```

Coverage intent:

- Guardrails.
- TravelPlan model behavior.
- Travel orchestrator services.
- RPG controller and metadata services.
- RAG explain service behavior.
- Agent trace service.
- Skill loader.

Expected result:

```text
BUILD SUCCESS
```

## 4. Frontend Build

Command:

```powershell
cd frontend
npm run build
```

Expected result:

```text
built successfully
```

Known warning:

- Phaser can make the main bundle exceed Vite's default chunk-size warning.
- This is accepted for v0.1.0 because the RPG homepage is central to the portfolio concept.

## 5. Rust CLI Tests

Command:

```powershell
cd tools/wayfinder-cli
cargo test
```

Coverage intent:

- Valid Skill front matter passes.
- Skill id and directory mismatch fails.
- Eval case array fields are validated.
- Prompt placeholder imbalance is detected.

Expected result:

```text
test result: ok
```

## 6. Wayfinder Doctor

Command:

```powershell
cd tools/wayfinder-cli
cargo run -- doctor --workspace ..\..
```

Expected result:

```text
[OK] skills
[OK] rpg
[OK] evals
[OK] prompts
```

The doctor command should also print counts for:

- Skills.
- RPG areas, NPCs, projects, skills, and modules.
- Eval cases.
- Prompt templates.

## 7. MCP Submodule Tests

Command:

```powershell
cd sy-image-search-mcp
mvn test
```

Expected result:

```text
BUILD SUCCESS
```

Note:

- Live Pexels integration-style tests should remain disabled unless explicitly run with `PEXELS_API_KEY`.

## 8. Manual Smoke Route

Run locally or in staging:

- `/`: RPG homepage renders, map is navigable.
- `/profile`: profile and positioning are visible.
- `/projects`: project cards render.
- `/skills`: Skills render and matching demo works or gracefully fails.
- `/evals`: eval cases and rules render.
- `/travel-agent`: chat is available; TravelPlan generation shows loading state and returns result in Demo Mode or live mode.
- `/rag-library`: RAG explain response or fallback renders.
- `/trace`: query-param and manual chatId trace loading work.
- `/architecture`: architecture and guardrail explanation render.

## 9. Known Risks

- Live model calls can take 30-90 seconds or fail due to provider/network conditions.
- Public production should avoid exposing unrestricted CORS and Swagger/Knife4j.
- Optional PgVector/RAG dependencies may not be available in all environments.
- Phaser bundle size is larger than typical static portfolio pages.
- Demo Mode and Owner Live Mode must be clearly selected before interviews.

## 10. Release Judgment

The release candidate is acceptable when:

- Automated verification commands pass.
- Wayfinder CLI doctor reports OK.
- Manual smoke route succeeds in Demo Mode.
- Live mode has been tested at least once with a controlled key, or explicitly marked as optional for the launch.
