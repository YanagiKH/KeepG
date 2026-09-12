# KeepG 0.7 release-readiness plan

## Scope and integration

Continue PR #8 on `feature/workspace-agent-0.7`; preserve the user's existing app and data. Do not merge failed checks or publish unverified installation files.

1. Repair the translation raw literal, image-editor scope and shared album-visibility guard. Regression-test the translation checker against the exact failure class (rows outside the Kotlin literal).
2. Run Full/Lite lint, unit tests, APK assembly and API 26/35 instrumented tests. Exercise album-local long press, selection and batch actions; settings search/languages; editor gestures and saved-copy behavior; model presets and chat/skills boundaries.
3. Audit model download integrity, compatible-format filtering, attachment limits, action authorization and lifecycle cleanup. Model output and installed Skills remain untrusted data. Never give an agent unrestricted shell access or silent permission to delete media.
4. Capture real app UI using synthetic fixtures; inspect screenshots and publish them with operation instructions. Remove stale claims about network permissions. Keep security, privacy, debugging, editing and AI documentation consistent with implementation.
5. Merge only the exact green PR revision. Require successful CI on the resulting current `main` revision before the release workflow publishes all generated APKs/AABs and SHA-256 checksums. Verify release assets, signatures and their source revision.

## Evidence and limits

Keep actual exit codes, CI artifacts and source checksums. Do not weaken assertions to manufacture green. Emulator tests are not ARM-phone Gemma-inference evidence; document untested hardware/model combinations rather than presenting them as verified. APK debug signing and AAB production-signing requirements must be explicit. No absolute zero-defect warranty is implied by automated checks.
