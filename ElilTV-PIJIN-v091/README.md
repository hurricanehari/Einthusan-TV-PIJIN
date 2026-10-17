# Elil TV v0.9.1 — cinematic Fire TV interface

A rebuild of the TV interface over the existing v0.9 playback, login, household profile,
watchlist, viewing history, secure local authentication and in-app update components.

## What changes
- A full-bleed, borderless home hero with a gradient-treated film poster on the right.
  The image is the source catalogue's poster, not a guaranteed landscape film still.
- Small, unobtrusive Elil TV branding and status links over the hero image.
- A full Popular films carousel appears immediately after the hero, avoiding a sparse
  Continue Watching row when only one or two films have been started.
- More compact poster rows, responsive to the Fire TV's display density and available width.
- A hidden, animated navigation drawer. Press Left beyond the first focused poster or
  use the Menu button; Right or Back dismisses it.
- Film details share the same wide-artwork treatment. Plain focus controls replace
  oversized outlined navigation buttons.
- Existing branded cold-start splash screen, banner artwork and app icon retained.

## Compatibility
- `applicationId`: `com.example.einthusantv` (unchanged)
- `versionCode`: 13 (higher than v0.9's 12)
- `versionName`: 0.9.1
- The APK must be signed using the **existing** Elil TV permanent signing key.
- No SharedPreferences storage schema changes; normal upgrades should preserve data.

## Release steps
1. Upload the folder `ElilTV-PIJIN-v091` to the GitHub repository root.
2. Create `.github/workflows/build-v091.yml` by copying the complete YAML from ChatGPT.
3. Run workflow `Build Signed Elil TV v0.9.1` on `main`.
4. Confirm the green build and signed APK artifact.
5. Create tag `v0.9.1` to trigger the automatic upload of `Elil-TV.apk` to Releases.
6. On the installed Elil TV app: Settings → App updates → Check for Updates.
   Accept the Fire TV installer prompt. Do not uninstall the previous version.

## Limitations and testing
- Source structure, Java parsing, release metadata, manifest and artwork are checked,
  but compiling and Fire TV playback require GitHub Actions and device testing.
- Depending on the film, portrait artwork may not reproduce the ideal horizontal
  cinema still shown in an earlier concept image. True landscape backdrops require
  a legitimate additional data source.
- The current Android target SDK remains 28 with an existing narrowly scoped lint
  exception; raising it requires a separate compatibility review.
- Fire TV may choose the square application icon for sideloaded apps instead of the
  supplied wide launcher banner.
