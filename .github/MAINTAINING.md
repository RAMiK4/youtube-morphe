# Maintaining

## Release

1. **Source APK** — the original YouTube APK (arm64, full APK) is attached to the draft release `source`. Replace it when a new YouTube version is supported. Do not delete or publish this draft.
2. **Actions → Release YouTube Morphe → Run workflow**
   - `patches`: `stable` (latest stable Morphe release) or `dev` (latest dev release)
   - `publish`: unchecked builds a test APK into the run's *Artifacts* only

Every published build becomes the latest release. The workflow builds the official Morphe patches with the additions in `extras/`, applies them together with the latest YaVoT release (built from source with the fixes in `addons/yavot/`), verifies package name and signature, publishes the release and updates `updates/*.json` for the in-app update check.

The changelog is generated from the Morphe changelog (YouTube only): a stable version lists its own section, a dev version lists its own and the two previous dev sections (never past the previous stable one), newest first. Optional translations for the in-app dialog: `changelog/<language>.md` whose first line is `<!-- <patches version> -->`.

## Configuration

| File | Purpose |
|---|---|
| `settings/defaults.json` | Recommended settings (Morphe settings export). Each setting is applied once: on first install, or on the update that adds it. |
| `branding/` | App icon (`mipmap-*`), header logo and notification icon (`drawable-*`) |
| `extras/` | Additional patches: theme defaults, default settings, update check, restart on update, experimental notice |
| `addons/yavot/` | Fixes applied to the YaVoT add-on before it is built (`git apply` patches; skipped once YaVoT includes them) |

Variant options (package, theme defaults) are set in `build_variant` calls of `.github/workflows/release.yml`.

## Secrets (Settings → Secrets and variables → Actions)

| Name | Type | Value |
|---|---|---|
| `KEYSTORE_B64` | secret | Keystore, base64 |
| `KEYSTORE_PASSWORD` | secret | Keystore password (empty: Morphe default) |
| `KEY_ALIAS` | secret | Key alias (empty: `Morphe`) |
| `KEY_PASSWORD` | secret | Key password (empty: `Morphe`) |
| `CERT_SHA256` | variable | SHA-256 fingerprint of the signing certificate |
