# Wayfinder Guild Naming Audit

Audit date: 2026-05-10

Scope: `README.md`, `docs`, `frontend/src`, `src/main/java`, `src/main/resources`, `src/test`, and `tools/wayfinder-cli`.

Skipped: `node_modules`, `dist`, `target`, `private-docs`, `.git`, and generated build output. `private-docs` is treated as a local historical archive and is intentionally excluded from naming gates.

## Summary

- The public product name is mostly normalized to `Wayfinder Guild` / `Wayfinder`.
- No active non-governance source or frontend file currently contains `寰宇智导`.
- The old boundary names `TravelMaster`, `TravelController`, and `TravelPromptConstant` are gone from active Java source after the earlier rename. Remaining mentions are either new `Wayfinder*` names or IDE workspace history.
- `Rpg*` remains the largest consistency topic. It is accepted for now as an implementation/API vocabulary, but future product metadata should migrate toward `Wayfinder`, `Guild`, or `Portfolio` terminology.
- Frontend pages are mostly aligned with `*Page.vue`; allowed exceptions are `RpgHome.vue`, `TravelChat.vue`, and `ManusChat.vue`. `Home.vue` appears to be a legacy compatibility view and should be reviewed before deletion.
- Markdown docs are mostly `UPPER-KEBAB-CASE.md`. `RELEASE-NOTES-v0.1.0.md` is a versioned exception candidate.
- Git status shows `frontend/node_modules` deletion entries, which means generated dependency files were previously tracked. They should stay ignored and be removed from the index in a separate cleanup, not as part of this naming pass.

## P0: Must Fix

- No P0 old-brand hits were found in active source, frontend, or docs outside this audit/governance context.
- `lint-naming` should fail if `寰宇智导`, `TravelMaster`, `TravelController`, or `TravelPromptConstant` appears in active source, frontend, or docs outside intentional governance docs.

## P1: Recommended Cleanup

- `RpgController`, `Rpg*` Java models/services, `/api/rpg/**`, `src/main/resources/rpg`, and `src/main/resources/prompts/rpg` mix implementation vocabulary with product vocabulary. Keep for this phase; consider a later compatibility-aware migration to `Portfolio` or `Guild` naming.
- `RpgHome.vue` is an accepted short-term exception because the active home route still represents the map-style experience. A later rename to `WayfinderHomePage.vue` or `GuildMapPage.vue` would improve consistency.
- `Home.vue` is a legacy compatibility page. It should be confirmed unused before removal or renamed to `LegacyHome.vue`.
- RPG JSON metadata contains product-facing labels such as `RPG Portfolio Backend`. Prefer `Wayfinder Portfolio Backend` or `Guild Portfolio Backend` in a later content pass.
- The CLI command set still includes `lint-rpg`. Keep it for compatibility now; a later alias such as `lint-portfolio` would better match the product vocabulary.

## P2: Deferred / Historical

- Maven `artifactId` remains `sy-ai-agent`.
- Java package remains `com.seewhy.syaiagent`.
- Repository root remains `sy-ai-agent`.
- `/api/travel/**` remains the compatibility API.
- Travel-domain names such as `TravelPlan`, `TravelRagService`, and `TravelEvalHarness` remain valid.
- `.idea/workspace.xml` and `frontend/.idea/workspace.xml` include historical run configurations and change records with old class names. They are IDE-local history and should not drive product naming work.

## Current File Naming Notes

- Frontend page files:
  - Aligned: `ArchitecturePage.vue`, `EvalsPage.vue`, `PortfolioPage.vue`, `ProfilePage.vue`, `ProjectsPage.vue`, `RagLibraryPage.vue`, `SkillsPage.vue`, `TracePage.vue`.
  - Allowed exceptions: `RpgHome.vue`, `TravelChat.vue`, `ManusChat.vue`.
  - Review candidate: `Home.vue`.
- Docs:
  - Aligned examples: `AGENTIC-TRAVEL-BACKEND.md`, `PRD-WAYFINDER-GUILD.md`, `TECH-DESIGN-WAYFINDER.md`, `TEST-REPORT-WAYFINDER.md`.
  - Versioned exception candidate: `RELEASE-NOTES-v0.1.0.md`.
- JSON:
  - Resource config file names are already `kebab-case.json`.
  - JSON `id` fields should remain `kebab-case`; this is now checked by `lint-naming`.
