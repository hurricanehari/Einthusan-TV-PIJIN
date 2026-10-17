# Elil TV v0.8 — In-app Updates & Cleaner Titles

This is a **complete Android source project** built on Elil TV v0.7.3. It retains the native player, Premium login, Fire TV keep-awake, all household profiles, My List, Continue Watching, progress tracking and movie metadata.

## Changes

- New **App Updates** page in the sidebar. Checks the `latest` GitHub Release, compares version tags against the installed app, and offers user-initiated APK download/install.
- Downloads only the release asset named **`Elil-TV.apk`** from the official repository, with a size limit.
- Verifies that the downloaded APK has the **same package ID**, **higher versionCode** and **same signing certificate** as the currently installed Elil TV. Fails closed if verification is unavailable.
- Uses Android FileProvider to hand the APK to the Fire TV system installer. No silent installation or removal of user consent.
- Shrinks featured/movie detail headings 34sp -> 28sp, section headings 24sp -> 20sp, card titles 14sp -> 13sp. Keeps brand identity readable.
- Package `com.example.einthusantv` preserved; v0.8 uses versionCode `10`.

## Step 1: Create ONE permanent signing key **on your own Mac**

**Never upload the `.jks` file to your public GitHub repo or share the password in chat.**

Run locally in Terminal (requires JDK / `keytool`):

```bash
keytool -genkeypair -v \
  -keystore "$HOME/elil-tv-release.jks" \
  -alias elil-tv \
  -keyalg RSA -keysize 3072 -validity 10000
```

Choose a strong keystore/key password and record them privately. Back up the `.jks` file offline; **losing it will break normal future upgrades**.

On macOS, copy the keystore as Base64:

```bash
base64 -i "$HOME/elil-tv-release.jks" | tr -d '\n' | pbcopy
```

## Step 2: Create GitHub repository secrets

Visit your repository > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**. Add:

| Secret | Value |
|---|---|
| `ELIL_KEYSTORE_BASE64` | The Base64 text copied on your Mac |
| `ELIL_KEYSTORE_PASSWORD` | Your keystore password |
| `ELIL_KEY_PASSWORD` | Your key password (possibly the same) |

The key alias must be `elil-tv` (matches the `keytool` command). The passwords and key are never committed to source control.

## Step 3: Upload project & full workflow

Upload the **entire** folder `ElilTV-PIJIN-v08/` to the root of the existing repo. Create `.github/workflows/build-v08.yml` with the **complete content** of the included `build-v08.yml` (also delivered as a standalone download).

Open **Actions > Build Signed Elil TV v0.8 > Run workflow** to produce a signed APK artifact. **Check the Android build succeeds before publishing anything.**

## Step 4: Publish release

After verifying the build, go to repo **Code > Releases > Draft a new release**. Make a `v0.8` tag pointing to the tested v0.8 commit, title the release `Elil TV v0.8`, and attach the signed APK renamed **`Elil-TV.apk`**. Mark it as the latest stable release. Alternatively, pushing `v0.8` tag triggers this workflow to automatically publish the release, provided Actions has contents write access and no release with the same tag already exists.

**Do not upload the debug APK**. The app checks for the exact `Elil-TV.apk` asset in the public release.

## Step 5: Test from v0.8 onward

- Install the signed v0.8 APK on a test Fire TV. Approve installation from this source where Fire TV requests it.
- Open `App Updates`: you should see `0.8 is up to date` once v0.8 is published as the latest release.
- For v0.9, compile with **the same signing key**, increase `versionCode` to 11 or above, set `versionName` to `0.9`, and publish a release tagged `v0.9` with an asset named `Elil-TV.apk`.
- Open v0.8 `App Updates` > Check for updates > Download & install. It must prompt the Fire TV system installer, and must update rather than uninstall.
- Check that household profiles and viewing progress survive the update.

## Important: Current debug APK migration

Older Elil builds were debug-signed on GitHub Actions runners. Your old debug APK may be signed with a **different key** from the first permanent release. Android does not allow a normal in-place upgrade between unrelated signing certificates. You may need a **one-time uninstall**, which can **erase app-local profiles, watchlists, viewing history and saved login**. **Do not uninstall your current working app until you have a satisfactory data migration/backup plan.** This v0.8 update feature cannot recover data erased by uninstalling an older APK. The release key cannot be reconstructed from an installed APK. Once you migrate to the permanent signing key, future releases can update normally.

## Known limitations

- **Source has not been compiled or tested on Fire TV.** You must run GitHub Actions and test on device.
- On Fire OS variants, installation prompts and permissions may differ. The app requests Android's ordinary installer, not silent updates.
- GitHub API availability/rate limits and public Releases accessibility affect version checks.
- Updates appear only for published non-prerelease GitHub releases that have the expected `Elil-TV.apk` attachment.
- This is an independent private app and is not endorsed by Einthusan.
