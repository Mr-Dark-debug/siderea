# Releasing

Releases are built by GitHub Actions when a tag matching `v*` is pushed (`.github/workflows/release.yml`).
Release notes come from the matching section of `CHANGELOG.md`; the workflow fails if the section is missing.

```
# 1. bump siderea.versionName in gradle.properties, add a "## [x.y.z] - date" section to CHANGELOG.md
# 2. commit, then tag and push
git tag -a v0.2.0 -m "v0.2.0"
git push origin main v0.2.0
```

## Debug-signed vs release-signed

Until the four signing secrets exist, the workflow still publishes an installable APK, but it is signed with
the **debug key** and named `siderea-vX.Y.Z-debug-signed.apk`, with a banner in the release notes.

Android refuses to update an app that is signed with a different key. So once you add a release key, the first
release-signed build has to be installed after **uninstalling** any debug-signed one.

## Create the release key (once, on your machine)

Keep the keystore **outside the repository** and back it up somewhere safe. If you lose it you can never ship
an update that installs over existing copies.

```bash
keytool -genkeypair -v \
  -keystore ~/siderea-release.jks \
  -alias siderea \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` prompts for the keystore password, the key password and your name; choose strong passwords and keep
them in a password manager.

## Give the secrets to GitHub

None of these commands needs you to paste a secret into a chat or onto a command line. `gh secret set` reads
from standard input or prompts with hidden input.

macOS / Linux / Git Bash:

```bash
base64 -w0 ~/siderea-release.jks | gh secret set KEYSTORE_BASE64 --repo Mr-Dark-debug/siderea
gh secret set KEYSTORE_PASSWORD --repo Mr-Dark-debug/siderea     # prompts, input hidden
gh secret set KEY_PASSWORD      --repo Mr-Dark-debug/siderea     # prompts, input hidden
gh secret set KEY_ALIAS         --repo Mr-Dark-debug/siderea --body siderea
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\siderea-release.jks")) |
  gh secret set KEYSTORE_BASE64 --repo Mr-Dark-debug/siderea
gh secret set KEYSTORE_PASSWORD --repo Mr-Dark-debug/siderea
gh secret set KEY_PASSWORD      --repo Mr-Dark-debug/siderea
gh secret set KEY_ALIAS         --repo Mr-Dark-debug/siderea --body siderea
```

Check they are registered (values are never shown):

```bash
gh secret list --repo Mr-Dark-debug/siderea
```

## Signing a release locally

The same variables the workflow uses work locally:

```bash
export SIDEREA_KEYSTORE_FILE=~/siderea-release.jks
export SIDEREA_KEYSTORE_PASSWORD=...   # use your shell's hidden-input read, don't put it in history
export SIDEREA_KEY_ALIAS=siderea
export SIDEREA_KEY_PASSWORD=...
./gradlew assembleRelease
```

Verify any APK's signer:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
```
