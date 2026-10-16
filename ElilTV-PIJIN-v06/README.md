# Elil TV — Brought by PIJIN · v0.6

Private Fire TV app update focused on fixing artwork selection, title presentation, and layout clipping. The working native catalogue, native playback, and in-app membership implementation from v0.5 are retained unchanged except for required metadata/UI improvements.

## Fixes
- **Film artwork:** select the film cover linked to its watch URL; prefer `/moviecovers/` and poster URLs and explicitly reject `/prof/` cast headshots. Later duplicate anchor links cannot overwrite a good film poster with an actor image.
- **Promotional labels:** `Must Watch` and similar trailing labels are removed from movie titles and displayed as a small separate gold subtitle.
- **No cropped subtitles:** expand card height and rail height; reserve separate lines for film title, promotional badge, and `ULTRA HD • Tamil`.
- **Hero banner:** larger hero area with more room for copy and buttons; fixes bottom cutoff on the Discover / original experience panel.
- **Remote navigation:** allow focus scaling without child clipping, make the left menu scroll on short display resolutions, adapt browse columns to available screen width.
- **Film details:** taller details area with a separate badge and room for actions.

## Installation
1. Upload the whole `ElilTV-PIJIN-v06` project folder to the root of `hurricanehari/Einthusan-TV-PIJIN` (beside v0.5).
2. Create `.github/workflows/build-v06.yml` with the **full replacement contents** of `build-v06.yml` from this project. Do not mix v0.5 and v0.6 contents.
3. In Actions run the v0.6 workflow; download the debug APK artifact, extract APK, and sideload it as usual.

## Notes
- Not built or Fire TV tested in this environment; GitHub Actions compilation and real-device layout/artwork still need to be checked.
- Native login, entitlement checks and service streaming remain subject to Einthusan's live behaviour.
- Do not publish a debug APK for general public distribution or add account credentials to GitHub.
- Original EinTamil MIT licence retained in EINTAMIL_SOURCE_LICENSE.txt. Independent client, not endorsed by the service.
