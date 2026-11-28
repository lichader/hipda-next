#!/usr/bin/env bash
# Record the forum responses that the debug app receives, to turn real pages into test
# fixtures. Works on a connected device or emulator running a debug build.
# See docs/capturing-forum-pages.md.
#
#   scripts/forum-captures.sh start        turn recording on
#   scripts/forum-captures.sh pull <dir>   copy the recorded files to <dir>
#   scripts/forum-captures.sh stop         turn recording off and delete the files on the device
set -euo pipefail

PKG=com.lichader.hipdanext
DIR=files/forum-captures
ADB=${ADB:-adb}

case "${1:-}" in
  start)
    "$ADB" shell run-as "$PKG" mkdir -p "$DIR"
    echo "Recording on. Use the app, then: $0 pull <dir>"
    ;;
  pull)
    dest=${2:?usage: $0 pull <dir>}
    mkdir -p "$dest"
    files=$("$ADB" shell run-as "$PKG" ls "$DIR" | tr -d '\r')
    count=0
    for f in $files; do
      "$ADB" exec-out run-as "$PKG" cat "$DIR/$f" > "$dest/$f"
      count=$((count + 1))
    done
    echo "Copied $count files to $dest. They contain personal data: scrub before committing anything."
    ;;
  stop)
    "$ADB" shell run-as "$PKG" rm -rf "$DIR"
    echo "Recording off; the recorded files on the device are deleted."
    ;;
  *)
    sed -n '2,10p' "$0"
    exit 1
    ;;
esac
