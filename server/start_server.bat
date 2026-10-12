@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
title BlackAI Engine
cd /d "%~dp0"

REM --- Space Bunny Free via OpenCode ---
REM Requires OpenCode installed and its server running.
REM Set to 0 to always use the local Qwen model instead.
if "%BLACKAI_USE_OPENCODE%"=="0" goto local_mode

where opencode >nul 2>&1
if errorlevel 1 (
    echo [Space Bunny Free] OpenCode CLI not found - using local Qwen.
    goto local_mode
)

echo Checking OpenCode server on 127.0.0.1:4096 ...
curl -s -o nul -w "  HTTP %%{http_code}\n" http://127.0.0.1:4096/api/info 2>nul
curl -s http://127.0.0.1:4096/api/info >nul 2>&1
if errorlevel 1 (
    echo [Space Bunny Free] Server not running.
    echo   Start it in another window:  opencode serve --port 4096
    echo   Falling back to local Qwen.
    goto local_mode
)

echo [Space Bunny Free] Server is up - using model opencode/space-bunny-free
set OPENCODE_URL=http://127.0.0.1:4096
set OPENCODE_PROVIDER=opencode
set OPENCODE_MODEL_ID=space-bunny-free
REM Variants: low, medium, high, xhigh, max
set OPENCODE_VARIANT=medium
goto run

:local_mode
set BLACKAI_ENGINE=llama

:run
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