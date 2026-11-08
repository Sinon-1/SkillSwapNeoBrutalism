@echo off
rem Build + run WITHOUT Maven (uses lib\postgresql-42.7.4.jar).
rem Optional: unzip a JavaFX SDK into lib\javafx-sdk (so lib\javafx-sdk\lib exists) to get in-app video playback and video previews.
rem Without it, reels open in your default video player and previews need ffmpeg on your PATH.
cd /d "%~dp0\.."
if exist out rmdir /s /q out
mkdir out
if exist lib\javafx-sdk\lib (
  dir /s /b src\*.java > out\sources.txt
  javac --module-path lib\javafx-sdk\lib --add-modules javafx.media,javafx.swing -d out @out\sources.txt || exit /b 1
  java --module-path lib\javafx-sdk\lib --add-modules javafx.media,javafx.swing -cp "out;lib\postgresql-42.7.4.jar" campus.skillswap.Main
  exit /b
)
dir /s /b src\*.java | findstr /v "VideoPlayerFX.java" > out\sources.txt
javac -d out @out\sources.txt || exit /b 1
java -cp "out;lib\postgresql-42.7.4.jar" campus.skillswap.Main
