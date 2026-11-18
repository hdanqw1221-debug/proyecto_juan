<#
.SYNOPSIS
  Compilacion limpia de la aplicacion con `javac --release 21` y verificacion del bytecode (C12).
.DESCRIPTION
  Borra la carpeta de salida, compila src/ (y tests/ con -IncluirPruebas) para Java 21 y comprueba que TODOS los
  .class generados son de la version 65 (Java 21). Antes, out/ del repositorio mezclaba bytecode de Java 21, 25 y 26.
.EXAMPLE
  powershell -File scripts/compilar.ps1
  powershell -File scripts/compilar.ps1 -Salida build/classes -IncluirPruebas
#>
param(
    [string]$Salida = "out",
    [switch]$IncluirPruebas
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$bin = Join-Path $PWD "oracleJdk-26\bin"
$javac = if (Test-Path (Join-Path $bin "javac.exe")) { Join-Path $bin "javac.exe" } else { "javac" }

if (Test-Path $Salida) { Remove-Item $Salida -Recurse -Force }
New-Item -ItemType Directory $Salida | Out-Null

$fuentes = @(Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })
if ($IncluirPruebas) { $fuentes += Get-ChildItem -Recurse -Filter *.java tests | ForEach-Object { $_.FullName } }

& $javac --version
& $javac --release 21 -encoding UTF-8 -Xlint:-options -cp "lib/*" -d $Salida $fuentes
if ($LASTEXITCODE -ne 0) { throw "Error de compilacion." }

# Version de bytecode: bytes 6-7 del .class (big-endian). Java 21 = 65.
$versiones = @{}
Get-ChildItem -Recurse -Filter *.class $Salida | ForEach-Object {
    $b = [System.IO.File]::ReadAllBytes($_.FullName)
    $v = $b[6] * 256 + $b[7]
    $versiones[$v] = 1 + [int]$versiones[$v]
}
$versiones.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("Bytecode version {0}: {1} clases" -f $_.Name, $_.Value) }
if ($versiones.Count -ne 1 -or -not $versiones.ContainsKey(65)) { throw "Hay bytecode que no es de Java 21 (version 65)." }
Write-Host "Compilacion limpia OK: todas las clases son Java 21." -ForegroundColor Green
