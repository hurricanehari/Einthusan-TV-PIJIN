# Elil TV v0.9.4 — Premium Fire TV UI

Native Fire TV interface update based on the user's agreed navy, slate and gold visual direction. It retains the existing Einthusan catalogue client, local profiles, resume/watchlist persistence and signed GitHub Release updater. No TMDB, TV series, or new remote catalogue dependencies have been introduced.

## What's new

- Home-screen top navigation: Home, Movies, My List and Search; film-first visual style.
- Sidebar only opens when moving beyond the left edge or pressing Menu; no TV Series/Help placeholders.
- Sidebar: fixed top links with a **separate fixed account footer**. Pressing Up from Home or Down from Settings stops at that boundary.
- Sign-in state shown on the pinned footer and the main header; account form feedback remains above scrolling content.
- New native Search page instead of a stock Android search dialog.
- Less explanatory content in the collection page; denser artwork-first rows.
- Adjusted hero and poster focus styles; backdrop still uses existing film poster sources, *not* new widescreen backdrops.
- Native custom playback overlay: elapsed/total timeline, gold progress, single-press left/right 10-second seek; hold left/right continuous scrub; dedicated FF/RW cycles 2× → 4× → 8× → 2×; Play resumes normal speed. Trick-play is simulated by repeated timeline seeking, since reliable reverse playback is not supported by the existing video source.
- Buttons are accessible as on-screen clickable TV controls; physical Media keys and directional controls are handled by the Activity.
- First Back hides the visible player overlay; a subsequent Back returns to the catalogue.
- Kept the existing restored-focus mechanism: return from film details to the original film and scroll position.

## Important limitations

- **Not independently Android-built or Fire TV-tested in this environment.** Build in GitHub Actions and test on the actual device before calling the implementation finished.
- The current catalogue provides mainly portrait posters. A pixel-perfect match to the cinematic preview's landscape actor imagery is not possible without appropriate artwork. This release refines the layout around what is already available.
- Trick-play uses simulated timeline scrubbing. HLS seeking may buffer, and performance is source/device dependent. It does not change actual video speed and provides no reverse audio.
- There is no TV Series catalogue, new third-party artwork API, or new content-streaming source in this release.
- The existing `targetSdk 28` / narrowly scoped lint exception remains pending a separate SDK upgrade and Fire TV behaviour test.

## Build and release

Android application ID `com.example.einthusantv`, versionName `0.9.4`, versionCode `16`.

Compile with Java 17, Gradle 8.9 and Android SDK 35. Run `gradle assembleRelease` with `ELIL_KEYSTORE_FILE`, `ELIL_KEYSTORE_PASSWORD`, and `ELIL_KEY_PASSWORD` exported in GitHub Actions; the separate GitHub secret `ELIL_KEYSTORE_BASE64` restores the key file. Keep using the **same permanent signing keystore** as v0.8–v0.9.3.

Tag the release `v0.9.4`, attach the built signed APK using the filename `Elil-TV.apk` and publish. Update from the app; don't uninstall an existing signed release.

## Physical Fire TV acceptance tests

1. Drawer hidden by default. Open from leftmost poster; it animates smoothly and dims the content.
2. Up from Home and Down from Settings stop on those menu items. The profile name and accurate signed-in status remain visible at the bottom at 720p and 1080p.
3. Press Right or Back to close the drawer and return focus to the same film.
4. Browse to a film in the middle of a long collection, open its details and press Back; the same film and scroll positions should return.
5. Home hero Play and More Info are both visible and focusable. Row posters do not clip.
6. Single Left/Right in a playing film seeks 10 seconds; hold performs continuous scrubbing and stops on release.
7. FF button steps through 2×, 4× and 8×, then resets to 2×. RW does likewise backwards. Play resumes normal speed.
8. First Back in player hides controls, next Back exits. Controls auto-hide during play.
9. Check native Premium login (encrypted remember-login), Continue Watching, My List and profile separation.
10. Check `Settings → App Updates` and confirm in-place installation preserves data.
