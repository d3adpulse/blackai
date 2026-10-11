@echo off
chcp 65001 >nul
title BlackAI - сборка APK

echo ==================================================
echo   BlackAI - сборка APK
echo ==================================================
echo.

where gradlew.bat >nul 2>&1
if errorlevel 1 (
    echo Использую локальный gradle...
)

if "%1"=="" (
    echo Собираю debug и release версии...
    gradlew.bat assembleDebug assembleRelease
) else (
    echo Собираю %1...
    gradlew.bat assemble%1%
)

echo.
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo ГОТОВО: app\build\outputs\apk\release\app-release.apk
)
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ГОТОВО: app\build\outputs\apk\debug\app-debug.apk
)
echo.
pause