@echo off
REM Tick live cada 4 horas (Windows)
cd /d "%~dp0\..\.."
node tools/bots/bot_farm.js loop --hours 4
