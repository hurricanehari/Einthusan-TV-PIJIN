# Elil TV v0.9.3 — Fluid Navigation & Focus Memory

Based on v0.9.2. Signed update-compatible: applicationId com.example.einthusantv, versionCode 15, same GitHub secrets/signing keystore.

## Changes
- Hard-stop sidebar D-pad at Home (Up) and Settings (Down).
- Save and restore existing catalogue view on returning from film details; original poster focus and vertical/horizontal scroll positions preserved.
- Returning from a film player does not rebuild the catalog and lose focus. Poster playback progress bars refresh in place.
- Softer navy/gold buttons, rounded poster artwork and navigation, focus lift/ease, 185–260ms page/drawer transitions.
- Hero occupies 271dp rather than 295dp, maintaining room for Play and More Info.

## Build
Gradle 8.9, Java 17, assembleRelease. Set ELIL_KEYSTORE_FILE, ELIL_KEYSTORE_PASSWORD, and ELIL_KEY_PASSWORD in CI. Never commit signing keys.

## Limits
Fire TV and Android visual testing are required. Catalogue provides film posters, not cinematic landscape backdrops. This is navigation and polish, not a complete replacement of live catalogue assets.
