# Elil TV — Brought by PIJIN v0.7.2

This is a **complete source replacement project** based on v0.7.1, preserved in a separate folder. Not compiled or tested on Firestick yet.

## Changes
- On launch, restore encrypted first-party session cookies and verify the Einthusan account.
- Optional **Remember my login** checkbox stores your password encrypted using the Firestick's Android Keystore (AES-256-GCM), only after successful account verification. When a session expires, Elil TV can automatically sign in again on startup. No password is included in code, Github or log output.
- Sign out clears both session data and saved credentials. Profile histories remain separate.
- Android backups of app data are disabled to prevent unusable/recoverable credential backups on another device.
- Native GET requests follow a same-domain redirect once, instead of treating every first redirect as a hard failure. Premium checks remain enforced by the service.
- Player video background, shutter and system bars are black (black letterboxing for unmatched aspect ratios). This does not remove any white lines already encoded into the video itself.
- Pressing Back during playback hides the visible playback controls first. A subsequent Back exits.
- Play or Resume receives initial focus on the film-details screen.
- Search appears above Home on the sidebar.
- Existing profiles, My List, watch progress, native playback and catalogue retained.

## Build
Upload the entire `ElilTV-PIJIN-v072` folder to your repository root. Create `.github/workflows/build-v072.yml` with the **complete content** of `build-v072.yml`. Run `Build Elil TV v0.7.2 APK` via GitHub Actions. The APK artifact is produced by the workflow.

## Important
Persistent login uses first-party session cookies and, when opted in, an encrypted password for reauthentication. Provider security challenges, password changes, server revocation or service changes may still require manual login. One Einthusan account is shared by local household profiles. Avoid uninstalling the app if you want to retain local watch progress. This project is an independent personal integration; not affiliated with the film provider.
