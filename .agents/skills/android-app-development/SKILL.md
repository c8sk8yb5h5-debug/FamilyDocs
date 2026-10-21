# Android App Development Skill

Use this workflow whenever developing or modifying an Android app for this user.

## 1. Repository-first workflow
- Search GitHub for mature projects before implementation; inspect licenses, activity, compatibility, and relevant source.
- Inspect the current project repository, branch, recent commits, Gradle files, existing tests, and CI workflows.
- Reuse the existing GitHub repository and preserve working behavior. Avoid generating disconnected replacement projects for normal changes.
- If a repository must be created, check available GitHub permissions and clearly identify any step that requires the user.

## 2. Implement and review
- Read the source and build configuration related to the requested change.
- Implement the actual code change, not only a plan.
- Keep changes focused. Review the diff and look for duplicated code, stale code, broken listeners, gesture conflicts, layout clipping, state restoration, and persistence regressions.
- Follow the project's UI specification. The user prefers minimalist interfaces and purposeful interactive motion, including smooth spring-like transitions.

## 3. GitHub collaboration
- Treat GitHub as the source of truth for ongoing development.
- Commit changes with descriptive messages and keep changes traceable to a commit SHA.
- Prefer small, reviewable commits. Do not overwrite unrelated user changes or force-push without explicit need and approval.
- Keep repositories private when they contain sensitive app context or user data. Never commit real identity documents, credentials, API keys, signing keys, or local configuration.

## 4. Automated build and release
- Configure GitHub Actions for Android builds using the project's Gradle wrapper and JDK/Android setup compatible with the project.
- Run unit tests and assemble the intended APK variant when possible.
- Inspect the exact workflow run and logs. If it fails, fix the cause and rerun.
- Upload APKs as workflow artifacts; create a GitHub Release only when intended and the build succeeds.
- Before sharing an APK, verify the workflow result, source commit SHA, artifact or Release asset URL, and that the asset actually exists.
- Never invent download links or claim a build succeeded without evidence.
- Clearly distinguish CI compilation from installation and real-device testing.

## 5. Delivery checklist
- Summarize files changed and the commit SHA.
- Provide the actual GitHub repository and workflow links.
- Report build/test status honestly.
- Provide APK links only after verifying the corresponding asset exists.
- List remaining manual checks, especially on-device behavior.
