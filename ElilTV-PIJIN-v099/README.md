# Elil TV — Brought by PIJIN | v0.9.9

Fire TV–first South Asian film browser with native HLS playback, household profiles,
Continue Watching, My List, a film-only sidebar, Movies category tabs, a featured-film
carousel, and signed GitHub Releases in-app updates.

## Changes in v0.9.9 (based on v0.9.8)

- **Single D-pad Left/Right press:** still skips 10 seconds.
- **Hold Left/Right:** fast timeline scrubbing that accelerates according to actual
  hold duration: <0.5 s at 5×, 0.5–1.5 s at 15×, 1.5–3 s at 30×, >3 s at 60×.
  Seek on release and return to the previous playing or paused state.
- **Dedicated Fast Forward key:** successive presses request actual forward video
  playback at 2× → 4× → 8× → 2×. Audio is muted during accelerated playback,
  and the original volume is restored on return to normal speed. Very high-speed
  decoding/buffering depends on the Firestick, network and original video stream.
- **Dedicated Rewind key:** successive presses cycle a simulated backward timeline
  scrub at 2× → 4× → 8× → 2×; true reverse-video decoding is not supported here.
- **Hardware Play/Pause:** exits trick-play and returns to normal speed without
  summoning the on-screen overlay. OK continues to pause/resume and show the
  minimalist title, gold timeline and timestamps as in v0.9.5.

The only application logic change is `PlayerActivity.java`. Authentication, profiles,
viewing progress, browsing, artwork, app installer and update checking are identical
to v0.9.8. App versionCode is 21 and versionName is 0.9.9.

## Signed release

Build with GitHub Actions using the same three repository secrets:
`ELIL_KEYSTORE_BASE64`, `ELIL_KEYSTORE_PASSWORD`, `ELIL_KEY_PASSWORD`.
Never commit the private signing key or credentials. Keep application ID
`com.example.einthusantv` and alias `elil-tv` unchanged for in-place upgrades.

This ZIP has been integrity-tested and checked at source level; an Android compilation,
APK signing verification, and Fire TV runtime playback test must still be completed.
The project retains a narrow `ExpiredTargetSdkVersion` lint exemption for private
sideloading; this does not meet modern app-store publishing requirements.
