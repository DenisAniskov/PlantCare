# PlantCare — Полная документация

**Автор:** Денис Аниськов  
**Версия:** 1.1  
**Дата:** Апрель 2026  
**Статус:** Production Ready

---

## Содержание

1. [Обзор проекта](#1-обзор-проекта)
2. [Возможности](#2-возможности)
3. [Архитектура AI](#3-архитектура-ai)
4. [Технологический стек](#4-технологический-стек)
5. [Структура проекта](#5-структура-проекта)
6. [Установка и сборка](#6-установка-и-сборка)
7. [Использование](#7-использование)
8. [API-интеграции](#8-api-интеграции)
9. [Минимальные требования](#9-минимальные-требования)
10. [История версий](#10-история-версий)

---

## 1. Обзор проекта

PlantCare — кроссплатформенное приложение для ухода за растениями (Android + Windows) с AI-ассистентом, диагностикой болезней, расширенным справочником и прогнозом погоды.

### Философия проекта

- **Оффлайн-первый подход** — все основные функции работают без интернета
- **AI с fallback** — Groq API → Gemini → локальная база
- **Конфиденциальность** — данные хранятся только на устройстве
- **Доступность** — крупные кнопки, поддержка масштабирования, тёмная тема
- **Мультиплатформенность** — Android + Desktop (Windows)
- **Open Source** — чистый, документированный код

---

## 2. Возможности

### 2.1 Мои растения

- Добавление, редактирование и удаление растений
- Поля: название, тип, примечания
- Локальное хранение в Room Database
- События ухода с типами:
  - Полив
  - Подкормка
  - Опрыскивание
  - Пересадка
- Отметка событий как выполненных
- Автоматические рекомендации по типу растения

### 2.2 Заметки

- Текстовые заметки с временными метками
- Привязка к конкретным растениям
- Быстрое редактирование и удаление
- Сортировка по дате

### 2.3 Справочник растений

- Локальная база растений с описаниями
- Perenual API — 300 000+ растений с данными по уходу
- Pixabay API — фотографии растений
- Справочник болезней и вредителей
- Советы по уходу
- LocalRagEngine — локальный поисковый движок
- Работа полностью оффлайн (fallback на локальную базу)

### 2.4 Диагностика болезней

- 13 симптомов для выбора:
  - Желтеют листья, коричневые пятна
  - Мучнистый налёт, вялость
  - Опадают листья, паутина
  - Насекомые, гниль
  - Чёрные точки, деформация листьев
  - Белые пятна, липкие выделения
  - Дырочки на листьях
- Автоматический подбор заболеваний из локальной базы
- AI-диагностика через Groq/Gemini при отсутствии совпадений
- Рекомендации по лечению

### 2.5 AI-ассистент

- **Основной:** Groq API (Llama 3.1/3.2) — быстрый, бесплатный
- **Fallback 1:** Google Gemini — поддержка vision, бесплатный
- **Fallback 2:** Локальная RAG-база — полностью оффлайн
- Чат-интерфейс с историей сообщений
- Системный промпт: «Ты — ассистент по уходу за растениями»
- Автоматическое переключение при недоступности сервиса

### 2.6 ИИ-анализатор растений

- Распознавание растений по фото
- Groq Vision (Llama 3.2 11B) — основной
- Gemini Vision — fallback
- Определение вида, оценка здоровья, выявление проблем
- Рекомендации по уходу

### 2.7 Погода

- Автоопределение геолокации (ipwho.is, ip-api.com)
- Open-Meteo API (без ключей, бесплатный)
- Температура, влажность, давление, ветер
- Weather code → описание на русском
- Учёт условий для растений

### 2.8 Тёмная тема

- Переключение светлой/тёмной темы
- Адаптивные цвета Material Design 3
- Анимированная кнопка (300ms fade)

### 2.9 Современный UI/UX

- Плавные анимации: fade-in, slide-in, scale-in
- Градиентные фоны
- Material Icons Extended
- Крупные кнопки (64dp) для доступности
- Высокий контраст для слабовидящих
- Сетка 2×4 на главном экране

---

## 3. Архитектура AI

### Цепочка fallback

```
Запрос пользователя
    ↓
Groq API (Llama 3.1/3.2)  ←  Основной (текст)
    ↓ (ошибка / нет интернета)
Gemini AI (gemini-2.0-flash)  ←  Fallback 1 (текст + vision)
    ↓ (ошибка / нет интернета)
LocalRagEngine  ←  Fallback 2 (оффлайн, справочник)
```

### Для изображений

```
Фото растения
    ↓
Groq Vision (llama-3.2-11b-vision-preview)  ←  Основной
    ↓ (ошибка)
Gemini Vision (gemini-2.0-flash)  ←  Fallback 1
    ↓ (ошибка)
LocalRagEngine  ←  Fallback 2
```

### Справочник растений

```
Поиск растения
    ↓
Локальная база (Room)  ←  Основной (быстрый, оффлайн)
    ↓ (не найдено)
Perenual API  ←  Fallback 1 (300K+ растений)
    ↓ (не найдено / нет интернета)
Сообщение «Ничего не найдено»
```

---

## 4. Технологический стек

### Android (app/)

| Технология | Версия |
|---|---|
| Kotlin | 1.9.22 |
| Jetpack Compose | 1.6.x |
| Material Design 3 | Да |
| MVVM | ViewModel + Room Database |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Room | SQLite |
| Navigation Compose | Да |
| OkHttp3 | 4.12.0 |
| Kotlinx Serialization | Да |
| Gson | Да |
| Coil Compose | Да |
| WorkManager | Да |

### Desktop (desktop/)

| Технология | Версия |
|---|---|
| Kotlin + Java | 17 |
| Compose for Desktop | 1.5.12 |
| MSI/EXE инсталляторы | Да |
| Render API | SOFTWARE |

### Core (core/)

- Kotlin Multiplatform (JVM + Android)
- LocalRagEngine (RAG-like поиск)

### Shared-UI (shared-ui/)

- Kotlin Multiplatform Compose
- Единая дизайн-система

### API-интеграции

| API | Назначение | Бесплатно |
|---|---|---|
| Groq API | AI-ассистент, анализ фото | Да |
| Google Gemini | AI fallback, vision | Да |
| Perenual API | Справочник растений | Да |
| Pixabay API | Фотографии растений | Да |
| Open-Meteo | Прогноз погоды | Да |

---

## 5. Структура проекта

```
PlantCare/
├── app/                    # Android приложение
│   ├── data/               # Room entities (Plant, CareEvent, Disease, Pest)
│   ├── db/                 # Database, DAO, Converters
│   ├── viewmodel/          # PlantCareViewModel
│   ├── ui/                 # Compose screens
│   │   ├── HomeScreen.kt
│   │   ├── PlantsScreen.kt
│   │   ├── PlantDetailScreen.kt
│   │   ├── NotesScreen.kt
│   │   ├── ReferenceScreen.kt
│   │   ├── SymptomDiagnosisScreen.kt
│   │   ├── ChatGPTAssistantScreen.kt
│   │   ├── NeuralScreen.kt
│   │   ├── WeatherScreen.kt
│   │   └── SettingsScreen.kt
│   ├── ai/                 # AI-клиенты
│   │   ├── AiClient.kt
│   │   ├── GroqAiClient.kt
│   │   ├── GeminiAiClient.kt
│   │   ├── OnDeviceAiClient.kt
│   │   ├── FallbackAiClient.kt
│   │   └── AiClientProvider.kt
│   └── util/               # Utilities
│       ├── WeatherApi.kt
│       ├── PerenualApi.kt
│       ├── PixabayApi.kt
│       ├── Prefs.kt
│       └── CareEventReminderWorker.kt
├── core/                   # Kotlin Multiplatform
│   └── LocalRagEngine      # Локальный поиск
├── desktop/                # Desktop (Compose for Desktop)
│   └── Main.kt             # Entry point
├── shared-ui/              # Общие UI компоненты
│   ├── DesignSystem.kt     # Цвета, spacing
│   └── SharedComponents.kt # Кнопки, экраны
└── server/                 # TensorFlow Lite сервер (опционально)
    └── tflite/
        └── start_server.bat
```

---

## 6. Установка и сборка

### Android APK

```bash
# Сборка debug APK
./gradlew :app:assembleDebug

# APK находится в:
# app/build/outputs/apk/debug/app-debug.apk
# Размер: ~150 МБ
```

### Desktop (Windows)

```bash
# Сборка
./gradlew :desktop:assemble

# Запуск
./gradlew :desktop:run

# MSI/EXE инсталляторы:
# desktop/build/compose/binaries/main-release/msi/PlantCare-0.1.0.msi
# desktop/build/compose/binaries/main-release/exe/PlantCare-0.1.0.exe
# Размер: ~200 МБ
```

### Требования для сборки

- JDK 17+
- Android SDK 34+
- Android Studio Giraffe или новее
- Gradle 8.11.1

---

## 7. Использование

### Первый запуск

1. Установите APK или запустите Desktop-версию
2. AI-ассистент работает через Groq API (требуется интернет)
3. При отсутствии интернета автоматически переключается на локальную базу
4. Справочник работает оффлайн (локальная база)

### Добавление растения

1. Откройте «Мои растения» → нажмите «+»
2. Введите название и тип
3. Сохраните

### Добавление события ухода

1. Откройте растение → «Добавить событие ухода»
2. Выберите тип: Полив, Подкормка, Опрыскивание, Пересадка
3. Укажите частоту и примечания
4. Сохраните

### AI-ассистент

1. Откройте «AI-ассистент»
2. Введите вопрос по уходу за растениями
3. Можно прикрепить фото растения
4. Ответ придёт от Groq API (или Gemini/local fallback)

### ИИ-анализатор

1. Откройте «ИИ анализатор»
2. Сделайте фото или выберите из галереи
3. Нажмите «Анализировать»
4. Получите определение растения, оценку здоровья, рекомендации

### Диагностика болезней

1. Откройте «Диагностика»
2. Выберите симптомы из списка
3. Нажмите «Анализировать»
4. Получите возможные заболевания и рекомендации по лечению

---

## 8. API-интеграции

### Groq API

- **Endpoint:** `https://api.groq.com/openai/v1/chat/completions`
- **Модели:** `llama-3.1-8b-instant` (текст), `llama-3.2-11b-vision-preview` (vision)
- **Бесплатный tier:** Да
- **Регистрация:** console.groq.com

### Google Gemini

- **Endpoint:** `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent`
- **Модель:** `gemini-2.0-flash`
- **Бесплатный tier:** Да (15 RPM)
- **Регистрация:** aistudio.google.com

### Perenual API

- **Endpoint:** `https://perenual.com/api/species-list`
- **Покрытие:** 300 000+ растений
- **Бесплатный tier:** Да

### Pixabay API

- **Endpoint:** `https://pixabay.com/api/`
- **Назначение:** Фотографии растений
- **Бесплатный tier:** Да

### Open-Meteo

- **Endpoint:** `https://api.open-meteo.com/v1/forecast`
- **Бесплатный tier:** Да, без ключа

---

## 9. Минимальные требования

### Android

- Android 7.0 (API 24) и выше
- 2 ГБ ОЗУ
- 150 МБ свободного места

### Desktop (Windows)

- Windows 10+
- 4 ГБ ОЗУ
- 200 МБ свободного места
- Java 17 (включена в дистрибутив)

---

## 10. История версий

### v1.1 — Апрель 2026

**Новое:**
- Groq API как основной AI-бэкенд (Llama 3.1/3.2)
- Google Gemini как fallback для AI и vision
- Perenual API для справочника растений (300K+ растений)
- Pixabay API для фотографий растений
- FallbackAiClient — автоматическая цепочка Groq → Gemini → Local RAG
- Обновлённый UI: сетка 2×4 на главном экране, карточки с иконками
- Улучшенная диагностика болезней с AI-fallback
- ИИ-анализатор с Groq Vision и Gemini fallback

**Удалено:**
- LM Studio как основной AI (теперь опционально)
- Wikipedia API (заменён на Perenual)
- TFLite как основной анализатор

**Исправлено:**
- Фликер событий в PlantDetailScreen
- Оффлайн fallback для всех AI-функций
- Погода — улучшенная обработка ошибок

### v1.0 — Октябрь 2025

- Первоначальный релиз
- Room Database, Compose UI
- Локальный справочник (10 растений)
- LM Studio интеграция
- TFLite модель для анализа
- Диагностика болезней (локальная база)
- Погода (Open-Meteo)
- Тёмная тема

---

**Создатель:** Денис Аниськов  
**GitHub:** https://github.com/DenisAniskov/PlantCare
