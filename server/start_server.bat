@echo off
chcp 65001 >nul
title BlackAI Engine - ИИ-движок на ПК
echo ==================================================
echo   BlackAI Engine v1.0 - запуск ИИ-движка
echo ==================================================
echo.
echo  После запуска в приложении BlackAI укажите:
echo    IP-адрес: %BLACKAI_HOST%
echo    Порт:     8000
echo.

if not exist ".venv\Scripts\python.exe" (
    echo [1/2] Создаю виртуальное окружение...
    py -m venv .venv
)

echo [2/2] Устанавливаю зависимости...
call .venv\Scripts\activate.bat
python -m pip install -r requirements.txt --quiet

rem Готовый CPU-wheel llama-cpp-python (без долгой компиляции)
python -m pip install llama-cpp-python --quiet --only-binary=:all: --extra-index-url https://abetlen.github.io/llama-cpp-python/whl/cpu

if exist "models\*.gguf" (
    echo.
    echo Модель найдена в папке models\ - будет загружена автоматически.
) else (
    echo.
    echo ВНИМАНИЕ: в папке models\ нет .gguf-файла.
    echo Скачайте модель с https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
    echo и положите файл в server\models\
)

echo.
echo Запускаю сервер на 0.0.0.0:8000. Не закрывайте это окно!
echo.
python server.py

pause