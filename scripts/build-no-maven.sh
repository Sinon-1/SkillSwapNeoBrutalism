#!/usr/bin/env sh
# Build + run WITHOUT Maven (uses lib/postgresql-42.7.4.jar).
# Optional: unzip a JavaFX SDK into lib/javafx-sdk (so lib/javafx-sdk/lib exists) for in-app video playback and previews.
# Without it, reels open in the default video player and previews need ffmpeg on the PATH.
set -e
cd "$(dirname "$0")/.."
rm -rf out && mkdir out
if [ -d lib/javafx-sdk/lib ]; then
  FX="--module-path lib/javafx-sdk/lib --add-modules javafx.media,javafx.swing"
  find src -name "*.java" > out/sources.txt
  javac $FX -d out @out/sources.txt
  exec java $FX -cp "out:lib/postgresql-42.7.4.jar" campus.skillswap.Main
fi
find src -name "*.java" ! -name VideoPlayerFX.java > out/sources.txt
javac -d out @out/sources.txt
java -cp "out:lib/postgresql-42.7.4.jar" campus.skillswap.Main
