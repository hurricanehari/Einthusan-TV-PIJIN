# Elil TV v0.9 — cinematic redesign

Fire TV remote-first native Android app; preserves all existing playback, household profile, premium-session and updater code from v0.8.1.

## New in v0.9
- Navy and gold hero-first home screen with six or more film cards visible per row on typical Fire TV resolutions.
- Full-width catalogue and dynamically sized film cards.
- Left-side drawer hidden until pressing LEFT beyond the first card or pressing MENU. RIGHT and BACK close the drawer.
- Compact navigation with Settings housing membership, profiles, language, collections and app updates.
- Cold-start branded launch screen (~1.25 seconds) and new landscape launcher banner / square icon.
- Sticky account status remains visible after sign in.
- `versionCode 12`, `versionName 0.9.0`, same applicationId and permanent signing configuration as v0.8.

## Release process
1. Upload `ElilTV-PIJIN-v09` into the repository root.
2. Create `.github/workflows/build-v09.yml` from the supplied workflow in chat.
3. Run the workflow manually and check APK signing.
4. Once successful, create tag `v0.9.0` to have workflow upload `Elil-TV.apk` to GitHub Releases.
5. On existing Elil TV v0.8, go to App Updates and install over the old app; do NOT uninstall.

## Limitations
- TV-specific launcher banner behavior varies across Fire TV models and sideloaded launchers; edge-to-edge tile display cannot be guaranteed.
- The app targets API 28 with the existing narrow lint exception; migrate target API with regression testing.
- Source assets and remote-navigation changes need build and device testing before being considered production-ready.
