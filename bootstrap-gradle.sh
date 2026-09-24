#!/usr/bin/env sh
set -eu

VERSION="${1:-9.6.0}"
EXPECTED="bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
EXPECTED_WRAPPER="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TMP="$ROOT/.gradle-bootstrap"
ZIP="$TMP/gradle-$VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"

command -v java >/dev/null 2>&1 || { echo "Java 17+ is required." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is required." >&2; exit 1; }

mkdir -p "$TMP"
echo "Downloading official Gradle $VERSION..."
curl -fL "$URL" -o "$ZIP"

if [ "$VERSION" = "9.6.0" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$ZIP" | awk '{print $1}')
  else
    ACTUAL=$(shasum -a 256 "$ZIP" | awk '{print $1}')
  fi
  [ "$ACTUAL" = "$EXPECTED" ] || { echo "Checksum mismatch." >&2; exit 1; }
fi

unzip -q -o "$ZIP" -d "$TMP"
cd "$ROOT"
# --no-daemon: avoid leaving a process locking files under $TMP.
"$TMP/gradle-$VERSION/bin/gradle" wrapper --gradle-version "$VERSION" --distribution-type bin --no-daemon

if [ "$VERSION" = "9.6.0" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    WRAPPER_ACTUAL=$(sha256sum "$ROOT/gradle/wrapper/gradle-wrapper.jar" | awk '{print $1}')
  else
    WRAPPER_ACTUAL=$(shasum -a 256 "$ROOT/gradle/wrapper/gradle-wrapper.jar" | awk '{print $1}')
  fi
  [ "$WRAPPER_ACTUAL" = "$EXPECTED_WRAPPER" ] || { echo "Wrapper JAR checksum mismatch." >&2; exit 1; }
  grep -v '^distributionSha256Sum=' "$ROOT/gradle/wrapper/gradle-wrapper.properties" > "$ROOT/gradle/wrapper/gradle-wrapper.properties.tmp"
  printf '%s\n' "distributionSha256Sum=$EXPECTED" >> "$ROOT/gradle/wrapper/gradle-wrapper.properties.tmp"
  mv "$ROOT/gradle/wrapper/gradle-wrapper.properties.tmp" "$ROOT/gradle/wrapper/gradle-wrapper.properties"
fi

rm -rf "$TMP"
echo "Gradle wrapper created. Next: ./gradlew --version"
