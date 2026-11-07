@echo off
rem Build a Windows installer with jpackage. Needs JDK 17+, Maven, and WiX Toolset 3.x (for --type exe/msi).
rem If you don't have WiX, change "--type exe" to "--type app-image" for a plain runnable folder.
cd /d "%~dp0\.."
call mvn -q -DskipTests package || exit /b 1
if exist target\input rmdir /s /q target\input
if exist dist rmdir /s /q dist
mkdir target\input & mkdir dist
copy target\skillswap-campus.jar target\input\ >nul
jpackage --type exe --name "SkillSwap Campus" --app-version 1.0.0 --win-shortcut --win-menu ^
  --input target\input --main-jar skillswap-campus.jar --main-class campus.skillswap.Main --dest dist
echo Done - see dist\
