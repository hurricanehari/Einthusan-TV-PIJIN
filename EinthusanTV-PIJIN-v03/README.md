# Einthusan TV — PIJIN Edition v0.3 (native integration prototype)

**What's new**: Kodi-inspired native catalogue with language filters, recent/featured/popular/staff picks, movie posters, search, pagination, an in-app Media3 HLS player, and a separate WebView fallback. No browser is used by the native catalogue/player screens.

## How it works

- `EinthusanClient.java` adapts `omega/plugin.video.einthusan/main.py` from https://github.com/eintamil/repository.eintamil (MIT licensed, see credits).
- Catalogue pages are fetched over HTTPS and parsed with Jsoup. Results include movie IDs, language, posters, description, next-page links.
- On selection, the player requests the normal watch page and its AJAX outcome endpoint with session cookies and headers, then extracts an HLS link as the Kodi add-on does.
- `PlayerActivity.java` uses AndroidX Media3 ExoPlayer to stream HLS video, including relevant HTTP headers. No Kodi dependency.
- Premium/login flows are **not** implemented. The regular website player remains accessible as fallback. No circumvention of authentication or DRM.

## Status & known limitations

This is **source code, not a compiled or Firestick-tested APK**. Android SDK and Gradle are not installed in the environment used to create the source, and live fetching of the Einthusan website was unavailable. The Kodi logic is from the provided code and may need adjustment to the site's live HTML and AJAX responses.

- **Most likely issue**: catalogue selection depends on current `.block1` markup and featured `newrelease_tab` markup; some posters may use lazy image attributes not captured.
- HLS tokens may require session headers/cookies; playing streams from the local Android session rather than Kodi may vary.
- The user may need to uninstall an older `app-debug.apk` before installing v0.3 if GitHub CI generated a new debug signing certificate. The package ID is kept as `com.example.einthusantv`.
- Preserve your working v0.1 / v0.2 APK as fallback.

## Build on GitHub

1. Extract the ZIP. Upload `EinthusanTV-PIJIN-v03` to the root of your existing `hurricanehari/Einthusan-TV-PIJIN` repository, next to the existing v0.1/v0.2 folders; *don't upload the ZIP file without extracting it.*
2. In GitHub web, create `.github/workflows/build-v03.yml` using the text in the ZIP's `build-v03.yml` (or upload the included hidden `.github` folder correctly). It will appear as **Build PIJIN Edition v0.3 APK** in Actions.
3. Run the workflow. Download the APK ZIP in Artifacts.
4. Create a `v0.3` GitHub release and attach the APK. Generate a Downloader code for the APK release asset as with earlier versions.
5. Install and test on Fire TV Stick 4K. Check native films, poster and search, pagination, selection, native playback and fallback.

## Troubleshooting

- Catalogue blank: Check whether the selected result page opens in the separate website fallback; the page HTML selectors may require updates.
- 'Native playback unavailable': That film may be premium-only, busy, or the AJAX response may have changed.
- Video playback error: Try the website fallback. The resolved HLS request may need additional headers or cookies.
- If build fails: Copy the first `What went wrong` block from GitHub Actions, not the entire stack trace.

## Credits

Based on the MIT-licensed EinTamil Kodi add-on (omega `plugin.video.einthusan`). Credits to humla, reasonsrepo and eintamil as listed in its source. All ownership of Einthusan branding, catalogues and movie streams belongs to the applicable rights holders. Independent personal development prototype; not affiliated with Einthusan or EinTamil.
