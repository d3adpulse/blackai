@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
title BlackAI Engine
cd /d "%~dp0"

echo ====================================================
echo   BlackAI Engine v1.0
echo ====================================================
echo.
echo   Keep this window open while using the app.
echo   To stop the server press Ctrl+C
echo.
echo ---------------------------------------------------
echo.

py server.py

echo.
echo Server stopped.
pause