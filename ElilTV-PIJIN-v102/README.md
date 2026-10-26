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


## v1.0.1 — Fire TV sidebar and splash refinements
- Right while drawer is open closes it and restores prior film/content focus. OK/Enter activates the highlighted destination.
- Film details do not show the permanent icon rail or the overlay drawer; Back returns to the selected film.
- Narrow browsing rail remains a visual navigation hint and cannot steal focus.
- Opening splash image has one clean “BROUGHT BY PIJIN” caption.
- No changes to playback, authentication, profiles or updates; package ID and permanent signing key are unchanged.


## v1.0.2 — Reliable Firestick held-D-pad scrubbing

- Recognises held Left/Right by a 330 ms timer rather than relying solely on remote key-repeat events.
- Short taps remain exactly ±10 seconds.
- Scrub position is an integral of wall-clock held time: UI lag does not diminish seeking speed.
- Speed ramp: 5x (0–0.5s), 15x (0.5–1.5s), 30x (1.5–3s), 60x (3–4.5s), 120x (4.5–6.5s), 240x (>6.5s).
- A single seek is committed upon release; the player resumes its previous play/pause state.
- This is scrubbing of the timeline, not video decoding at 240x. Dedicated FF/RW remains unchanged.
- Player layout, app ID, signing setup, accounts and updater remain unchanged.
