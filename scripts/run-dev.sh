#!/usr/bin/env sh
# Run the app straight from source (needs JDK 17+ and Maven).
cd "$(dirname "$0")/.." && mvn -q compile exec:java
