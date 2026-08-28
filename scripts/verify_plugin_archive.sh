#!/usr/bin/env bash
# Find exactly one zip and one plugin.sha256 under a directory, then require
# the recorded digest to match the zip. An optional second argument must also
# match. Prints zip, sha256, and file assignments for `eval`.
set -euo pipefail

archive_dir="${1:-}"
expected_sha="${2:-}"

if [ -z "$archive_dir" ] || [ ! -d "$archive_dir" ]; then
  echo "::error::Archive directory missing: ${archive_dir:-<empty>}" >&2
  exit 1
fi

mapfile -t zips < <(find "$archive_dir" -name '*.zip' -type f)
mapfile -t sums < <(find "$archive_dir" -name 'plugin.sha256' -type f)
if [ "${#zips[@]}" -ne 1 ] || [ "${#sums[@]}" -ne 1 ]; then
  echo "::error::Expected one zip and one plugin.sha256 in $archive_dir" >&2
  find "$archive_dir" -type f -print >&2 || true
  exit 1
fi

zip="${zips[0]}"
recorded="$(awk '{print $1}' "${sums[0]}")"
actual="$(sha256sum "$zip" | awk '{print $1}')"
file="$(basename "$zip")"

if [ "$recorded" != "$actual" ]; then
  echo "::error::SHA-256 mismatch for $file: recorded $recorded, actual $actual" >&2
  exit 1
fi
if [ -n "$expected_sha" ] && [ "$actual" != "$expected_sha" ]; then
  echo "::error::SHA-256 mismatch for $file: actual $actual, expected $expected_sha" >&2
  exit 1
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "zip=$zip"
    echo "sha256=$actual"
    echo "file=$file"
    echo "zip_name=$file"
  } >> "$GITHUB_OUTPUT"
fi

printf 'zip=%s\n' "$zip"
printf 'sha256=%s\n' "$actual"
printf 'file=%s\n' "$file"
