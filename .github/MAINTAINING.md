# Maintaining

## Release

1. **Source APK** — the original YouTube APK (arm64, full APK) is attached to the draft release `source`. Replace it when a new YouTube version is supported. Do not delete or publish this draft.
2. **Actions → Release YouTube Morphe → Run workflow**
   - `patches`: `stable` (latest stable Morphe release) or `dev` (latest dev release)
   - `publish`: unchecked builds a test APK into the run's *Artifacts* only
   - `update_preview` (test builds): the APK offers the latest release, to see the update dialog
   - `r2_test` (test builds, with `update_preview`): the APK and its update info are uploaded to R2 as test files (`apk/test_*`, `updates/*-test.json`) and the APK checks those; the next published build removes them

Every published build becomes the latest release. The workflow builds the official Morphe patches with the additions in `extras/`, applies them together with the latest YaVoT release (built from source with the fixes in `addons/yavot/`), verifies package name and signature, publishes the release (the two APKs, then `YT_Morphe_PRO_Settings_v<patches version>.txt` with the settings of `settings/defaults.json`) and updates `updates/*.json` for the in-app update check.

The changelog is generated from the Morphe changelog (YouTube only), newest first: a stable version lists its own section; a dev version lists the new features of itself and the previous dev version, and the bug fixes of itself only. Fixes of the mod are added under `## <patches version>` in `notes/mod-fixes.md` and listed after the Morphe fixes of that version. Only when a release skips versions (such as the first dev build after a stable one), important features of the skipped versions can be added under `## <patches version>` in `notes/extra-features.md`; they are listed after the Morphe new features of that version. The release notes and the in-app dialog show the same order, New Features, Mod Features (`notes/mod-features.md`), Bug Fixes, then the files (release notes only). Release notes use `##` headings (GitHub draws a thin rule under them), the in-app dialog shows each title bold with a thin rule under it. Optional translations for the in-app dialog: `changelog/<language>.md` whose first line is `<!-- <patches version> -->`.

## 4PDA post

`notes/4pda-post.txt` is the post for the 4PDA topic, updated for each release (YouTube and patches versions, What's new, links).

- An empty line after each `[spoiler=...]` title
- No "test version" note next to dev patches versions, users could misread it
- An empty line after the `YouTube Morphe PRO Settings` title, where the settings file `YT_Morphe_PRO_Settings_v<patches version>.txt` of the release is attached

## Configuration

| File | Purpose |
|---|---|
| `settings/defaults.json` | Recommended settings (Morphe settings export). Each setting is applied once: on first install, or on the update that adds it. |
| `branding/` | App icon (`mipmap-*`), header logo and notification icon (`drawable-*`) |
| `extras/` | Additional patches: theme defaults, default settings, update check, restart on update, experimental notice, Save to device. Strings and arrays of `extras/strings/<locale>/` are appended to the YouTube ones |
| `addons/yavot/` | Fixes applied to the YaVoT add-on before it is built (`git apply` patches; skipped once YaVoT includes them) |

Variant options (package, theme defaults) are set in `build_variant` calls of `.github/workflows/release.yml`.

## Downloads (Cloudflare R2)

When the variable `DL_BASE_URL` is set, published builds are also uploaded to the R2 bucket `R2_BUCKET`: the APKs to `apk/`, the update info to `updates/<variant>.json`. The apps check `DL_BASE_URL/updates/<variant>.json` and download the APK from `DL_BASE_URL/apk/`, so they no longer depend on GitHub. The APKs of the last 3 builds are kept. `cloudflare/worker.js` is the Worker (`cdn`) that serves the bucket (binding `BUCKET`, read only). `updates/*.json` in this repository stays up to date for builds that still check GitHub.

The update address is built into each APK: `DL_BASE_URL` must not change, or the builds that use it stop getting updates.

## Secrets (Settings → Secrets and variables → Actions)

| Name | Type | Value |
|---|---|---|
| `KEYSTORE_B64` | secret | Keystore, base64 |
| `KEYSTORE_PASSWORD` | secret | Keystore password (empty: Morphe default) |
| `KEY_ALIAS` | secret | Key alias (empty: `Morphe`) |
| `KEY_PASSWORD` | secret | Key password (empty: `Morphe`) |
| `CERT_SHA256` | variable | SHA-256 fingerprint of the signing certificate |
| `R2_ACCOUNT_ID` | secret | Cloudflare account ID |
| `R2_ACCESS_KEY_ID` | secret | R2 API token: access key ID (Object Read & Write, bucket only) |
| `R2_SECRET_ACCESS_KEY` | secret | R2 API token: secret access key |
| `R2_BUCKET` | variable | R2 bucket name |
| `DL_BASE_URL` | variable | Address of the Worker, without a trailing slash |
