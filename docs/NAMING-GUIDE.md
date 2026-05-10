# Wayfinder Guild Naming Guide

This guide is the source of truth for naming during the Wayfinder Guild normalization phase.

## Product Brand

- `Wayfinder Guild`: the full product name for the portfolio experience and public product boundary.
- `Wayfinder`: the short name and the core Agent name.
- `The Wayfinder` / `寻路者`: the visitor role inside the portfolio town experience.

## Reserved Names

These names are intentionally retained for compatibility or domain clarity:

- `Travel*`: travel-domain models and services, such as `TravelPlan`, `TravelRagService`, and `TravelEvalHarness`.
- `SyManus`: the tool-calling Agent.
- `/api/travel/**`: compatibility API surface. Do not rename it in this phase.
- Maven `artifactId`: keep `sy-ai-agent` for now.
- Java package: keep `com.seewhy.syaiagent` for now.

## Recommended Names

- Product boundary classes should use `Wayfinder*`.
- Portfolio and town metadata should prefer `Wayfinder`, `Guild`, or `Portfolio` language. Avoid casually mixing in `RPG` unless the name describes the gameplay-style presentation or an existing API compatibility boundary.
- Frontend page components should use `PascalCase + Page.vue`.
- Generic Vue components should use `PascalCase.vue`.
- JSON config files should use `kebab-case.json`.
- Markdown documentation should use `UPPER-KEBAB-CASE.md`.
- Rust crates should use `kebab-case`.

## Temporary Non-Goals

Do not rename these in the current phase:

- Java package `com.seewhy.syaiagent`.
- Repository root directory `sy-ai-agent`.
- Compatibility API paths under `/api/travel/**`.
- Travel-domain names such as `TravelPlan`, `TravelRagService`, and `TravelEvalHarness`.

## Intentional Legacy References

Governance documents may mention old names when explaining migration history or audit findings. Product UI, README-level branding, source boundaries, and active docs should not reintroduce the old brand `寰宇智导` or old boundary names such as `TravelMaster`, `TravelController`, or `TravelPromptConstant`.
