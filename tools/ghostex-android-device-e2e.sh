#!/usr/bin/env bash
set -euo pipefail

#
# CDXC:AndroidReleaseE2E 2026-05-17-16:30:
# Release QA needs one command that proves the Android runtime, not the host
# Mac, can reach a Tailscale SSH machine, run Ghostex CLI/zmx checks, parse the
# remote sidebar inventory, and execute a stable-session-id action.
#
# CDXC:AndroidReleaseE2E 2026-05-17-18:39:
# Optional SSH passwords for live release QA must not be passed as Gradle or
# instrumentation command-line arguments or shared device temp files. Stream
# them from the QA shell environment into the debug app's private storage
# without a host temp file, and remove the staged files after the test run.
#
# CDXC:AndroidReleaseE2E 2026-05-17-19:22:
# The live E2E harness installs a debug app and instrumentation APK. Require
# the same disposable-device confirmation as UI smoke, then uninstall the
# runtime and test packages before install so a signed release candidate or
# existing Termux install cannot block the debug proof with signature mismatch.
#
# CDXC:AndroidReleaseE2E 2026-05-17-19:34:
# Uninstalling Ghostex Android for disposable-device safety also removes any
# previously installed phone-side OpenSSH/sshpass packages. Make the live E2E
# harness self-contained by waiting for the fresh app bootstrap, then installing
# those tools inside the app-private runtime before instrumentation.
#
# CDXC:AndroidSideBySideInstall 2026-05-17-23:39:
# Live device E2E must not uninstall or clear upstream Termux when Ghostex
# Android is being tested side-by-side. Derive adb run-as, prefix, and launcher
# targets from the Ghostex runtime package helper.
#

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/ghostex-android-adb.sh"

usage() {
  cat <<'EOF'
Usage:
  GHOSTEX_ANDROID_HOST=<tailscale-host-or-ip> \
  GHOSTEX_ANDROID_USER=<ssh-user> \
  [GHOSTEX_ANDROID_PASSWORD=<ssh-password>] \
  [GHOSTEX_ANDROID_PORT=22] \
  [GHOSTEX_ANDROID_SESSION_ID=<session-id>] \
  [GHOSTEX_ANDROID_ALLOW_EMPTY_SESSIONS=1] \
  [GHOSTEX_ANDROID_DEVICE=<adb-serial>] \
  [GHOSTEX_ANDROID_ADB=/absolute/path/to/adb] \
  GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 \
  tools/ghostex-android-device-e2e.sh

What this proves:
  1. A real Android device/emulator is reachable over adb.
  2. The Ghostex Android debug APK and instrumentation APK install cleanly.
  3. The on-device Termux runtime can find phone-side ssh/sshpass as needed.
  4. The device reaches the supplied Mac over Tailscale SSH.
  5. The Mac SSH login shell can run ghostex and zmx.
  6. ghostex android-check --json verifies zmx, Ghostex zmx persistence, and bridge inventory.
  7. ghostex sessions --json returns ZMX-backed sessions.
  8. The selected session's attach command uses ghostex attach --session-id.
  9. A stable --session-id Ghostex CLI action works from the Android runtime.
  10. The harness can prepare phone-side OpenSSH/sshpass after a fresh install.

Before running:
  - Install and sign in to Tailscale on the phone/emulator and Mac.
  - Enable Remote Login on the Mac.
  - Start Ghostex on the Mac with Session persistence set to zmx.
  - Use a disposable device/emulator, because this script uninstalls Ghostex
    Android after GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 is set.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    echo >&2
    usage >&2
    exit 2
  fi
}

adb_cmd() {
  if [[ -n "${GHOSTEX_ANDROID_DEVICE:-}" ]]; then
    "$ADB_BIN" -s "$GHOSTEX_ANDROID_DEVICE" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

termux_run() {
  local command="$1"
  adb_cmd shell run-as "$runtime_package" "$prefix/bin/bash" -lc \
    "export HOME=$home PREFIX=$prefix TMPDIR=$prefix/tmp PATH=$prefix/bin:/system/bin:/system/xbin; $command"
}

wait_for_termux_bootstrap() {
  local attempt
  for attempt in $(seq 1 90); do
    if adb_cmd shell run-as "$runtime_package" sh -c "test -x files/usr/bin/pkg" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for Ghostex Android bootstrap to install pkg." >&2
  return 1
}

install_phone_ssh_tools() {
  wait_for_termux_bootstrap
  if termux_run "command -v ssh >/dev/null && command -v sshpass >/dev/null" >/dev/null 2>&1; then
    return 0
  fi
  echo "Installing phone-side OpenSSH and sshpass in the fresh Ghostex Android runtime..."
  termux_run "pkg update -y && pkg install -y openssh sshpass"
  termux_run "command -v ssh && command -v sshpass" >/dev/null
}

require_env GHOSTEX_ANDROID_HOST
require_env GHOSTEX_ANDROID_USER

if ! ADB_BIN="$(resolve_ghostex_android_adb)"; then
  explain_ghostex_android_adb_missing
  exit 127
fi

project_dir="$(cd "$script_dir/.." && pwd)"
cd "$project_dir"
runtime_package="$(ghostex_android_package_name)"
test_package="$(ghostex_android_test_package_name)"
prefix="$(ghostex_android_prefix)"
home="$(ghostex_android_home)"

require_ghostex_android_device "$ADB_BIN" "${GHOSTEX_ANDROID_DEVICE:-}"
require_ghostex_android_data_clear_confirmation "GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA"

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

apk_path="$(find app/build/outputs/apk/debug -name 'ghostex-android_*_universal.apk' -print | sort | tail -n 1)"
test_apk_path="$(find app/build/outputs/apk/androidTest/debug -name '*.apk' -print | sort | tail -n 1)"

if [[ -z "$apk_path" || -z "$test_apk_path" ]]; then
  echo "Could not find debug APK outputs." >&2
  exit 1
fi

adb_cmd uninstall "$runtime_package" >/dev/null 2>&1 || true
adb_cmd uninstall "$test_package" >/dev/null 2>&1 || true
adb_cmd install -r "$apk_path" >/dev/null
adb_cmd install -r "$test_apk_path" >/dev/null
adb_cmd shell am start -n "$runtime_package/com.termux.app.TermuxActivity" >/dev/null
install_phone_ssh_tools

cleanup_password_files() {
  adb_cmd shell run-as "$runtime_package" rm -f files/ghostex-e2e/password >/dev/null 2>&1 || true
}
trap cleanup_password_files EXIT

if [[ -n "${GHOSTEX_ANDROID_PASSWORD:-}" ]]; then
  printf '%s' "$GHOSTEX_ANDROID_PASSWORD" | adb_cmd shell run-as "$runtime_package" sh -c \
    "mkdir -p files/ghostex-e2e && cat > files/ghostex-e2e/password && chmod 600 files/ghostex-e2e/password"
fi

args=(
  -Pandroid.testInstrumentationRunnerArguments.ghostexHost="$GHOSTEX_ANDROID_HOST"
  -Pandroid.testInstrumentationRunnerArguments.ghostexUser="$GHOSTEX_ANDROID_USER"
  -Pandroid.testInstrumentationRunnerArguments.ghostexPort="${GHOSTEX_ANDROID_PORT:-22}"
)

if [[ -n "${GHOSTEX_ANDROID_SESSION_ID:-}" ]]; then
  args+=("-Pandroid.testInstrumentationRunnerArguments.ghostexSessionId=$GHOSTEX_ANDROID_SESSION_ID")
fi
if [[ "${GHOSTEX_ANDROID_ALLOW_EMPTY_SESSIONS:-}" == "1" ]]; then
  args+=("-Pandroid.testInstrumentationRunnerArguments.ghostexAllowEmptySessions=1")
fi

if [[ -n "${GHOSTEX_ANDROID_DEVICE:-}" ]]; then
  ANDROID_SERIAL="$GHOSTEX_ANDROID_DEVICE" ./gradlew :app:connectedDebugAndroidTest "${args[@]}"
else
  ./gradlew :app:connectedDebugAndroidTest "${args[@]}"
fi
