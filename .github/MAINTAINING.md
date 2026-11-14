# Maintaining

## Release

1. **Source APK** — the original YouTube APK (arm64, full APK) is attached to the draft release `source`. Replace it when a new YouTube version is supported. Do not delete or publish this draft.
2. **Actions → Release YT Morphe → Run workflow**
   - `patches`: `stable` (latest stable Morphe release) or `dev` (latest dev release)
   - `publish`: unchecked builds a test APK into the run's *Artifacts* only
   - `update_preview` (test builds): the APK offers the latest release, to see the update dialog
   - `r2_test` (test builds, with `update_preview`): the APK and its update info are uploaded to R2 as test files (`apk/test_*`, `updates/*-test.json`) and the APK checks those; the next published build removes them

The changelog of a published release is generated again, without a new build, with **Actions → Update release notes** (`tag`: empty for the latest release): the files table stays, the in-app changelog is replaced if the update info is still the one of that release.

Old releases are removed with **Actions → Remove old releases** (`keep`: the newest published releases to keep; drafts such as `source` are never removed).

Every published build becomes the latest release. The workflow builds the official Morphe patches with the changes in `addons/morphe/` and the additions in `extras/`, applies them together with the latest YaVoT release (built from source with the fixes in `addons/yavot/`), verifies package name and signature, uploads the two APKs to R2, publishes the release (`YT_Morphe_PRO_Settings_v<patches version>.txt` with the settings of `settings/defaults.json`, and links to the APKs on R2) and updates `updates/*.json` for the in-app update check.

The changelog is generated from the Morphe changelog (YouTube only), newest first: a stable version lists the new features of its own section; a dev version lists the new features of all dev versions since the last one released here. Bug fixes and improvements are not listed one by one: the notes always end with the heading `🔧 Bug fixes and other improvements`, followed in the release notes by the reminder to install the downloaded APK from the Downloads folder. Only when a release skips versions (such as the first dev build after a stable one), important features of the skipped versions can be added under `## <patches version>` in `notes/extra-features.md`; they are listed after the Morphe new features of that version. The release notes and the in-app dialog show the same order, New Features, Mod Features (`notes/mod-features.md`), the closing heading, then the files (release notes only). Release notes use `##` headings (GitHub draws a thin rule under them), the in-app dialog shows each title bold with a thin rule under it. Optional translations for the in-app dialog: `changelog/<language>.md` whose first line is `<!-- <patches version> -->`.

## 4PDA post

`notes/4pda-post.txt` is the post for the 4PDA topic, updated for each release (YouTube and patches versions, What's new, links).

- An empty line after each `[spoiler=...]` title
- No "test version" note next to dev patches versions, users could misread it
- What's new is a plain list, without a line for the YouTube and patches versions (such as "YouTube 21.44.528, Morphe Patches 2.1.0 (стабильная версия)"). A stable version lists its full changelog, the best YouTube features first; a dev version lists the new features of the last 2 dev versions
- Link titles in `[color="#808080"]`, followed by the file size
- Section order: header, What's new, Features, Download (Amoled, then Original), Mod info, Help (MicroG RE, playback fixes)

## Configuration

| File | Purpose |
|---|---|
| `settings/defaults.json` | Recommended settings (Morphe settings export). Each setting is applied once: on first install, or on the update that adds it. Importing settings (also a preset file) keeps the channel whitelists of ads, playback speed and SponsorBlock. |
| `branding/` | App icon (`mipmap-*`), header logo and notification icon (`drawable-*`) |
| `extras/` | Additional patches: theme defaults, default settings, update check, restart on update, experimental notice, Save to device. Strings and arrays of `extras/strings/<locale>/` are appended to the YouTube ones |
| `addons/morphe/` | Changes of the mod to official Morphe files (`git apply` patches, such as features sent to Morphe that are not merged yet; skipped once Morphe includes them, the build fails if one no longer applies). Their new strings in other languages go to `extras/strings/`; strings that Morphe already has are left out |
| `addons/yavot/` | Fixes applied to the YaVoT add-on before it is built (`git apply` patches; skipped once YaVoT includes them) |

Variant options (package, theme defaults) are set in `build_variant` calls of `.github/workflows/release.yml`.

## Downloads (Cloudflare R2)

Published builds are uploaded to the R2 bucket `R2_BUCKET` (the variable `DL_BASE_URL` is required to publish; the APKs are not attached to the GitHub release): the APKs to `apk/`, the update info to `updates/<variant>.json`. The apps check `DL_BASE_URL/updates/<variant>.json` and download the APK from `DL_BASE_URL/apk/`, so they no longer depend on GitHub. The APKs of the last 3 builds are kept. `cloudflare/worker.js` is the Worker (`cdn`) that serves the bucket (binding `BUCKET`, read only). `updates/*.json` in this repository stays up to date for builds that still check GitHub.

With the optional D1 binding `STATS` (database `stats`), the Worker counts per day the APK downloads per file and the update checks per variant, which is about the number of installs in use; only the path, the day and a count are stored. The page `DL_BASE_URL/stats?key=<STATS_KEY>` shows the counts, once the secret `STATS_KEY` is set on the Worker (Settings → Variables and Secrets); without it the page does not exist. Ready-made queries for the D1 console: `cloudflare/stats.sql`.

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
