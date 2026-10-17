# NyaWorld Android / iOS update synchronization

## Single source of truth

Use the **same** JSON file as the Windows launcher:

https://serve.nyaworld.site/client/manifest.json

Verified through [GitHub Actions probe](https://github.com/BCXOVER/NyaWorld-Android/actions/runs/39033266209) on 2026-10-17:
- pack version: **0.7.2**
- \`loader: NeoForge\`, \`loaderVersion: 21.1.250\`
- \`files: [ { path, sha256, size }, ... ]\`
- 4,063 files, 426 \`mods/\`, 1,577 \`config/\`, 2,052 \`kubejs/\`, 4 \`defaultconfigs/\`, 2 \`resourcepacks/\`, 1 \`shaderpacks/\`, 1 \`local/\`.
- 0 missing or invalid SHA-256 digests.

**The existing CI-built APK is only a test of the upstream launcher engine; it does not yet implement this specification.**

## Expected updater behavior

1. On startup and **before every Play**, fetch the current manifest over HTTPS. Display pack version and update status; no silent auto-joining an out-of-date server.
2. Compare downloaded file list against a locally saved manifest and actual SHA-256 on disk. Download only absent or changed files; verify size and SHA-256 before installing each file.
3. Stage downloads in a temporary folder. Keep a local recovery/rollback strategy if power/network is lost. Do not delete working files until replacements are verified.
4. After the replacement set succeeds, safely remove **only** stale *updater-managed* game files from an allowlist. Never delete player worlds, saves, screenshots, external mods, account data, controls, or unrelated user files.
5. Record pack version and exact managed paths/hashes **only after the update completes**. A restart resumes interrupted downloads.
6. Resolve NeoForge loader/version changes from the manifest; pause with an actionable message when the Android Java runtime or launcher needs a software update. Do not hardcode the loader version permanently.
7. Only launch once required files, runtime, NeoForge and client-side compatibility checks succeed.

## Android-specific compatibility

- Share **pack version** and authoritative server content with PC, but allow a platform-specific *client overlay* for Android/iOS when a mod is platform-incompatible.
- Do not blindly copy Windows-only natives, GPU-dependent shaders, desktop key mappings, and incompatible graphics/rendering mods.
- TouchController and screen-button bindings must be kept separate from the remotely managed pack so updates do not erase touch controls or hardware keyboard/mouse configuration.
- The server must accept any necessary mobile client exclusions; server-required mods cannot simply be removed without checking the handshake.
- Prioritize POCO X6 Pro 12GB; offer conservative Redmi Pad SE 6GB presets. Neither is yet validated with the full pack.

## iOS

Use the same **manifest protocol**, but a separate \`.ipa\` launcher/runtime and platform overlay. iOS Java/JIT and NeoForge compatibility are unverified. Refreshing game files differs from distributing a new signed IPA.

## Known blockers / validation

- Only manifest **structure and metadata** have been inspected from GitHub; no file download/installation from this manifest has yet been tested on Android.
- The v7 source-only patch previously used a manual update button and bypassed update checks on the Play path when a ready marker existed. That shortcut must be removed before release.
- Existing Android test APK is still the lightly branded XDAetherium build. A separate task is needed to integrate NyaWorld UI, update code, auth, inputs, game profile and Android compatibility.
- Public GitHub should not host user-provided mod binaries, proprietary configs/scripts, passwords or signing keys without permission.