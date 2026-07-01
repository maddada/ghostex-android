# Ghostex Android

<!--
CDXC:AndroidBranding 2026-05-17-16:38:
The Android submodule is a release surface for `maddada/ghostex-android`, not
an upstream Termux distribution page. Keep the README focused on Ghostex
Android while documenting the Termux lineage and package-id constraint so
future upstream syncs remain understandable.
-->

Ghostex Android is a Termux-based Android app for connecting to persistent Ghostex sessions already running on your Mac.

<!--
CDXC:AndroidSshTransport 2026-05-20-12:59:
Production Android builds use the app-owned SSHJ transport for reconnect,
session inventory, actions, attach, and uploads. Keep release docs explicit so
publishing does not regress to the removed phone-side OpenSSH/sshpass setup.
-->

The app reaches the Mac over Tailscale SSH, uses the app-owned SSHJ transport, asks the Ghostex CLI for the live sidebar session list, and attaches the Android terminal surface to selected ZMX-backed sessions. Local Termux terminal management is intentionally hidden behind the Ghostex drawer.

## Current Scope

- Connect to one or more saved SSH machines from the Ghostex drawer.
- Automatically reconnect to the last selected machine when the app reopens.
- Open Tailscale from onboarding, setup, and recovery paths.
- Use built-in SSHJ for phone-side SSH instead of installing OpenSSH or `sshpass`.
- List only ZMX-backed Ghostex sessions for the first Android release.
- Keep the last seven opened remote sessions warm for faster switching.
- Use long-press action sheets for sidebar actions that are hover controls on macOS.

## Setup

1. Install and sign in to Tailscale on the Mac.
2. Enable macOS Remote Login in System Settings > General > Sharing.
3. Install Ghostex on the Mac and make sure the `ghostex` CLI is available in the SSH login shell.
4. Install `zmx` on the Mac and set Ghostex session persistence to `zmx`.
5. Verify the Mac SSH login shell can see both commands:

   ```sh
   command -v ghostex && command -v zmx
   ```

6. Install and sign in to Tailscale on the Android device.
7. Open Ghostex Android, then add the Mac as a saved machine.

## Release Verification

<!--
CDXC:AndroidReleaseE2E 2026-05-17-20:54:
Final release verification should start from the root `npm run
android:verify-release` runner so Mac CLI readiness, Android source gates,
signing, checksum, signature, live E2E, and first-run UI smoke proof stay one
operator path instead of being reconstructed from separate Gradle and adb
commands.
-->

Run the strict release proof from the repository root:

```sh
GHOSTEX_ANDROID_HOST=<tailscale-host-or-ip> \
GHOSTEX_ANDROID_USER=<ssh-user> \
GHOSTEX_ANDROID_PASSWORD=<ssh-password-if-needed> \
GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 \
GHOSTEX_ANDROID_REQUIRE_RELEASE_SIGNING=1 \
GHOSTEX_ANDROID_SIGNING_STORE_FILE=<external-keystore-path> \
GHOSTEX_ANDROID_SIGNING_STORE_PASSWORD=<keystore-password> \
GHOSTEX_ANDROID_SIGNING_KEY_ALIAS=<release-key-alias> \
GHOSTEX_ANDROID_SIGNING_KEY_PASSWORD=<release-key-password> \
npm run android:verify-release
```

This command runs the Mac `ghostex android-check --json` contract, root CLI
tests, Android shell harness syntax checks, unit tests, instrumentation
compilation, release APK/AAB builds, release gates, release lint, APK/AAB
checksum verification, APK/AAB signature verification, live connected-device
E2E, and first-run UI smoke. It requires a disposable connected Android
device/emulator and a Tailscale-reachable Mac using Ghostex with ZMX
persistence.

For local source/build validation without signing or a connected device, run
from the repository root:

```sh
npm run android:verify-release:local
```

That local mode builds and verifies release artifacts but explicitly skips the
connected-device E2E and first-run UI smoke, so it is not final release proof.

To inspect only the local release APK and Android App Bundle from inside this
Android submodule:

```sh
./gradlew :app:assembleRelease :app:bundleRelease :app:verifyGhostexReleaseChecksums
```

This produces and verifies `SHA256SUMS` beside the Ghostex release APKs and AAB in `app/build/outputs/apk/release` and `app/build/outputs/bundle/release`.

Run the first-run UI smoke on a connected Android device or emulator:

```sh
GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 \
GHOSTEX_ANDROID_UI_SMOKE_BUILD=release \
tools/ghostex-android-ui-smoke.sh
```

The UI smoke uninstalls any existing `io.ghostex`/`io.ghostex.test` packages, clears Ghostex Android app data on the selected device, installs the selected Ghostex Android APK, verifies the scrollable tutorial setup steps, and confirms Done opens Add Machine for a fresh user. `GHOSTEX_ANDROID_UI_SMOKE_BUILD=release` exercises the release candidate; `GHOSTEX_ANDROID_UI_SMOKE_APK=/path/to/signed-release.apk` can point at an externally signed artifact. Use a disposable emulator/device or confirm there is no real Ghostex Android data to preserve before setting `GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1`.

Run the live device E2E harness before treating a build as release-ready:

```sh
GHOSTEX_ANDROID_HOST=<tailscale-host-or-ip> \
GHOSTEX_ANDROID_USER=<ssh-user> \
GHOSTEX_ANDROID_PASSWORD=<ssh-password-if-needed> \
GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 \
tools/ghostex-android-device-e2e.sh
```

The E2E harness uninstalls any existing `io.ghostex`/`io.ghostex.test` packages, installs the debug app/test APKs on a disposable connected Android device or emulator, starts Ghostex Android, and verifies Tailscale SSHJ reachability, remote `ghostex` and `zmx`, ZMX inventory parsing, direct zmx attach for live provider rows, stable-id attach fallback, and a stable-session-id remote action.

If `GHOSTEX_ANDROID_PASSWORD` is set, the harness streams it into the debug app's private E2E storage and removes the staged files after the run; it is not forwarded as a Gradle instrumentation argument, written through host temp files, or written through shared Android temp storage. The instrumentation test deletes the private password file after the first read attempt, including failed reads, and keeps the value only in the test process for the remaining E2E methods.

## Package Identity

Ghostex Android now uses the side-by-side runtime package id `io.ghostex`, so it can install next to upstream Termux (`com.termux`) on the same phone.

The bundled Termux bootstrap is still built around an absolute private prefix, so the build generates local patched bootstrap archives for `/data/data/io.ghostex/files/usr` before native packaging. The `io.ghostex` id intentionally matches the byte length of `com.termux`; a longer package id requires Ghostex-built bootstrap and package artifacts for that new prefix.

## Upstream Lineage

This submodule is based on the upstream [termux/termux-app](https://github.com/termux/termux-app) codebase. Ghostex-specific work should stay modular under `com.termux.app.ghostex`, Ghostex layouts/resources, and narrow upstream wiring changes so the project can continue to sync with Termux.

Upstream Termux documentation remains available at:

- https://github.com/termux/termux-app
- https://termux.dev
