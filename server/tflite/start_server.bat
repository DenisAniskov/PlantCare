@echo off
setlocal ENABLEDELAYEDEXPANSION
cd /d "%~dp0"

echo [PlantCare] Preparing local TFLite server...

REM Prefer py launcher, fallback to python
where py >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  set PY=py -3
) else (
  set PY=python
)

if not exist .venv (
  echo Creating venv...
  %PY% -m venv .venv
)

call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt

set MODEL_TFLITE=plant_disease_mobilenetv2.tflite
set CLASS_INDEX=class_indices.json
if not exist "%MODEL_TFLITE%" (
  echo WARNING: %MODEL_TFLITE% not found in %cd%
)
if not exist "%CLASS_INDEX%" (
  echo WARNING: %CLASS_INDEX% not found in %cd%
)

echo Starting server at http://127.0.0.1:8000
uvicorn main:app --host 127.0.0.1 --port 8000
