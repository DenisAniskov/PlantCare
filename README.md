# 🌿 PlantCare

Кроссплатформенное приложение для ухода за растениями (Android + Windows) с AI-ассистентом, диагностикой болезней и расширенным справочником.

**Версия:** 1.2  
**Автор:** Денис Аниськов  
**Статус:** Production Ready

---

## Возможности

### 🤖 AI-ассистент с каскадным failover
- **NVIDIA Nemotron** — основная модель (бесплатная)
- **MiniMax** —vision поддержка
- **GLM 4.5 Air** — альтернатива
- **OpenRouter Free** — резерв
- **Локальная RAG-база** — полностью оффлайн
- Автоматическое переключение при недоступности сервиса

### 🖼️ Эталонные изображения
- **Wikimedia Commons** — автоматическая загрузка эталонных фото
- Интеграция с Wikipedia для справки
- Извлечение KEYWORDS из ответов ИИ

### 💬 Chat Sessions
- История чатов с сохранением в БД
- Переименование чатов
- Новый чат / История кнопки

### 📸 ИИ-анализатор растений
- Распознавание по фото через AI cascade
- Определение вида, оценка здоровья, выявление проблем
- Персональные рекомендации по уходу
- TFLite fallback when AI fails

### 🏥 Диагностика болезней
- AI-диагностика + локальная база (LocalRagEngine)
- Симптомы: место проявления, описание, растение
- Рекомендации по лечению и профилактике

### 📚 Расширенный справочник
- Локальная база растений с описаниями
- **Perenual API** — 300 000+ растений с данными по уходу
- **Wikimedia** — фотографии растений
- Справочник болезней и вредителей
- Работает оффлайн (fallback на локальную базу)

### 🌱 Мои растения
- Добавление, редактирование, удаление
- События ухода: полив, подкормка, опрыскивание, пересадка
- Отметка выполненных событий
- Автоматические рекомендации по типу растения

### 📝 Заметки
- Текстовые заметки с привязкой к растениям
- Временные метки, сортировка по дате

### ☀️ Погода
- **Open-Meteo API** (бесплатный, без ключа)
- Автоопределение геолокации
- Температура, влажность, давление, ветер

### 🌓 Тёмная тема
- Переключение светлой/тёмной темы
- Адаптивные цвета Material Design 3

### 📱 Современный UI/UX
- Сетка 2×4 на главном экране
- Плавные анимации (fade-in, slide-in, scale-in)
- FilledTonalButton для фото (лучшая видимость)
- Proxy Indicator статус
- Крупные кнопки для доступности
- Высокий контраст для слабовидящих

---

## Архитектура AI (Cascade)

```
Запрос → NVIDIA Nemotron → MiniMax → GLM → OpenRouter Free → LocalRagEngine
        ↓              ↓         ↓         ↓              ↓
     (vision)      (vision)   (vision)    (оффлайн)
```

Для изображений: 4-модельный каскад с автоматическим выбором vision-модели

Status callback: реальное отображение статуса модели на всех экранах (Chat, Neural, Diagnosis)

---

## Технологический стек

| Компонент | Технологии |
|---|---|
| **Android** | Kotlin, Jetpack Compose, Room, MVVM, OkHttp, Coil |
| **Desktop** | Kotlin, Compose for Desktop, Java 17 |
| **Core** | Kotlin Multiplatform, LocalRagEngine |
| **Shared-UI** | KMP Compose, дизайн-система |
| **AI** | OpenRouter (NVIDIA, MiniMax, GLM), Local RAG, TFLite |
| **API** | Perenual, Wikimedia Commons, Open-Meteo |

---

## Структура проекта

```
PlantCare/
├── app/                    # Android-приложение
│   ├── data/               # Room entities (Plant, CareEvent, Note, ChatSession, ChatMessageEntity)
│   ├── db/                 # Database, DAO (PlantDao, CareEventDao, ChatDao)
│   ├── viewmodel/          # PlantCareViewModel
│   ├── ui/                 # Compose-экраны (HomeScreen, ChatGPTAssistantScreen, NeuralScreen, SymptomDiagnosisScreen, ReferenceScreen, WeatherScreen)
│   ├── ai/                 # AI-клиенты (CascadeAiClient, AiClient, AiClientProvider)
│   └── util/               # Utilities (Prefs, PerenualApi, WikipediaApi, PixabayApi, ProxyStatusMonitor)
├── desktop/                # Desktop-версия (Compose for Desktop)
├── core/                   # KMP — LocalRagEngine
├── shared-ui/              # KMP — дизайн-система, SharedChatAssistantScreen
└── server/                 # TFLite сервер (опционально)
```

---

## Сборка и запуск

### Android
```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk (~150 МБ)
```

### Desktop (Windows)
```bash
./gradlew :desktop:assemble   # Сборка
./gradlew :desktop:run        # Запуск
# MSI/EXE: desktop/build/compose/binaries/main-release/
```

### Требования
- JDK 17+
- Android SDK 34+
- Android Studio Giraffe или новее
- Gradle 8.11.1

---

## Минимальные требования

| Платформа | Требования |
|---|---|
| **Android** | 7.0 (API 24)+, 2 ГБ ОЗУ, ~150 МБ |
| **Windows** | Windows 10+, 4 ГБ ОЗУ, ~200 МБ |

---

## API-интеграции

| API | Назначение | Бесплатно |
|---|---|---|
| **OpenRouter** | AI cascade (NVIDIA, MiniMax, GLM) | ✅ Да |
| **Perenual API** | Справочник растений (300K+) | ✅ Да |
| **Wikimedia Commons** | Эталонные изображения | ✅ Да |
| **Open-Meteo** | Прогноз погоды | ✅ Да |

Все API бесплатные, без обязательной регистрации.

---

## Сравнение версий

| Версия | AI | Images | Chat | Status |
|--------|-------|--------|------|--------|
| **1.1** | Groq → Gemini | Pixabay | ❌ | ❌ |
| **1.2** | 4-stage Cascade | Wikimedia | ✅ Sessions | ✅ |

---

##Примечания

- AI cascade с 4 бесплатными моделями
- Автоматические эталонные изображения через Wikimedia
- История чатов с переименованием
- Status отображение на всех AI-экранах
- TFLite fallback для NeuralScreen
- Приложение работает полностью оффлайн (AI fallback на локальную базу)
- Все данные хранятся только на устройстве
- Язык интерфейса — русский

---

**Создатель:** Денис Аниськов  
**GitHub:** https://github.com/DenisAniskov/PlantCare