param(
    [ValidateSet("run", "package")]
    [string]$Mode = "run",
    [string]$Version = "1.0.3",
    [string]$BundledPythonSource = ""
)

$ErrorActionPreference = "Stop"

$JDK_BIN = Join-Path $PSScriptRoot "oracleJdk-26\bin"
$JAVAC = Join-Path $JDK_BIN "javac.exe"
$JAVA = Join-Path $JDK_BIN "java.exe"
$JAR = Join-Path $JDK_BIN "jar.exe"
$JPACKAGE = Join-Path $JDK_BIN "jpackage.exe"

if (!(Test-Path $JAVAC)) {
    $JAVAC = "javac"
    $JAVA = "java"
    $JAR = "jar"
    $JPACKAGE = "jpackage"
}

function Compile-App {
    if (!(Test-Path "out")) { New-Item -ItemType Directory "out" | Out-Null }
    Write-Host "Compilando proyecto..." -ForegroundColor Cyan
    $files = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
    & $JAVAC -encoding UTF-8 -cp "lib/*" -d out $files
    if ($LASTEXITCODE -ne 0) {
        throw "Error en la compilacion."
    }
}

function Add-DirectoryIfExists {
    param(
        [string]$Source,
        [string]$Target
    )
    if (Test-Path $Source) {
        Copy-Item $Source $Target -Recurse -Force
    } else {
        Write-Host "Aviso: no se encontro '$Source' para empaquetar." -ForegroundColor Yellow
    }
}

function Add-SimulationDirectory {
    param(
        [string]$Source,
        [string]$Target
    )
    if (!(Test-Path $Source)) {
        Write-Host "Aviso: no se encontro '$Source' para empaquetar." -ForegroundColor Yellow
        return
    }

    & robocopy $Source $Target /E /XD ".venv" /NFL /NDL /NJH /NJS /NC /NS | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "No se pudo copiar el modulo de simulaciones 3D."
    }
}

function Package-App {
    param([string]$AppVersion)

    $buildRoot = "build"
    $inputDir = Join-Path $buildRoot "package-input"
    $destDir = Join-Path $buildRoot "dist"
    $manifest = Join-Path $buildRoot "manifest.mf"

    if (Test-Path $inputDir) { Remove-Item $inputDir -Recurse -Force }
    if (Test-Path $destDir) { Remove-Item $destDir -Recurse -Force }
    if (!(Test-Path $buildRoot)) { New-Item -ItemType Directory $buildRoot | Out-Null }
    New-Item -ItemType Directory $inputDir | Out-Null
    New-Item -ItemType Directory $destDir | Out-Null

    @"
Main-Class: app.Main
Class-Path: lib/jbcrypt-0.4.jar lib/postgresql-42.7.10.jar

"@ | Set-Content -Encoding ASCII $manifest

    & $JAR --create --file (Join-Path $inputDir "app.jar") --manifest $manifest -C out .
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo crear app.jar."
    }

    New-Item -ItemType Directory (Join-Path $inputDir "lib") | Out-Null
    Get-ChildItem "lib\*.jar" -Exclude "*junit*" | Copy-Item -Destination (Join-Path $inputDir "lib") -Force

    Add-DirectoryIfExists -Source "config" -Target (Join-Path $inputDir "config")
    Add-DirectoryIfExists -Source "assets" -Target (Join-Path $inputDir "assets")
    Add-SimulationDirectory -Source "aplicacion_tres_d" -Target (Join-Path $inputDir "aplicacion_tres_d")

    if ([string]::IsNullOrWhiteSpace($BundledPythonSource)) {
        $defaultPythonRuntime = Join-Path $buildRoot "python-runtime"
        if (Test-Path (Join-Path $defaultPythonRuntime "python.exe")) {
            $BundledPythonSource = $defaultPythonRuntime
            Write-Host "Incluyendo runtime Python embebido desde '$BundledPythonSource'." -ForegroundColor Cyan
        }
    }

    $includeBundledPython = ![string]::IsNullOrWhiteSpace($BundledPythonSource)
    if ($includeBundledPython) {
        if (!(Test-Path $BundledPythonSource)) {
            throw "No se encontro el runtime Python indicado: '$BundledPythonSource'."
        }
        Add-DirectoryIfExists -Source $BundledPythonSource -Target (Join-Path $inputDir "python-runtime")
    }

    $bundledPythonExe = Join-Path (Join-Path $inputDir "python-runtime") "python.exe"
    if (Test-Path $bundledPythonExe) {
        Write-Host "Validando runtime Python embebido..." -ForegroundColor Cyan
        & $bundledPythonExe -c "import PyQt5, vtk; print('OK runtime Python embebido')"
        if ($LASTEXITCODE -ne 0) {
            throw "El runtime Python embebido no tiene dependencias completas (PyQt5/vtk)."
        }
    } elseif ($includeBundledPython) {
        throw "El runtime Python indicado no contiene python.exe en la raiz."
    } else {
        Write-Host "Aviso: no se incluyo runtime Python embebido. Los simuladores requeriran Python instalado en el PC destino." -ForegroundColor Yellow
    }

    $wixBin1 = "C:\Program Files (x86)\WiX Toolset v3.14\bin"
    $wixBin2 = "C:\Program Files\WiX Toolset v3.14\bin"
    if (Test-Path $wixBin1) { $env:PATH = "$wixBin1;$env:PATH" }
    if (Test-Path $wixBin2) { $env:PATH = "$wixBin2;$env:PATH" }

    Write-Host "Generando app-image..." -ForegroundColor Cyan
    & $JPACKAGE --type app-image `
        --name "GestionAcademica" `
        --input $inputDir `
        --dest $destDir `
        --main-jar "app.jar" `
        --main-class "app.Main" `
        --java-options "-Dfile.encoding=UTF-8"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo generar app-image."
    }

    Write-Host "Generando instalador EXE..." -ForegroundColor Cyan
    & $JPACKAGE --type exe `
        --name "GestionAcademica" `
        --input $inputDir `
        --dest $destDir `
        --main-jar "app.jar" `
        --main-class "app.Main" `
        --app-version $AppVersion `
        --vendor "Universidad de Pamplona" `
        --description "Sistema de Gestion Academica" `
        --win-dir-chooser `
        --win-shortcut `
        --win-menu `
        --java-options "-Dfile.encoding=UTF-8"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo generar el instalador EXE (verifica WiX)."
    }

    Write-Host "Empaquetado completado." -ForegroundColor Green
    Get-ChildItem $destDir | Select-Object Name, Length, LastWriteTime
}

Compile-App

if ($Mode -eq "run") {
    Write-Host "Compilacion exitosa. Iniciando aplicacion..." -ForegroundColor Green
    & $JAVA -cp "out;lib/*" app.Main
} else {
    Package-App -AppVersion $Version
}
