#!/usr/bin/env bash
# Solo Java: compila con --release 21 y ejecuta JUnit. Uso: scripts/junit.sh <archivo_salida>
cd "$(dirname "$0")/.."
rm -rf build/classes && mkdir -p build/classes
javac --release 21 -encoding UTF-8 -cp "lib/*" -d build/classes $(find src tests -name '*.java') || exit 1
CP="build/classes"; for j in lib/*.jar; do case "$j" in *junit*) ;; *) CP="$CP;$j" ;; esac; done
OUT="${1:-build/junit.txt}"; mkdir -p "$(dirname "$OUT")"
java -jar lib/junit-platform-console-standalone-1.10.1.jar execute -cp "$CP" --scan-class-path \
  --disable-banner --disable-ansi-colors --details=tree > "$OUT" 2>&1
RC=$?; grep -E "tests (found|successful|failed|skipped|aborted)" "$OUT"; grep -B2 -A12 "✘" "$OUT" | head -60; exit $RC
