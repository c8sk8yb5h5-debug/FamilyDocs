# FamilyDocs

Android app for managing family documents locally.

- Native Android with Kotlin and Jetpack Compose.
- Documents are stored locally in app-private storage; no cloud upload, accounts, or encryption in this initial version.
- UI: minimalist dark theme, family-member selector, animated wave-shaped category cards.

See `AGENTS.md`, `UI_SPEC.md`, and `.agents/skills/android-app-development/SKILL.md` for development instructions.

> Initial source import. Build and device validation are still required.
## Known-good installation baseline

- Confirmed by the user: **v0.3.17** installs and launches successfully on their Android device.
- Release: https://github.com/c8sk8yb5h5-debug/FamilyDocs/releases/tag/v0.3.17
- Source commit used for the successful release build: `beb50e5064fd085829d6a229accc99331d88d93f`
- Release signing: persistent keystore configured through GitHub Actions Secrets. Never regenerate or rotate this key for routine releases.
- Scope of validation: installation and app startup confirmed; in-app update replacement still requires a separate test with a later release.
