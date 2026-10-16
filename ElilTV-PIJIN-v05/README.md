# ELIL TV — Brought by PIJIN · v0.5 (Fire TV prototype)

Personal native Fire TV client for the Einthusan catalogue. Independent of and not endorsed by Einthusan. Content, membership access and rights remain with their respective owners.

## This build

- New ELIL TV branding, custom Fire TV icon and banner, fixed left navigation, dark cinema-themed featured hero, horizontal collections, native full-screen details and TV remote focus highlighting.
- Native film browser and progressive catalogue pagination. Tamil/Hindi/Telugu/Malayalam/Kannada/Bengali/Marathi/Punjabi, Recently Added, Popular, Featured, Staff Picks, A-Z, Year, Decade, Rating and Search.
- Better title extraction: looks for headings within and beside the movie card, item attributes and image descriptions, then optionally checks the film's watch page for a title. Missing titles show **Title unavailable**, not an opaque internal movie ID. Live markup remains unverified.
- **In-app native email/password sign-in**, using HTTPS requests to the same first-party login AJAX endpoint referenced in the MIT-licensed EinTamil Kodi add-on. Passwords are never saved or added to logs, source or preferences. Android session cookies are maintained in memory and synchronized to the site's WebView fallback where possible.
- Sign-in distinguishes **verified** from **unverified** account status; merely posting login credentials cannot mark the user as signed in. Includes refresh status and clear-session sign-out.
- Selecting Premium Ultra HD when logged out takes the user to native sign-in; after verification, the app can return to the selected film and request playback.
- Native Media3 HLS streaming preserved. Playback displays decoded video resolution so 1080p isn't mislabeled as 4K.
- Website fallback remains separate and optional, not the primary login.

## Important accuracy and verification

- **Source-only development build: not yet compiled or Firestick-tested.** v0.4 HD native playback is previously confirmed by the user; v0.5 network/HTML/account/login changes are **not** confirmed on a real account or server.
- The public Einthusan Premium marketing page describes its UltraHD tier as **1080p** at the time of this build. Premium does not guarantee **2160p 4K**. The app requests the service's `uhd=true` quality path only after verified sign-in, and actual resolution depends on the service.
- Email/password login was adapted from the Kodi add-on and may need adjustment if Einthusan updated its AJAX response, session handling or verification markup. Google/social sign-in and 2FA are not implemented. Do not submit passwords into GitHub issues, code or build logs.
- Account verification relies on official account-page signals, which may be brittle; if unclear, it fails closed as **unverified** rather than falsely granting access. Login session currently is not guaranteed to survive app restart; refresh status checks again.
- Film title fallback may require further work for specific catalogue layouts. Avoid claims of fully verified catalogue metadata until device tests show real movie names.
- Preserve the working v0.4 APK; debug signing certificate changes can require uninstalling the prior debug APK.

## Build from GitHub

1. Extract the ZIP and upload the full `ElilTV-PIJIN-v05/` folder to the repository root alongside, not instead of, previous versions.
2. Create `.github/workflows/build-v05.yml` from the full contents of `build-v05.yml` included both at the project root and the supplied `.github/workflows` path in the ZIP.
3. Run **Build Elil TV v0.5 APK** in GitHub Actions, then download the `Elil-TV-PIJIN-v0.5-APK` artifact.
4. Extract `app-debug.apk`, install on Fire TV Stick 4K and test film names, pagination, in-app login verification, HD and Premium Ultra HD playback.
5. If native login fails, report only its displayed error (never your email, password or cookies).

## Licence and credits

Catalogue route and playback logic and the native email/password login pattern adapted with attribution from the EinTamil Kodi add-on (`omega/plugin.video.einthusan/main.py`), MIT licensed: https://github.com/eintamil/repository.eintamil . Its original licence text is included in `EINTAMIL_SOURCE_LICENSE.txt`.
