#!/usr/bin/env sh
# SkillSwap converts uploaded videos automatically when ffmpeg is installed.
if command -v ffmpeg >/dev/null 2>&1; then echo "ffmpeg is already installed."; exit 0; fi
echo "Install ffmpeg, then restart SkillSwap:"
echo "  macOS:          brew install ffmpeg"
echo "  Ubuntu/Debian:  sudo apt install ffmpeg"
