 # PlantCare TFLite Server
 
 Локальный сервер для классификации болезней растений на базе TFLite-модели
 `plant_disease_mobilenetv2.tflite` и словаря `class_indices.json`.
 
 Эндпоинты:
 - `GET /health` → `{ "status": "ok" }`
 - `POST /predict` (`multipart/form-data`, поле `file` с изображением) →
   ```json
   {
     "best": { "index": 12, "label": "Powdery mildew", "confidence": 0.92 },
     "top_k": [ { "index": 12, "label": "Powdery mildew", "confidence": 0.92 }, ... ]
   }
   ```
 
 ## Подготовка
 1) Скопируйте рядом с `main.py` файлы модели:
    - `plant_disease_mobilenetv2.tflite`
    - `class_indices.json`
 
 2) Установите зависимости (Windows):
    ```bash
    python -m venv .venv
    .venv\Scripts\activate
    pip install -r requirements.txt
    ```
    На Linux/macOS можно использовать тот же `requirements.txt`.
 
 3) Запуск:
    ```bash
    uvicorn main:app --host 127.0.0.1 --port 8000
    ```
    Переменные окружения:
    - `MODEL_PATH` — путь к `.tflite` (по умолчанию: `./plant_disease_mobilenetv2.tflite`)
    - `LABELS_PATH` — путь к `class_indices.json`
    - `NORMALIZE_MINUS1_1` — `1`/`0` для включения/выключения нормализации в диапазон [-1,1]
 
 ## Проверка
 ```bash
 curl -F "file=@sample.jpg" http://127.0.0.1:8000/predict
 ```
 
 ## Интеграция
 - Desktop-приложение уже шлёт запросы на `127.0.0.1:8000` (c fallback на `localhost`).
 - Для Android подключение к серверу ПК напрямую обычно невозможно. Используйте сервер в одной сети и укажите IP ПК.