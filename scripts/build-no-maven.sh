#!/usr/bin/env sh
# Build + run WITHOUT Maven (uses lib/postgresql-42.7.4.jar). Skips the optional JavaFX video player,
# so reels open in the computer's default video player.
set -e
cd "$(dirname "$0")/.."
rm -rf out && mkdir out
find src -name "*.java" ! -name VideoPlayerFX.java > out/sources.txt
javac -d out @out/sources.txt
java -cp "out:lib/postgresql-42.7.4.jar" campus.skillswap.Main
