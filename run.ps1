param(
    [ValidateSet("run", "package")]
    [string]$Mode = "run",
    [string]$Version = "1.1.0",
    [string]$BundledPythonSource = "",
    # Instalacion de PostgreSQL de la que se toman el servidor y los utilitarios (por defecto, la mas reciente del equipo).
    [string]$PostgresSource = ""
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

    # Solo lo que usan los visores: sin entorno local, pruebas, generador de Blender ni archivos .blend.
    & robocopy $Source $Target /E /XD ".venv" "__pycache__" ".pytest_cache" "tests" "blender" /XF "*.blend" "*.blend1" /NFL /NDL /NJH /NJS /NC /NS | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "No se pudo copiar el modulo de simulaciones 3D."
    }
}

function Remove-Directory {
    param([string]$Path)

    # Remove-Item -Recurse falla a veces en arboles profundos (python-runtime) mientras el antivirus o el
    # indexador tienen abierto algun archivo: se reintenta con rmdir.
    for ($i = 1; $i -le 5 -and (Test-Path $Path); $i++) {
        & cmd /c "rmdir /s /q `"$Path`" 2>nul"
        if (Test-Path $Path) { Start-Sleep -Seconds 2 }
    }
    if (Test-Path $Path) {
        throw "No se pudo borrar '$Path'. Cierra la aplicacion o cualquier programa que tenga abiertos archivos de esa carpeta."
    }
}

function Invoke-Robocopy {
    param(
        [string]$Source,
        [string]$Target,
        [string[]]$Options
    )
    & robocopy $Source $Target @Options /NFL /NDL /NJH /NJS /NC /NS | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "No se pudo copiar '$Source'."
    }
}

function Find-PostgresInstall {
    foreach ($root in @("$env:ProgramFiles\PostgreSQL", "${env:ProgramFiles(x86)}\PostgreSQL")) {
        if (!(Test-Path $root)) { continue }
        $found = Get-ChildItem $root -Directory |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\initdb.exe") } |
            Sort-Object { [int]($_.Name -replace '\D.*$', '') } -Descending |
            Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

function Prepare-PostgresRuntime {
    param([string]$Target)

    if (Test-Path (Join-Path $Target "bin\pg_ctl.exe")) {
        Write-Host "Incluyendo PostgreSQL embebido desde '$Target'." -ForegroundColor Cyan
        return
    }

    $source = $PostgresSource
    if ([string]::IsNullOrWhiteSpace($source)) { $source = Find-PostgresInstall }
    if ([string]::IsNullOrWhiteSpace($source) -or !(Test-Path (Join-Path $source "bin\initdb.exe"))) {
        throw "No se encontro una instalacion de PostgreSQL para embeber. Indica con -PostgresSource la carpeta que contiene bin, lib y share."
    }
    Write-Host "Preparando PostgreSQL embebido desde '$source'..." -ForegroundColor Cyan

    # Solo el servidor y los utilitarios de linea de comandos: sin pgAdmin, StackBuilder, documentacion,
    # cabeceras ni lenguajes procedurales que necesitan Perl, Python o Tcl instalados.
    $plExternos = @("*plperl*.dll", "*plpython*.dll", "pltcl*.dll")
    Invoke-Robocopy (Join-Path $source "bin") (Join-Path $Target "bin") (@("*.exe", "*.dll", "/XF", "stackbuilder.exe", "wx*.dll") + $plExternos)
    Invoke-Robocopy (Join-Path $source "lib") (Join-Path $Target "lib") (@("*.dll", "/XF") + $plExternos)
    Invoke-Robocopy (Join-Path $source "share") (Join-Path $Target "share") @("/E", "/XD", "doc")
    Invoke-Robocopy $source $Target @("server_license.txt", "commandlinetools_3rd_party_licenses.txt")

    # postgres.exe usa el runtime de Visual C++, que un equipo limpio no tiene: se despliega junto a los ejecutables.
    $system = if ([Environment]::Is64BitProcess) { "System32" } else { "Sysnative" }
    foreach ($dll in "msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll") {
        Copy-Item (Join-Path $env:WINDIR "$system\$dll") (Join-Path $Target "bin") -Force
    }
}

function Write-PackagedConfig {
    param([string]$Path)

    @"
# Configuracion de la aplicacion instalada.
#
# Base de datos PostgreSQL incluida en la aplicacion: la primera vez la crea (con los usuarios, la materia
# y las practicas de prueba de db/test_users.sql) y despues la arranca y la detiene sola. Solo escucha en
# 127.0.0.1. Los datos quedan en %LOCALAPPDATA%\GestionAcademica\basedatos (se conservan al desinstalar).
app.db.embedded=true
app.db.embedded.port=15432
app.db.embedded.demoData=true
# Otra carpeta para los datos (con barras /):
#app.db.embedded.dir=D:/GestionAcademica/basedatos

# Servidor PostgreSQL externo: pon app.db.embedded=false y completa la conexion.
app.db.url=jdbc:postgresql://localhost:5432/gestion
app.db.user=postgres
app.db.password=
"@ | Set-Content -Encoding ASCII $Path
}

function Package-App {
    param([string]$AppVersion)

    $buildRoot = "build"
    $inputDir = Join-Path $buildRoot "package-input"
    $destDir = Join-Path $buildRoot "dist"
    $manifest = Join-Path $buildRoot "manifest.mf"

    Remove-Directory $inputDir
    Remove-Directory $destDir
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

    # La configuracion de desarrollo (con su clave) no se empaqueta: el paquete usa la base embebida.
    New-Item -ItemType Directory (Join-Path $inputDir "config") | Out-Null
    Write-PackagedConfig -Path (Join-Path $inputDir "config\app.properties")
    Add-DirectoryIfExists -Source "assets" -Target (Join-Path $inputDir "assets")
    Add-DirectoryIfExists -Source "db" -Target (Join-Path $inputDir "db")
    Add-SimulationDirectory -Source "aplicacion_tres_d" -Target (Join-Path $inputDir "aplicacion_tres_d")

    $postgresRuntime = Join-Path $buildRoot "pgsql-runtime"
    Prepare-PostgresRuntime -Target $postgresRuntime
    Add-DirectoryIfExists -Source $postgresRuntime -Target (Join-Path $inputDir "pgsql")
    Write-Host "Validando PostgreSQL embebido..." -ForegroundColor Cyan
    & (Join-Path $inputDir "pgsql\bin\postgres.exe") --version
    if ($LASTEXITCODE -ne 0) {
        throw "El PostgreSQL embebido no se pudo ejecutar."
    }

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
        & $bundledPythonExe -c "import PyQt5, vtk, numpy; print('OK runtime Python embebido')"
        if ($LASTEXITCODE -ne 0) {
            throw "El runtime Python embebido no tiene dependencias completas (PyQt5/vtk/numpy)."
        }
    } elseif ($includeBundledPython) {
        throw "El runtime Python indicado no contiene python.exe en la raiz."
    } else {
        throw "Falta el runtime Python embebido (build\python-runtime con PyQt5, vtk y numpy, o -BundledPythonSource): sin el, los visores 3D necesitarian Python instalado en el equipo destino."
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
        --description "Sistema de Gestion Academica (incluye Java, Python y PostgreSQL)" `
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
