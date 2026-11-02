$ErrorActionPreference = 'Stop'

$JUNIT_VERSION = "1.10.1"
$JUNIT_URL = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"
$JUNIT_JAR = "lib\junit-platform-console-standalone-$JUNIT_VERSION.jar"

if (-Not (Test-Path $JUNIT_JAR)) {
    Write-Host "Descargando JUnit 5 Standalone ($JUNIT_VERSION)..."
    Invoke-WebRequest -Uri $JUNIT_URL -OutFile $JUNIT_JAR
    Write-Host "Descarga completada.`n"
}

Write-Host "Compilando proyecto para pruebas..."
if (-Not (Test-Path "out")) {
    New-Item -ItemType Directory -Force -Path "out" | Out-Null
}

$javaFiles = Get-ChildItem -Recurse -Filter *.java src
$testFiles = Get-ChildItem -Recurse -Filter *.java tests

$allFiles = @($javaFiles.FullName) + @($testFiles.FullName)

javac -encoding UTF-8 -cp "lib/*" -d out $allFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error en compilacion." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Ejecutando Pruebas Unitarias...`n" -ForegroundColor Cyan
$libJars = (Get-ChildItem -Filter *.jar lib).FullName -join ';'
$testCp = "out;$libJars"
java -jar $JUNIT_JAR execute -cp $testCp --scan-class-path

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nTodas las pruebas pasaron exitosamente." -ForegroundColor Green
} else {
    Write-Host "`nAlgunas pruebas fallaron." -ForegroundColor Red
}
