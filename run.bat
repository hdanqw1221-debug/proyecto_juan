@echo off
title Sistema de Gestion Academica
cd /d "%~dp0"
echo ========================================================
echo   Iniciando Sistema de Gestion Academica y Simulador 3D
echo ========================================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Ocurrio un error al iniciar el sistema.
    echo Verifique que Java y PostgreSQL esten configurados correctamente.
    echo.
    pause
)

