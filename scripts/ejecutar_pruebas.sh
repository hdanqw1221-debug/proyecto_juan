#!/usr/bin/env bash
# Compila con --release 21 y ejecuta JUnit y pytest.
# Uso: scripts/ejecutar_pruebas.sh <carpeta_evidencia> [etiqueta]
# Las pruebas con base de datos usan gestion_pruebas (ver tests/app/testutil/TestDatabase.java).
set -u
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
DEST="${1:-evidencias/$(date +%F)}"; ETQ="${2:-pruebas}"
mkdir -p "$DEST"
rm -rf build/classes && mkdir -p build/classes
javac --release 21 -encoding UTF-8 -cp "lib/*" -d build/classes $(find src tests -name '*.java') > "$DEST/${ETQ}_javac.txt" 2>&1
JC=$?; echo "javac exit=$JC"
[ $JC -ne 0 ] && { tail -30 "$DEST/${ETQ}_javac.txt"; exit 1; }
CP="build/classes"
for j in lib/*.jar; do case "$j" in *junit*) ;; *) CP="$CP;$j" ;; esac; done
java -jar lib/junit-platform-console-standalone-1.10.1.jar execute -cp "$CP" \
  --scan-class-path --disable-banner --disable-ansi-colors --details=tree > "$DEST/${ETQ}_junit.txt" 2>&1
JU=$?; echo "junit exit=$JU"; grep -E "tests (found|successful|failed|skipped|aborted)" "$DEST/${ETQ}_junit.txt"
grep -nE "✘|\[X\]" "$DEST/${ETQ}_junit.txt" | head -20
cd aplicacion_tres_d/aplicacion_tres_d
.venv/Scripts/python.exe -m pytest cavidades/tests -v -p no:cacheprovider > "$RAIZ/$DEST/${ETQ}_pytest.txt" 2>&1
PY=$?; echo "pytest exit=$PY"; tail -1 "$RAIZ/$DEST/${ETQ}_pytest.txt"
grep -E "FAILED|ERROR" "$RAIZ/$DEST/${ETQ}_pytest.txt" | head -20
[ $JU -eq 0 ] && [ $PY -eq 0 ]
