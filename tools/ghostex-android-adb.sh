#!/usr/bin/env bash

# CDXC:AndroidReleaseE2E 2026-05-17-17:35:
# Release verification should work on normal Android SDK installs, not only
# shells where platform-tools is already exported on PATH. Keep adb discovery
# shared by the live Tailscale E2E and first-run UI smoke harnesses so both
# scripts fail with the same actionable setup message.
#
# CDXC:AndroidSideBySideInstall 2026-05-17-23:39:
# Device verification must target Ghostex Android's side-by-side runtime
# package, not upstream Termux. Keep package helpers centralized so smoke and
# live E2E cannot accidentally uninstall or clear a user's existing Termux app.
#

ghostex_android_package_name() {
  # CDXC:AndroidBranding 2026-05-18-06:41: Device helpers default to `io.ghostex` so adb install/test flows target a correctly spelled Ghostex package id.
  printf '%s\n' "${GHOSTEX_ANDROID_PACKAGE_NAME:-io.ghostex}"
}

ghostex_android_test_package_name() {
  printf '%s\n' "${GHOSTEX_ANDROID_TEST_PACKAGE_NAME:-$(ghostex_android_package_name).test}"
}

ghostex_android_prefix() {
  printf '/data/data/%s/files/usr\n' "$(ghostex_android_package_name)"
}

ghostex_android_home() {
  printf '/data/data/%s/files/home\n' "$(ghostex_android_package_name)"
}

resolve_ghostex_android_adb() {
  if [[ -n "${GHOSTEX_ANDROID_ADB:-}" ]]; then
    if [[ -x "$GHOSTEX_ANDROID_ADB" ]]; then
      printf '%s\n' "$GHOSTEX_ANDROID_ADB"
      return 0
    fi
    return 1
  fi

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  local candidates=()
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    candidates+=(
      "$ANDROID_HOME/platform-tools/adb"
      "${ANDROID_HOME%/cmdline-tools/*}/platform-tools/adb"
      "${ANDROID_HOME%/cmdline-tools}/platform-tools/adb"
      "${ANDROID_HOME%/android-commandlinetools}/android-sdk/platform-tools/adb"
    )
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    candidates+=("$ANDROID_SDK_ROOT/platform-tools/adb")
  fi
  candidates+=(
    "$HOME/Library/Android/sdk/platform-tools/adb"
    "$HOME/Android/Sdk/platform-tools/adb"
    "/opt/android-sdk/platform-tools/adb"
    "/usr/local/share/android-sdk/platform-tools/adb"
    "/opt/homebrew/share/android-sdk/platform-tools/adb"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

explain_ghostex_android_adb_missing() {
  cat >&2 <<'EOF'
adb is required for Ghostex Android device verification.

Install Android platform-tools, add adb to PATH, or set:
  GHOSTEX_ANDROID_ADB=/absolute/path/to/platform-tools/adb
EOF
}

require_ghostex_android_device() {
  local adb_bin="$1"
  local serial="${2:-}"
  local output
  if [[ -n "$serial" ]]; then
    if ! output="$("$adb_bin" -s "$serial" get-state 2>&1)"; then
      printf 'No Android device/emulator is reachable for GHOSTEX_ANDROID_DEVICE=%s.\n' "$serial" >&2
      printf '%s\n\n' "$output" >&2
      "$adb_bin" devices -l >&2 || true
      return 1
    fi
    return 0
  fi

  if ! output="$("$adb_bin" get-state 2>&1)"; then
    printf 'No Android device/emulator is reachable over adb.\n' >&2
    printf 'Start an emulator or connect a phone with USB debugging enabled, then rerun the Ghostex Android release check.\n\n' >&2
    printf '%s\n\n' "$output" >&2
    "$adb_bin" devices -l >&2 || true
    return 1
  fi
}

require_ghostex_android_data_clear_confirmation() {
  local variable_name="$1"
  if [[ "${!variable_name:-}" == "1" ]]; then
    return 0
  fi

  cat >&2 <<EOF
Refusing to clear $(ghostex_android_package_name) app data without explicit confirmation.

Ghostex Android now uses a side-by-side runtime package. A fresh-install smoke
run can still delete existing Ghostex Android state on the selected device.

Rerun on a disposable emulator/device with:
  ${variable_name}=1
EOF
  return 1
}
