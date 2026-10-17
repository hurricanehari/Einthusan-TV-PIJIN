# Elil TV — v0.8.1 (UI refinement)

Complete source based on v0.8. Uses **the same** package ID and release-signing secrets as v0.8.

## What's changed

- Featured title 28sp → 24sp; film detail title 28sp → 24sp; section titles 20sp → 18sp; film card titles 13sp → 12sp.
- Page headings capped at 24sp rather than 30sp.
- Account screen displays signed-in / signing-in / sign-in-failed status in a **fixed banner below the top navigation bar**, always visible while using the login form.
- Reduced excess spacing in the account form; no change to the first-party authentication implementation, passwords, cookie persistence, or provider entitlements.
- Android `versionCode 11`, `versionName 0.8.1`. Same `com.example.einthusantv` application ID, signing alias and protected keystore.
- Prior v0.8 feature set remains: continue watching, watchlists, household profiles, playback keep-awake, remote focus, GitHub-release update client.

## Build and publish

1. Upload entire `ElilTV-PIJIN-v081` folder to the GitHub repo root.
2. Create `.github/workflows/build-v081.yml` with the entire `build-v081.yml` file in this project (also provided in-chat).
3. Use GitHub Actions to verify the signed build. Then create a Git tag `v0.8.1` pointing at the new commit to trigger the tag workflow and publish the matching signed APK to GitHub Releases (or manually publish the built `Elil-TV.apk` as release `v0.8.1`).
4. In installed v0.8 use **App Updates → Check for updates**. Do NOT uninstall v0.8; this project is signed with the same keystore so Android can preserve your local data.

## Verification

Source edited and checked structurally; has NOT been compiled on GitHub Actions or validated on Firestick. If the updater rejects the signature, do not uninstall: verify both builds used the same signing key and application ID.

## Compatibility

The project retains `targetSdk 28` from v0.8. A narrow lint exception permits private Fire TV release builds while a future update can safely test targeting a more recent Android SDK. Do not assume this config meets current Play Store policy.
