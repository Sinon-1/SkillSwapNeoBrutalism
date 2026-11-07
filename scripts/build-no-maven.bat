@echo off
rem Build + run WITHOUT Maven (uses lib\postgresql-42.7.4.jar). Skips the optional JavaFX video player.
cd /d "%~dp0\.."
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java | findstr /v "VideoPlayerFX.java" > out\sources.txt
javac -d out @out\sources.txt || exit /b 1
java -cp "out;lib\postgresql-42.7.4.jar" campus.skillswap.Main
