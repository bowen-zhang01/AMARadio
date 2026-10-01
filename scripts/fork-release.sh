#!/usr/bin/env bash
# Build (and optionally publish) a signed release APK of the bowen-zhang01/AMARadio fork.
#
# Usage: scripts/fork-release.sh [--publish]
#
# The signing key lives outside the repository:
#   keystore: ~/.android/amaradio-fork-release.jks   (override with AMARADIO_FORK_KEYSTORE)
#   password: macOS keychain item "amaradio-fork-release" (account: $USER)
# --publish creates a GitHub release named after the version and attaches the APK.
set -euo pipefail

cd "$(dirname "$0")/.."

publish=false
[[ "${1:-}" == "--publish" ]] && publish=true

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

db=app/src/main/assets/databases/radio_browser_database.db
if ! head -c 15 "$db" | grep -q "SQLite format"; then
  echo "Station database is a Git LFS pointer; fetching the real file..." >&2
  git lfs fetch upstream master || git lfs fetch origin
  git lfs checkout "$db"
  head -c 15 "$db" | grep -q "SQLite format" || { echo "Could not fetch $db" >&2; exit 1; }
fi

export AMARADIO_FORK_KEYSTORE="${AMARADIO_FORK_KEYSTORE:-$HOME/.android/amaradio-fork-release.jks}"
export AMARADIO_FORK_KEY_ALIAS="${AMARADIO_FORK_KEY_ALIAS:-amaradio-fork}"
if [[ -z "${AMARADIO_FORK_KEYSTORE_PASSWORD:-}" ]]; then
  AMARADIO_FORK_KEYSTORE_PASSWORD="$(security find-generic-password -a "$USER" -s amaradio-fork-release -w)"
  export AMARADIO_FORK_KEYSTORE_PASSWORD
fi

./gradlew --console=plain clean :app:testFossDebugUnitTest :app:assembleFossRelease

apk=$(ls app/build/outputs/apk/foss/release/*.apk | head -n 1)
version=$(sed -n 's/.*"versionName" *: *"\([^"]*\)".*/\1/p' app/build/outputs/apk/foss/release/output-metadata.json | head -n 1)
out="build-release/AMARadio-${version}.apk"
mkdir -p build-release
cp "$apk" "$out"

apksigner=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -n 1)apksigner
"$apksigner" verify --print-certs "$out" | grep -E "certificate (DN|SHA-256 digest)"
echo "Built $out"

if $publish; then
  tag="v${version}"
  gh release create "$tag" "$out" \
    --repo bowen-zhang01/AMARadio \
    --target "$(git rev-parse HEAD)" \
    --title "AMARadio ${version}" \
    --notes-file <(awk -v v="### ${version}" '$0 == v {f=1; next} /^#{2,3} / {f=0} f' FORK.md)
fi
