#!/usr/bin/env bash
set -euo pipefail

#
# CDXC:AndroidReleaseSurface 2026-05-17-21:14:
# Publishable Ghostex Android artifacts need an explicit post-build signature
# inspection step in both strict local release proof and signed CI candidates.
# Keep APK/AAB lookup and apksigner/jarsigner discovery in one Android-side tool
# so CI and the root runner cannot drift into different signature evidence.
#
# CDXC:AndroidReleaseSurface 2026-05-17-21:25:
# The strict runner is usually launched from macOS while CI runs Linux. Avoid
# GNU-only version sorting and Bash 4-only array loading; compare dotted
# build-tool versions and collect artifacts with Bash 3-compatible loops so
# signature proof is portable.
#
# CDXC:AndroidReleaseSurface 2026-05-17-21:31:
# `jarsigner -verify` can exit successfully for an unsigned AAB while printing
# `jar is unsigned.` Treat the textual verification result as part of the
# release proof so strict mode cannot accept an unsigned publish bundle.
#
# CDXC:AndroidReleaseSurface 2026-05-17-20:47:
# Signature verification must use SHA256SUMS as the artifact manifest so strict
# local proof and signed CI inspect the same APK/AAB files that release QA
# checksums and uploads.
#

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  [GHOSTEX_ANDROID_APKSIGNER=/absolute/path/to/apksigner] \
  [ANDROID_HOME=/path/to/android-sdk] \
  [ANDROID_SDK_ROOT=/path/to/android-sdk] \
  tools/ghostex-android-verify-release-signatures.sh

Verifies Ghostex release APK entries listed in
app/build/outputs/apk/release/SHA256SUMS with Android build-tools apksigner
and the single Ghostex release AAB entry listed in
app/build/outputs/bundle/release/SHA256SUMS with JDK jarsigner. Manifested
artifact names must match ghostex-android_*.apk or ghostex-android_*.aab.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

find_ghostex_android_apksigner() {
  if [[ -n "${GHOSTEX_ANDROID_APKSIGNER:-}" ]]; then
    if [[ -x "$GHOSTEX_ANDROID_APKSIGNER" ]]; then
      printf '%s\n' "$GHOSTEX_ANDROID_APKSIGNER"
      return 0
    fi
    return 1
  fi

  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi

  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
    local candidate
    candidate="$(find_latest_ghostex_android_apksigner "$sdk_root/build-tools")"
    if [[ -n "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  return 1
}

find_latest_ghostex_android_apksigner() {
  local build_tools_dir="$1"
  local candidate=""
  local tool
  while IFS= read -r tool; do
    if [[ -z "$candidate" || "$(ghostex_android_version_greater "$(basename "$(dirname "$tool")")" "$(basename "$(dirname "$candidate")")")" == "1" ]]; then
      candidate="$tool"
    fi
  done < <(find "$build_tools_dir" -maxdepth 2 -type f -name apksigner -perm -111 | sort)
  printf '%s\n' "$candidate"
}

ghostex_android_version_greater() {
  local left="$1"
  local right="$2"
  local left_parts right_parts left_value right_value index max_parts
  IFS=. read -r -a left_parts <<<"$left"
  IFS=. read -r -a right_parts <<<"$right"
  max_parts="${#left_parts[@]}"
  if [[ "${#right_parts[@]}" -gt "$max_parts" ]]; then
    max_parts="${#right_parts[@]}"
  fi
  for ((index = 0; index < max_parts; index++)); do
    left_value="${left_parts[$index]:-0}"
    right_value="${right_parts[$index]:-0}"
    if [[ ! "$left_value" =~ ^[0-9]+$ || ! "$right_value" =~ ^[0-9]+$ ]]; then
      if [[ "$left" > "$right" ]]; then
        printf '1\n'
      else
        printf '0\n'
      fi
      return
    fi
    if ((10#$left_value > 10#$right_value)); then
      printf '1\n'
      return
    fi
    if ((10#$left_value < 10#$right_value)); then
      printf '0\n'
      return
    fi
  done
  printf '0\n'
}

run() {
  printf '\n+'
  printf ' %q' "$@"
  printf '\n'
  "$@"
}

run_jarsigner_verify_aab() {
  local bundle="$1"
  local output
  if ! output="$(jarsigner -verify "$bundle" 2>&1)"; then
    printf '%s\n' "$output" >&2
    return 1
  fi
  printf '%s\n' "$output"
  if printf '%s\n' "$output" | grep -F "jar is unsigned." >/dev/null; then
    echo "Ghostex Android release AAB is unsigned: $bundle" >&2
    return 1
  fi
  if ! printf '%s\n' "$output" | grep -F "jar verified." >/dev/null; then
    echo "Ghostex Android release AAB did not report a verified signature: $bundle" >&2
    return 1
  fi
}

collect_ghostex_signed_artifacts_from_sums() {
  local directory="$1"
  local suffix="$2"
  local label="$3"
  local sums_file="$directory/SHA256SUMS"
  local line checksum name artifact
  if [[ ! -f "$sums_file" ]]; then
    echo "Missing $label SHA256SUMS: $sums_file" >&2
    return 1
  fi

  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    checksum="${line%%  *}"
    name="${line#*  }"
    if [[ "$checksum" == "$line" || -z "$checksum" || -z "$name" || "$name" == */* || "$name" == *\\* ]]; then
      echo "Malformed $label SHA256SUMS line: $line" >&2
      return 1
    fi
    if [[ ! "$checksum" =~ ^[0-9a-fA-F]{64}$ ]]; then
      echo "Malformed $label SHA256SUMS checksum: $line" >&2
      return 1
    fi
    if [[ ! "$name" == ghostex-android_* || ! "$name" == *"$suffix" ]]; then
      echo "Unexpected $label artifact in SHA256SUMS: $name" >&2
      return 1
    fi
    artifact="$directory/$name"
    if [[ ! -f "$artifact" ]]; then
      echo "Missing $label artifact listed in SHA256SUMS: $artifact" >&2
      return 1
    fi
    printf '%s\n' "$artifact"
  done < "$sums_file"
}

apk_dir="$project_dir/app/build/outputs/apk/release"
bundle_dir="$project_dir/app/build/outputs/bundle/release"
apks=()
bundles=()
apk_manifest=""
bundle_manifest=""

if ! apk_manifest="$(collect_ghostex_signed_artifacts_from_sums "$apk_dir" ".apk" "release APK")"; then
  exit 1
fi
if ! bundle_manifest="$(collect_ghostex_signed_artifacts_from_sums "$bundle_dir" ".aab" "release AAB")"; then
  exit 1
fi

while IFS= read -r artifact; do
  [[ -n "$artifact" ]] || continue
  apks+=("$artifact")
done <<<"$apk_manifest"
while IFS= read -r artifact; do
  [[ -n "$artifact" ]] || continue
  bundles+=("$artifact")
done <<<"$bundle_manifest"

if [[ "${#apks[@]}" -eq 0 ]]; then
  echo "No Ghostex Android release APKs found for signature verification in $apk_dir." >&2
  exit 1
fi
if [[ "${#bundles[@]}" -ne 1 ]]; then
  echo "Expected exactly one Ghostex Android release AAB for signature verification in $bundle_dir, found ${#bundles[@]}." >&2
  exit 1
fi
if ! apksigner_bin="$(find_ghostex_android_apksigner)"; then
  echo "Could not find apksigner. Install Android build-tools, set GHOSTEX_ANDROID_APKSIGNER, or set ANDROID_HOME/ANDROID_SDK_ROOT before strict release verification." >&2
  exit 1
fi
if ! command -v jarsigner >/dev/null 2>&1; then
  echo "Could not find jarsigner. Install a full JDK before strict release verification." >&2
  exit 1
fi

for artifact in "${apks[@]}"; do
  run "$apksigner_bin" verify --print-certs "$artifact"
done
run_jarsigner_verify_aab "${bundles[0]}"
