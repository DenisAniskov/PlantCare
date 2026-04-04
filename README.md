# 🌿 PlantCare

Кроссплатформенное приложение для ухода за растениями (Android + Windows) с AI-ассистентом, диагностикой болезней и расширенным справочником.

**Версия:** 1.1  
**Автор:** Денис Аниськов  
**Статус:** Production Ready

---

## Возможности

### 🤖 AI-ассистент
- **Groq API** (Llama 3.1/3.2) — основной, быстрый, бесплатный
- **Google Gemini** — fallback, поддержка vision
- **Локальная RAG-база** — полностью оффлайн
- Автоматическое переключение при недоступности сервиса

### 📸 ИИ-анализатор растений
- Распознавание по фото через Groq Vision / Gemini
- Определение вида, оценка здоровья, выявление проблем
- Персональные рекомендации по уходу

### 🏥 Диагностика болезней
- 13 симптомов для выбора
- Локальная база заболеваний + AI-диагностика
- Рекомендации по лечению и профилактике

### 📚 Расширенный справочник
- Локальная база растений с описаниями
- **Perenual API** — 300 000+ растений с данными по уходу
- **Pixabay API** — фотографии растений
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
- Крупные кнопки для доступности
- Высокий контраст для слабовидящих

---

## Архитектура AI

```
Запрос → Groq API → Gemini AI → LocalRagEngine
            ↓           ↓            ↓
       (текст)    (текст+vision)  (оффлайн)
```

Для изображений: Groq Vision → Gemini Vision → Local RAG

---

## Технологический стек

| Компонент | Технологии |
|---|---|
| **Android** | Kotlin, Jetpack Compose, Room, MVVM, OkHttp, Coil |
| **Desktop** | Kotlin, Compose for Desktop, Java 17 |
| **Core** | Kotlin Multiplatform, LocalRagEngine |
| **Shared-UI** | KMP Compose, дизайн-система |
| **AI** | Groq API, Google Gemini, Local RAG |
| **API** | Perenual, Pixabay, Open-Meteo |

---

## Структура проекта

```
PlantCare/
├── app/                    # Android-приложение
│   ├── data/               # Room entities
│   ├── db/                 # Database, DAO, Converters
│   ├── viewmodel/          # PlantCareViewModel
│   ├── ui/                 # Compose-экраны
│   ├── ai/                 # AI-клиенты (Groq, Gemini, Fallback)
│   └── util/               # Utilities (API, Prefs)
├── desktop/                # Desktop-версия (Compose for Desktop)
├── core/                   # KMP — LocalRagEngine
├── shared-ui/              # KMP — дизайн-система, компоненты
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
| **Groq API** | AI-ассистент, анализ фото | ✅ Да |
| **Google Gemini** | AI fallback, vision | ✅ Да |
| **Perenual API** | Справочник растений (300K+) | ✅ Да |
| **Pixabay API** | Фотографии растений | ✅ Да |
| **Open-Meteo** | Прогноз погоды | ✅ Да |

Все API бесплатные, без обязательной регистрации.

---

## Примечания

- Приложение работает полностью оффлайн (AI fallback на локальную базу)
- Все данные хранятся только на устройстве
- Язык интерфейса — русский
- Все API-ключи встроены в код (для демо)

---

**Создатель:** Денис Аниськов  
**GitHub:** https://github.com/DenisAniskov/PlantCare
