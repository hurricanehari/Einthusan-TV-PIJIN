# PIJIN Edition v0.4 — Native Fire TV Cinema Prototype

A private, TV-first Android app inspired by the EinTamil Kodi add-on. **Einthusan is a separate service.** This project is not an official app or endorsement.

## Changes since v0.3

- Native cinematic start screen with a featured artwork panel and 4 horizontal rows: Recently Added, Popular, Featured, Staff Picks.
- Language switching for Tamil, Hindi, Telugu, Malayalam, Kannada, Bengali, Marathi and Punjabi.
- **Full catalogue browsing across pages**. Einthusan often supplies **six films per webpage**, so PIJIN initially fetches up to four pages (around 24 films), then retrieves more as you scroll or press Load next. It is NOT a 6-film-only catalogue.
- A-Z, year, decade, rating-based collections and native search.
- More resilient extraction of film titles, headings, posters and descriptions (including the title in a sibling heading after the movie link).
- Official Einthusan Premium sign-in in the separate website screen. When you return to PIJIN, the native HTTP client reads the first-party website session cookie. **No custom password form and no password storage in PIJIN.**
- Film details with HD or Ultra HD playback action where the catalogue identifies Ultra HD availability. The UHD action makes the same quality-specific request as the Kodi add-on and leaves entitlement decisions to the service. **Premium membership, true 4K resolution and supported 4K codecs are not guaranteed or bypassed.**
- Better PIJIN app icon and banner.
- Existing separate website fallback retained.

## Build through GitHub

1. Upload the full `EinthusanTV-PIJIN-v04` folder alongside your existing v0.3 folder at your repository root, preserving its `app/src/main/...` subdirectories.
2. Create `.github/workflows/build-v04.yml` using the **entire contents** of `build-v04.yml` included with this ZIP.
3. Go to GitHub Actions -> **Build PIJIN Edition v0.4 APK**. Run the workflow.
4. Download the artifact `Einthusan-TV-PIJIN-v0.4-APK`, extract `app-debug.apk`, and install on Fire TV Stick 4K with Downloader as before.
5. Preserve your working v0.3 APK. Different debug signing keys may require uninstalling the earlier test APK before installing the new one.

## Device test checklist

1. Home screen has featured banner and horizontal category rows.
2. Tamil -> Browse All displays more than 6 films, and loads more at the end of the grid.
3. Movies have real titles rather than `Movie ABCD` IDs (site HTML parsing may still need tuning).
4. Switch language and try Recently Added / Popular / Staff Picks.
5. Try A-Z, Year and Search.
6. Play HD through the native player.
7. On an Ultra HD-tagged film, sign in on the official login page, return and try Play 4K. Check your TV's actual playback resolution; displaying the 4K option does not prove a UHD stream was delivered.
8. Check Firestick remote Up/Down/Select/Back behaviour and website fallback.

## Known limitations and security

- **Not built or tested on your Firestick in the development environment.** Source syntax and workflow packaging have been inspected, but live Android compilation and playback must be verified with GitHub Actions / Firestick.
- The catalogue parser depends on Einthusan's live HTML, pagination and session behaviour. Some posters/titles, genres or pages may still require adjustments.
- Website login uses Android WebView on the service's official domain; only its session cookie is read by the native client. Do not paste credentials into GitHub or logs. Logging of sensitive playback URLs/session cookies is deliberately avoided.
- No circumvention of subscription checks or content protection; access depends on the member's actual entitlement.
- No bulk scraping/downloading of the entire catalogue at once. Collections load a few pages on demand to avoid hammering the service.
- The app uses a development/debug APK. Not suitable for public distribution as-is.

## Code credits

Catalogue routes and direct playback scheme informed by the MIT-licensed EinTamil Kodi add-on, `omega/plugin.video.einthusan/main.py` from https://github.com/eintamil/repository.eintamil . Its MIT licence is retained as `EINTAMIL_SOURCE_LICENSE.txt`.
