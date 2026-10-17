# Elil TV v0.9.2 — Hero + remote-navigation fixes

This is the full Elil TV source based on v0.9.1. It maintains the same application
ID (`com.example.einthusantv`), permanent release-signing configuration, profile
and login data, existing player, playback and in-app updater.

## Fixes

1. Featured Play and More info now remain visible; the content stack is bottom-
   anchored and WRAP_CONTENT rather than clipped inside a fixed 238dp column.
2. D-pad **Down** while Explore is highlighted enters the **first film immediately
   below that Explore link**, rather than jumping to the Explore in the next row.
3. D-pad **Right** after the last film in a horizontal collection focuses that
   collection's **Explore** link. This also works on Continue Watching/My List.
   While other films remain to the right, Right still navigates those films.
4. D-pad **Left** from the first film still opens the hidden sidebar.

## Update-safe release

- `versionName`: 0.9.2
- `versionCode`: 14, incremented from v0.9.1's 13
- `applicationId`: `com.example.einthusantv` unchanged
- Same GitHub Secrets / permanent keystore as v0.8 onward
- Do not uninstall; update over the installed version to retain local data.

## Build and publish

1. Upload **ElilTV-PIJIN-v092/** to repository root.
2. Create **.github/workflows/build-v092.yml** from the complete ChatGPT code.
3. Run **Build Signed Elil TV v0.9.2** on main in GitHub Actions.
4. Once green, push/create tag `v0.9.2`; the tag build publishes `Elil-TV.apk`
   to the corresponding GitHub Release.
5. On Firestick: **Settings → App Updates → Check for Updates**.

## Test with the Fire TV remote

- Home featured: Play and More info visible in full and selectable.
- On **Popular → Explore**, press Down: first Popular poster is focused.
- On the last Popular poster, press Right: Popular Explore receives focus.
- Repeat on Continue Watching and Recently Added.
- From first poster, Left opens menu; Right dismisses the menu.

Code and layouts have been statically inspected; an actual Android release compile
and Firestick D-pad test are still required before considering this validated.
