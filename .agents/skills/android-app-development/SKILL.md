---
name: android-app-development
description: Develop, modify, test, debug, and release Android apps. Use whenever the user asks to create or change an Android app, implement UI, fix bugs, build an APK, configure signing, or work on an Android repository.
---

# Android App Development

## Mandatory entry checks
1. Identify the active repository and branch; inspect the latest commit and working-tree state where available. Never guess repository names, paths, workflow names, run IDs, or asset URLs.
2. Read the repository's `AGENTS.md`, this Skill, README, applicable design/spec documents, relevant source/build files, and existing CI/release workflows before editing.
3. Search GitHub for mature reusable projects before implementing features. Check license, maintenance, compatibility, dependencies, architecture, and fit. Reuse only what is appropriate; do not copy blindly.
4. Extract the user's requested outcome and explicit constraints. Make the smallest scoped change. Do not add unrequested features, dependencies, services, analytics, cloud sync, encryption, or product behavior.
5. If required instructions are missing or contradictory, report the exact conflict and resolve only what can be resolved from the user's request. Do not invent requirements.

## Implementation and UI
- Work in the existing repository and preserve its history, package ID, architecture, data, signing, versioning, and working behavior unless explicitly asked to change them.
- Follow the current project's design specification first. General preference: minimalist UI, clear hierarchy, restrained visual noise, and purposeful smooth interaction/animation. Do not transfer another app's palette, icon concept, or product-specific UI into this app.
- Review the diff for duplicated/stale code, broken listeners, state/persistence regressions, gesture conflicts, clipping, accessibility, permissions, and unintended network behavior.
- Keep changes focused. Do not rewrite unrelated files or create disconnected replacement projects for ordinary iterations.

## Signing and release — only when producing an installable release APK
1. Before the first user-installable release, check whether a valid existing signing key is already configured. Reuse it if available; do not create a replacement key merely because a new version is being built.
2. If no key exists and tools permit, generate one persistent keystore automatically. Provide the keystore, Base64 representation, and Secrets setup instructions as files/bundle when useful; do not ask the user to run commands that the available tools can perform.
3. Never regenerate the key for routine releases. Protect the keystore and passwords; never commit them, print them in logs, or ask the user to paste them into chat. Explain secure backup and the consequence of losing the key.
4. Use the exact documented GitHub Secrets names from the actual workflow. Base64 must be derived from the exact keystore used for CI. Do not assume store password and key password are equal unless verified.
5. Before Gradle release packaging, fail fast if required secrets are missing; decode the keystore to a temporary location and run `keytool` to validate the file, password, and alias without exposing secrets. Verify the actual workflow implements this; documentation alone is not proof.
6. Diagnose failures from the latest relevant Actions run and logs. Distinguish missing Secrets, Base64/decode problems, store-password errors, alias mismatch, key-password errors, build failures, and release-upload failures. Fix the identified cause and verify the next run.
7. Verify package ID, versionCode, signing certificate, and release asset. Warn before any uninstall that may erase app data if an older APK uses a different signing certificate.
8. Distinguish source review, CI compilation, signature verification, emulator testing, and physical-device installation/update testing. Never claim a level of validation that did not occur.

## Build, test, and delivery
1. Inspect and reuse existing Gradle and GitHub Actions workflows where appropriate. Do not create duplicate workflows without need.
2. Run available tests, lint, and the requested build. If local Android tooling is unavailable, use accessible CI or state the blocker.
3. Tie results to the exact commit SHA and relevant workflow run. Read logs for failures; do not treat a queued or unrelated successful run as evidence.
4. Verify the APK artifact or Release asset exists and is downloadable before linking it. A source ZIP is not an APK.
5. Commit/push only through available authorized access. Never force-push, rewrite history, change repository visibility, alter Secrets/settings, or delete user work without explicit authorization. If unable to push, state that plainly.
6. Report changed files, commit SHA when available, actual test/build/signature status, verified artifact link, and remaining device checks. Respond in concise Chinese unless the user uses another language.

## Completion gate
Before declaring completion, check:
- [ ] Project-specific instructions and design spec were read.
- [ ] GitHub reuse search was performed before feature implementation.
- [ ] Changes are scoped and diff-reviewed.
- [ ] Relevant tests/build were run or the blocker is explicit.
- [ ] Signing checks were run if releasing a signed APK.
- [ ] The exact CI run and artifact/release asset were verified before claims or links.
- [ ] Any unperformed real-device checks are clearly identified.
