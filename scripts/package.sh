#!/usr/bin/env sh
# Build an installer/app-image with the JDK's jpackage (run on the OS you are targeting).
# Needs JDK 17+ and Maven. Output goes to dist/.
set -e
cd "$(dirname "$0")/.."
mvn -q -DskipTests package
# Draw the SkillSwap icon in the format this OS needs
java -cp target/classes campus.skillswap.ui.IconExport target/icon
rm -rf target/input dist && mkdir -p target/input dist
cp target/skillswap-campus.jar target/input/
case "$(uname -s)" in
  Darwin) TYPE=dmg
          iconutil -c icns target/icon/icon.iconset -o target/icon/icon.icns
          ICON=target/icon/icon.icns ;;
  *)      TYPE=deb       # use "rpm" on Fedora/RHEL, or "app-image" for a plain folder
          ICON=target/icon/icon.png ;;
esac
jpackage --type "$TYPE" --name "SkillSwap Campus" --app-version 1.0.1 \
  --icon "$ICON" \
  --input target/input --main-jar skillswap-campus.jar --main-class campus.skillswap.Main \
  --dest dist
echo "Done - see dist/"
