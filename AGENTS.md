# FamilyDocs — project-specific instructions

## Product scope
- This repository is the FamilyDocs Android app.
- Current approved scope: local document management only. No cloud upload, accounts, cloud sync, analytics, advertising, network SDKs, or encryption unless the user explicitly changes scope.
- Store imported files in app-private local storage. Review manifest permissions, backup/data-extraction rules, and dependencies for unintended network or data exposure.

## Design specification
- Minimalist dark interface.
- Compact capsule header and horizontal family-member selector.
- Layered pastel wave-shaped category cards.
- Use smooth spring transitions and custom wave shapes.
- Preserve this approved concept; do not replace it with generic cards without asking.
- Keep interaction motion purposeful and ensure it does not interfere with touch, scrolling, readability, accessibility, or small-screen layouts.

## Repository workflow
- Follow the shared `.agents/skills/android-app-development/SKILL.md` for general Android development, GitHub reuse search, implementation, signing, build, and release verification.
- Before editing, inspect current source, Gradle configuration, Git history, and existing GitHub Actions workflows.
- Keep changes focused and preserve existing behavior outside the requested scope.
- Never commit credentials, signing keys, keystores, API tokens, `local.properties`, or personal documents.
- Do not claim a build, release, or device test succeeded without verifying the corresponding evidence.
