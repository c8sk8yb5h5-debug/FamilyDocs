# FamilyDocs development instructions

## Required workflow
1. Before implementing a feature, search GitHub for mature projects that can be reused. Review license, maintenance, compatibility, and fit; prefer a small native implementation when appropriate.
2. Inspect the repository source, Gradle configuration, Git history, and existing GitHub Actions workflows before editing.
3. Continue work in this repository; do not create disconnected replacement source packages for routine iterations.
4. Keep changes scoped. After edits, inspect diffs and check state handling, persistence, file import, gesture conflicts, clipping, and accessibility.
5. Use GitHub Actions to run Android Gradle builds and tests. Diagnose failed runs from logs and fix issues before claiming success.
6. Verify the workflow run, commit SHA, artifact, and Release asset. Only provide an APK link after the actual asset exists.
7. Distinguish static/source review, CI build success, and real-device validation. Never claim device testing unless it was performed.
8. Do not expose credentials, signing keys, tokens, or personal documents in the repository. Keep the repository private while this project is in development.

## Product constraints
- Family document manager for Android.
- No cloud upload, account, cloud sync, or encryption in the current version, as explicitly requested.
- Store imported files in app-private local storage. Do not add network SDKs or cloud backup without explicit approval.

## Design preferences
- Minimalist UI with meaningful interactive motion.
- Dark background, compact capsule header, horizontal family-member selector, layered pastel wave-shaped category cards.
- Use smooth spring transitions and custom wave shapes; do not replace the approved concept with generic cards.
