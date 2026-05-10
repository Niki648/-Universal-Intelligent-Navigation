# Git Hygiene Report

Audit date: 2026-05-10

Scope: Git index/worktree status, `.gitignore`, README first screen, docs, frontend, backend resources, and local secret/config patterns.

## Executive Summary

- The repository is close to public-shareable after removing generated artifacts from the Git index and keeping local/private material ignored.
- `frontend/node_modules` is the main hygiene issue. It contains 1,143 staged deletions and should remain removed from Git history going forward.
- `docs/screenshots` contains 13 tracked screenshot/PDF/download artifacts currently staged for deletion from the index. These should not be part of the public release unless explicitly curated later.
- `src/main/resources/application-local.yml` exists locally and contains real-looking local credentials/API key material. It is ignored by `.gitignore` and must never be staged.
- `private-docs` exists locally and is ignored. It should stay local unless the owner explicitly reviews and sanitizes it.

## Must Remove From Git Index

These are generated or private/demo artifacts and should not be committed as tracked files:

- `frontend/node_modules/**`
  - Status: already staged as removed from the index.
  - Count observed in staged diff: 1,143 paths.
  - Action taken this pass: `git rm -r --cached --ignore-unmatch frontend/node_modules docs/screenshots`.
- `docs/screenshots/**`
  - Status: already staged as removed from the index.
  - Count observed in staged diff: 13 paths plus one nested downloaded Markdown artifact.
  - Includes screenshot PNG/JPG files, PDFs, and downloaded demo artifacts.

## Can Be Kept

These files are appropriate for a public engineering portfolio repository:

- Source code under `src/main/java`, `src/test/java`, `frontend/src`, `sy-image-search-mcp`, and `tools/wayfinder-cli`.
- Build descriptors and lock files:
  - `pom.xml`
  - `frontend/package.json`
  - `frontend/package-lock.json`
  - `tools/wayfinder-cli/Cargo.toml`
  - `tools/wayfinder-cli/Cargo.lock`
- Public templates:
  - `.env.example`
  - `src/main/resources/application-local.yml.example`
- Public docs:
  - `README.md`
  - `SECURITY.md`
  - `docs/*.md` that do not contain private notes or binary artifacts.
- Runtime-safe config:
  - `src/main/resources/application.yml`, as long as it keeps using environment placeholders rather than literal credentials.

## Needs Owner Confirmation

These items are not automatically deleted in this pass:

- Deleted historical docs currently shown by `git status`, such as `docs/ARCH-DEEPDIVE-RAG-AGENT.md`, interview notes, MCP setup notes, and older packaging docs.
  - Recommendation: confirm whether these were intentionally replaced by Wayfinder launch docs before committing their deletion.
- Old Java class deletions from the naming migration, including legacy travel boundary classes and related tests/config files.
  - Recommendation: keep if already replaced by `Wayfinder*` classes and tests pass; do not restore just for hygiene.
- `private-docs/**`
  - Recommendation: keep ignored and local. Only publish after a manual content/security review.
- `.idea/**` and `frontend/.idea/**`
  - Recommendation: keep ignored and local. They contain IDE workspace state and datasource metadata and should not be staged.

## Secrets Risk

Observed risk levels:

- High local risk: `src/main/resources/application-local.yml`
  - It contains local database password/API-key-looking values.
  - It is ignored by `.gitignore`; do not stage it.
  - Rotate any real key that has ever been committed or shared.
- Medium local risk: `private-docs/PRIVATE-DOCS-ARCHIVE.md`
  - It contains placeholder-looking secret examples and private planning notes.
  - It is ignored and should stay private.
- Low/public-safe examples:
  - `.env.example`, `README.md`, `SECURITY.md`, and docs use placeholder values such as `your_key`, `replace-me`, or environment-variable names.
- IDE metadata risk:
  - `.idea/dataSources*` contains local datasource metadata. It is ignored and should remain out of Git.

## Ignore Rules Updated

`.gitignore` now covers:

- Maven/Java build output: `target`, `build`, `out`.
- Frontend dependencies/build output: `node_modules`, `frontend/node_modules`, `dist`, `frontend/dist`, `.vite`.
- Local env/secrets: `.env`, `.env.*`, `application-local.yml`, key/keystore files.
- Runtime/generated data: `data`, `tmp`, `temp`, logs, cache, coverage.
- Demo artifacts: screenshots and download folders.
- Local private docs: `private-docs`.
- OS and IDE noise.

## README Public Review

The README first screen is suitable for public review:

- It leads with `Wayfinder Guild · AI Engineering Portfolio`.
- It explains the project as a portfolio/productized AI engineering showcase.
- It names the core stack and capabilities clearly.
- It avoids exposing secrets and uses environment placeholders later in the setup section.

No large README rewrite was needed in this pass.
