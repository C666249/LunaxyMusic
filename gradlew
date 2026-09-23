#!/bin/sh
set -e
VERSION=9.5.0
DIST="$HOME/.gradle/wrapper/dists/xingyu-gradle-$VERSION"
HOMEG="$DIST/gradle-$VERSION"
if [ ! -x "$HOMEG/bin/gradle" ]; then
  mkdir -p "$DIST"
  echo "[SunflowerMusic] First build: downloading Gradle $VERSION ..."
  i=1
  while [ $i -le 3 ]; do
    if command -v curl >/dev/null 2>&1; then curl --retry 3 --connect-timeout 30 -L "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$DIST/gradle.zip" && break; else wget -t 3 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -O "$DIST/gradle.zip" && break; fi
    i=$((i+1)); sleep 2
  done
  unzip -q -o "$DIST/gradle.zip" -d "$DIST"
  rm -f "$DIST/gradle.zip"
fi
exec "$HOMEG/bin/gradle" "$@"
