# Elil TV — Brought by PIJIN v0.7.3: Fire TV Keep-Awake Fix

Complete project based on v0.7.2 with an isolated video-player change.

## Fix

Fire TV can activate its screensaver/sleep mode after a period without remote input, even while a film plays, unless the foreground video Activity requests that the screen remain awake.

`PlayerActivity` now sets Android `FLAG_KEEP_SCREEN_ON` (and the video-view equivalent) while the movie is playing or temporarily buffering, and removes the flag when paused, ended, errored, or when the player Activity leaves the foreground. It does not change Fire TV's normal idle-sleep behaviour on the catalogue screens.

## Preserved from v0.7.2

- Official-account sign-in with saved session and optional encrypted remember-login
- Native Media3 playback, HD/Ultra HD requests and first-Back-hides-toolbar behaviour
- Play-button initial focus, Search sidebar position, film discovery and metadata
- Separate household profiles, My List, Continue Watching and resume points

## Build with GitHub Actions

1. Extract `Elil-TV-PIJIN-v0.7.3-Keep-Awake.zip`.
2. Upload `ElilTV-PIJIN-v073/` as a **new folder** in the repository root, retaining the previously working v0.7.2.
3. Create `.github/workflows/build-v073.yml` using the complete supplied workflow file.
4. Push/commit on `main`, then use Actions > Build Elil TV v0.7.3 APK.
5. Extract the APK from the uploaded workflow artifact and sideload on the Firestick.

This source has had Java syntax checked but is not yet compiled with the Android SDK or tested on a physical Firestick. Current GitHub debug APK signing certificates may differ between builds. If an uninstall is needed, your locally saved account session, watchlists and progress could be removed; preserve these before proceeding.

## Expected test

Start a film and leave the remote idle longer than the normal Fire TV sleep interval. It should stay awake. Pause it and ordinary power saving may resume. Close playback and the Firestick should sleep normally when idle. Independent TV/HDMI-CEC sleep timers cannot be controlled by Android's keep-screen-on flag.

Sources: Android Developers Keep the screen on; Amazon Fire TV Requirements for Multimedia Apps (Keeping the Device Awake).
