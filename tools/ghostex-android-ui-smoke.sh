#!/usr/bin/env bash
set -euo pipefail

#
# CDXC:AndroidOnboarding 2026-05-17-16:59:
# Release QA needs a repeatable device smoke path for the first-run UX, not only
# source tests. This script verifies the required setup tutorial is visible,
# scrollable, and routes a first-time user into Add Machine before any remote
# Mac is available.
#
# CDXC:AndroidOnboarding 2026-05-17-19:11:
# Canceling the first Add Machine editor must not mark setup complete on a
# fresh install. Keep the device smoke path restarting after cancel so release
# QA proves the tutorial remains the first screen until an SSH machine is saved.
#
# CDXC:AndroidReleaseE2E 2026-05-17-19:15:
# Final UI smoke should exercise the installable release artifact when one is
# available, not silently rebuild only debug. Allow an explicit APK path for
# signed release candidates, prefer an existing release APK in auto mode, and
# keep debug as the standalone developer fallback.
#
# CDXC:AndroidReleaseE2E 2026-05-17-19:17:
# select_apk runs inside command substitution, so Gradle build logs must stay
# on stderr. Only the selected APK path may be printed to stdout or adb install
# receives a path polluted with build output.
#
# CDXC:AndroidReleaseE2E 2026-05-17-18:24:
# A fresh-install UI smoke run can delete real Ghostex Android data on the
# selected device. Require an explicit data-clear opt-in before install/pm-clear
# work.
#
# CDXC:AndroidReleaseE2E 2026-05-17-19:20:
# Final release proof runs debug instrumentation before release UI smoke, and
# debug/release APKs can have different signatures. After the explicit data
# clear confirmation, uninstall the runtime and test packages before installing
# the selected UI-smoke APK so release install is not blocked by a debug build.
#
# CDXC:AndroidConnectionManagement 2026-05-17-19:53:
# First-run UI smoke must also prove the saved-machine manager path, not only
# tutorial copy. Save a disposable loopback SSH machine through the real Add
# Machine form, restart the app, then verify the machine settings and action
# sheet surfaces are reachable without requiring a live Mac.
#
# CDXC:AndroidConnectionRecovery 2026-05-17-19:56:
# A saved loopback target should still prove reconnect recovery. After restart,
# accept either missing phone tools or SSH connection failure, then open the
# Setup panel so release QA covers the repair path users need before a live Mac
# is reachable.
#
# CDXC:AndroidSideBySideInstall 2026-05-17-23:39:
# UI smoke must install and launch Ghostex Android through its side-by-side
# package id so QA can run on phones that already have upstream Termux.
#

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/ghostex-android-adb.sh"

usage() {
  cat <<'EOF'
Usage:
  [GHOSTEX_ANDROID_DEVICE=<adb-serial>] \
  [GHOSTEX_ANDROID_ADB=/absolute/path/to/adb] \
  GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 \
  [GHOSTEX_ANDROID_UI_SMOKE_APK=/absolute/path/to/signed-release.apk] \
  [GHOSTEX_ANDROID_UI_SMOKE_BUILD=auto|release|debug] \
  [GHOSTEX_ANDROID_UI_SMOKE_DIR=/tmp/ghostex-android-ui-smoke] \
  tools/ghostex-android-ui-smoke.sh

What this proves:
  1. A real Android device/emulator is reachable over adb.
  2. The selected Ghostex Android APK installs and starts cleanly. Auto mode
     uses an existing release APK when present, otherwise builds debug for
     standalone development smoke runs.
  3. A fresh launch opens the required first-run tutorial.
  4. The tutorial is scrollable and contains the Mac, phone, Tailscale, SSH,
     Ghostex CLI, and zmx setup steps.
  5. Done routes an empty first-run user directly into Add Machine.
  6. Canceling Add Machine before saving keeps the first-run tutorial active on
     the next launch.
  7. Saving a disposable SSH machine completes onboarding and survives app
     restart as the automatic reconnect target.
  8. Reconnect recovery exposes the phone setup repair panel from the drawer.
  9. The saved-machine settings card and machine action sheet are reachable
     from the drawer, including Check connection, password, details, copy,
     host-key, delete, and Tailscale recovery actions.

Warning:
  This clears app data for Ghostex Android on the selected device. Set
  GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA=1 only on a disposable emulator/device or
  after confirming there is no real Ghostex Android data to preserve.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

adb_cmd() {
  if [[ -n "${GHOSTEX_ANDROID_DEVICE:-}" ]]; then
    "$ADB_BIN" -s "$GHOSTEX_ANDROID_DEVICE" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

fail() {
  echo "Ghostex Android UI smoke failed: $*" >&2
  exit 1
}

dump_ui() {
  adb_cmd shell uiautomator dump /sdcard/ghostex-window.xml >/dev/null
  adb_cmd pull /sdcard/ghostex-window.xml "$ui_dump" >/dev/null
}

assert_text() {
  local expected="$1"
  dump_ui
  if ! grep -F "$expected" "$ui_dump" >/dev/null; then
    echo "Current UI dump:" >&2
    sed -n '1,80p' "$ui_dump" >&2
    fail "missing visible text: $expected"
  fi
}

assert_any_text() {
  local expected
  dump_ui
  for expected in "$@"; do
    if grep -F "$expected" "$ui_dump" >/dev/null; then
      return 0
    fi
  done
  echo "Current UI dump:" >&2
  sed -n '1,80p' "$ui_dump" >&2
  fail "missing any visible text: $*"
}

tap_text() {
  local label node bounds x1 y1 x2 y2
  dump_ui
  for label in "$@"; do
    node="$(grep -o "<node[^>]*text=\"$label\"[^>]*>" "$ui_dump" | head -n 1 || true)"
    if [[ -n "$node" ]]; then
      bounds="$(printf '%s' "$node" | sed -n 's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')"
      if [[ -n "$bounds" ]]; then
        read -r x1 y1 x2 y2 <<<"$bounds"
        adb_cmd shell input tap "$(((x1 + x2) / 2))" "$(((y1 + y2) / 2))"
        return 0
      fi
    fi
  done
  return 1
}

tap_edit_text() {
  local index="$1"
  local node bounds x1 y1 x2 y2
  dump_ui
  node="$(grep -o '<node[^>]*class="android.widget.EditText"[^>]*>' "$ui_dump" | sed -n "${index}p" || true)"
  if [[ -z "$node" ]]; then
    echo "Current UI dump:" >&2
    sed -n '1,120p' "$ui_dump" >&2
    fail "could not find EditText number $index"
  fi
  bounds="$(printf '%s' "$node" | sed -n 's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')"
  if [[ -z "$bounds" ]]; then
    fail "could not read bounds for EditText number $index"
  fi
  read -r x1 y1 x2 y2 <<<"$bounds"
  adb_cmd shell input tap "$(((x1 + x2) / 2))" "$(((y1 + y2) / 2))"
}

fill_edit_text() {
  local index="$1"
  local value="$2"
  tap_edit_text "$index"
  sleep 0.3
  adb_cmd shell input text "$value"
}

screenshot() {
  local name="$1"
  adb_cmd exec-out screencap -p > "$output_dir/$name.png"
}

select_apk() {
  local mode="${GHOSTEX_ANDROID_UI_SMOKE_BUILD:-auto}"
  local explicit_apk="${GHOSTEX_ANDROID_UI_SMOKE_APK:-}"
  local release_apk debug_apk

  if [[ -n "$explicit_apk" ]]; then
    [[ -f "$explicit_apk" ]] || fail "GHOSTEX_ANDROID_UI_SMOKE_APK does not exist: $explicit_apk"
    printf '%s\n' "$explicit_apk"
    return 0
  fi

  case "$mode" in
    auto|release|debug) ;;
    *) fail "GHOSTEX_ANDROID_UI_SMOKE_BUILD must be auto, release, or debug." ;;
  esac

  if [[ "$mode" == "release" || "$mode" == "auto" ]]; then
    if [[ "$mode" == "release" ]]; then
      ./gradlew :app:assembleRelease >&2
    fi
    release_apk="$(find app/build/outputs/apk/release -name 'ghostex-android_*_universal.apk' -print 2>/dev/null | sort | tail -n 1)"
    if [[ -n "$release_apk" ]]; then
      printf '%s\n' "$release_apk"
      return 0
    fi
    [[ "$mode" == "auto" ]] || fail "could not find a Ghostex Android release universal APK. Build/sign release first or set GHOSTEX_ANDROID_UI_SMOKE_APK."
  fi

  ./gradlew :app:assembleDebug >&2
  debug_apk="$(find app/build/outputs/apk/debug -name 'ghostex-android_*_universal.apk' -print | sort | tail -n 1)"
  if [[ -z "$debug_apk" ]]; then
    fail "could not find a Ghostex Android debug universal APK."
  fi
  printf '%s\n' "$debug_apk"
}

if ! ADB_BIN="$(resolve_ghostex_android_adb)"; then
  explain_ghostex_android_adb_missing
  fail "adb is required for UI smoke verification."
fi

project_dir="$(cd "$script_dir/.." && pwd)"
cd "$project_dir"
runtime_package="$(ghostex_android_package_name)"
test_package="$(ghostex_android_test_package_name)"

output_dir="${GHOSTEX_ANDROID_UI_SMOKE_DIR:-/tmp/ghostex-android-ui-smoke}"
mkdir -p "$output_dir"
ui_dump="$output_dir/window.xml"

require_ghostex_android_device "$ADB_BIN" "${GHOSTEX_ANDROID_DEVICE:-}"
require_ghostex_android_data_clear_confirmation "GHOSTEX_ANDROID_CONFIRM_CLEAR_DATA"
apk_path="$(select_apk)"

adb_cmd uninstall "$runtime_package" >/dev/null 2>&1 || true
adb_cmd uninstall "$test_package" >/dev/null 2>&1 || true
if ! adb_cmd install -r "$apk_path" >/dev/null; then
  fail "could not install APK: $apk_path. If this is an unsigned release APK, set GHOSTEX_ANDROID_UI_SMOKE_APK to a signed release candidate or use GHOSTEX_ANDROID_UI_SMOKE_BUILD=debug for development-only smoke."
fi
adb_cmd shell am force-stop "$runtime_package" >/dev/null || true
adb_cmd shell pm clear "$runtime_package" >/dev/null || true
adb_cmd shell am start -n "$runtime_package/com.termux.app.TermuxActivity" >/dev/null

sleep 3
assert_text "Set up Ghostex Android"
assert_text "Connect to your running Ghostex sessions"
assert_text "On the Mac, install and sign in to Tailscale."
screenshot "01-first-run-tutorial-top"

adb_cmd shell input swipe 500 1700 500 520 450 >/dev/null
sleep 1
assert_text "command -v ghostex"
assert_text "Session persistence to zmx"
screenshot "02-first-run-tutorial-middle"

adb_cmd shell input swipe 500 1700 500 520 450 >/dev/null
sleep 1
assert_text "Tap Retry or reopen the app."
screenshot "03-first-run-tutorial-bottom"

tap_text "Done" "DONE" || fail "could not tap the Done tutorial action."
sleep 1
assert_text "Add a Mac or workstation"
assert_text "Ghostex Android connects over Tailscale SSH"
screenshot "04-add-machine-handoff"

tap_text "Cancel" "CANCEL" || fail "could not cancel the Add Machine editor."
sleep 1
adb_cmd shell am force-stop "$runtime_package" >/dev/null || true
adb_cmd shell am start -n "$runtime_package/com.termux.app.TermuxActivity" >/dev/null
sleep 3
assert_text "Set up Ghostex Android"
assert_text "Connect to your running Ghostex sessions"
screenshot "05-cancel-keeps-first-run-tutorial"

tap_text "Add machine" "ADD MACHINE" || fail "could not reopen Add Machine from the tutorial."
sleep 1
assert_text "Add a Mac or workstation"
fill_edit_text 1 "Smoke_Mac"
fill_edit_text 2 "127.0.0.1"
fill_edit_text 3 "ghostex"
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
sleep 0.5
screenshot "06-add-machine-filled"
tap_text "Save" "SAVE" || fail "could not save the disposable SSH machine."
sleep 5
assert_text "Smoke_Mac"
screenshot "07-saved-machine-drawer"

adb_cmd shell am force-stop "$runtime_package" >/dev/null || true
adb_cmd shell am start -n "$runtime_package/com.termux.app.TermuxActivity" >/dev/null
sleep 5
assert_text "Smoke_Mac"
assert_any_text "Phone setup required" "Connection needs attention" "No ZMX sessions yet"
screenshot "08-restart-reconnects-saved-machine"

tap_text "Setup" "SETUP" || fail "could not open phone setup from the reconnect drawer."
sleep 1
assert_text "Prepare this phone"
assert_text "SSH tools"
assert_text "Install SSH"
assert_text "Tailscale"
screenshot "09-phone-setup-panel"
tap_text "Cancel" "CANCEL" || fail "could not close phone setup panel."
sleep 1

tap_text "Machines" "MACHINES" || fail "could not open saved-machine settings."
sleep 1
assert_text "Saved machines"
assert_text "Smoke_Mac"
assert_text "ghostex@127.0.0.1:22"
assert_text "Uses SSH keys, Tailscale SSH, or session-only password"
screenshot "10-saved-machine-settings"

tap_text "More" "MORE" || fail "could not open saved-machine actions."
sleep 1
assert_text "Check connection"
assert_text "Enter password"
assert_text "Details"
screenshot "11-saved-machine-action-sheet"
adb_cmd shell input swipe 500 1700 500 520 450 >/dev/null
sleep 1
assert_text "Copy SSH target"
assert_text "Reset SSH host key"
assert_text "Delete"
assert_text "Open Tailscale"
screenshot "12-saved-machine-action-sheet-bottom"

echo "Ghostex Android UI smoke passed. Screenshots: $output_dir"
