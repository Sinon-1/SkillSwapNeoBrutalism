@echo off
rem Downloads ffmpeg (a free video converter) into lib\ffmpeg so SkillSwap can convert uploaded videos
rem to MP4 (H.264 + AAC) automatically. Run this once, then restart SkillSwap.
cd /d "%~dp0\.."
if exist lib\ffmpeg\ffmpeg.exe (
  echo ffmpeg is already installed in lib\ffmpeg.
  pause
  exit /b 0
)
if not exist lib\ffmpeg mkdir lib\ffmpeg
echo Downloading ffmpeg - about 30 MB, please wait...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' -OutFile 'lib\ffmpeg\ffmpeg.zip'; Expand-Archive -Path 'lib\ffmpeg\ffmpeg.zip' -DestinationPath 'lib\ffmpeg\tmp' -Force; $e = Get-ChildItem 'lib\ffmpeg\tmp' -Recurse -Filter ffmpeg.exe | Select-Object -First 1; Copy-Item $e.FullName 'lib\ffmpeg\ffmpeg.exe' -Force; Remove-Item 'lib\ffmpeg\tmp','lib\ffmpeg\ffmpeg.zip' -Recurse -Force"
echo.
if exist lib\ffmpeg\ffmpeg.exe (
  echo Done. Restart SkillSwap and uploaded videos will be converted automatically.
) else (
  echo Download failed. Alternative: open Command Prompt and run   winget install Gyan.FFmpeg
  echo Then close and reopen SkillSwap.
)
pause
