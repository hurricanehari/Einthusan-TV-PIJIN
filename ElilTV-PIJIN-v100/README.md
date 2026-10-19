# எழில் TV · Brought by PIJIN — v1.0.0

Fire TV-first app based on v0.9.9. Native Einthusan browsing/player and existing household profiles are retained.

## v1.0.0 changes

- Tamil brand **எழில்** on the cold-start splash, **எழில் TV** on the home header and Fire TV launcher artwork.
- Raster Tamil wordmark avoids device-dependent Tamil font rendering. New 1920×1080 splash, 1280×720 launcher banner, 512×512 icon and transparent header asset.
- Application label changed; Android package ID remains `com.example.einthusantv` to support in-place updates.
- The launcher activity explicitly declares a TV banner and rectangular activity icon. **Fire OS may still use a padded square tile for sideloaded apps; this cannot be guaranteed.**
- One Play/Resume control on film details; no separate Ultra HD button. Defaults to Auto: request Ultra HD if labelled and account is signed in, otherwise standard playback. If UHD resolution fails, Auto retries the standard stream.
- Default Playback Quality in Settings: Auto / Standard / Ultra HD (when available). Setting is saved on the Firestick.
- During playback, press the Firestick **Menu (☰)** button to open the minimal quality chooser. It requests a fresh authorised stream and resumes at the current timestamp; a brief buffer may occur.
- Film details opened after a search explicitly focus Play after the page animation, rather than the profile/header controls.
- No changes to the provider client, playback seeking, profiles, account storage, or app updater.

## Requirements & release

Existing `ELIL_KEYSTORE_BASE64`, `ELIL_KEYSTORE_PASSWORD`, and `ELIL_KEY_PASSWORD` GitHub Actions secrets.
Upload this folder as `ElilTV-PIJIN-v100`, add `.github/workflows/build-v100.yml` and run on `main`. A release tag `v1.0.0` triggers the signed APK upload.

## Important limitations

- No provider entitlement or DRM bypass. Ultra HD availability still depends on the real stream and membership; the Ultra HD label is not a guarantee of resolution or access.
- In-player quality changing requires the Fire TV Menu key and may briefly reload/buffer.
- Android source passes local parser checks; a full APK build and device test remain required.
- Do not put your keystore or membership credentials in this repository.
