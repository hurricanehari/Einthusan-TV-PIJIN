# Einthusan TV — PIJIN Edition v0.2 (Native TV Preview)

## What actually works
- Android TV-native home screen with PIJIN Edition branding and remote-focusable buttons.
- Existing website playback is retained in an embedded WebView under **Browse Einthusan**.
- Separate built-in **Media3 ExoPlayer** for user-provided, authorised HTTPS video URLs (MP4/HLS where supported).
- Back navigation, fullscreen website playback and About credit.

## Not implemented
- No native Einthusan catalogue, artwork, authentication or search integration.
- No automatic Einthusan stream extraction, nor guaranteed playback through Media3 from Einthusan.
- Movie pages still use WebView. This is not yet the user's final requested fully native streaming experience.

## Build and update
Repository structure retains an outer `EinthusanTV-PIJIN-Edition/` folder. Copy that directory's contents into the same folder in the GitHub repository, leaving the root `.github/workflows/build.yml` intact.
GitHub Actions on main compiles a debug APK and supplies it as an artifact.
The package ID is unchanged, allowing the APK to update the previous debug build if signing keys match; GitHub-hosted debug APK signing keys may change between workflow runs, requiring uninstall/reinstall.

## Security and rights
Only use direct video URLs for content you are authorised to access. This project does not bypass access controls or claim to be an official Einthusan client. Do not enter service credentials into an untrusted build.
