# 🤖 Настройка API для Онлайн ИИ

## 📌 Проблема

Если вы видите ошибку `unexpected ending 192.168...` при запросах к онлайн ИИ, это означает, что приложение пытается подключиться к локальному серверу вместо реального API.

## ✅ Решение

### Вариант 1: Использование OpenAI API (Рекомендуется)

1. **Получите API ключ:**
   - Зарегистрируйтесь на https://platform.openai.com
   - Создайте API ключ в разделе API Keys
   - Скопируйте ключ (начинается с `sk-...`)

2. **Откройте файл:**
   ```
   app/src/main/java/com/example/plantcare/ai/RemoteAiClient.kt
   ```

3. **Замените значения:**
   ```kotlin
   private const val DEFAULT_BASE_URL = "https://api.openai.com"
   private const val DEFAULT_API_KEY = "sk-ваш-ключ-здесь"
   private const val DEFAULT_MODEL = "gpt-4o-mini" // или gpt-3.5-turbo
   ```

4. **Пересоберите приложение:**
   ```bash
   ./gradlew assembleDebug
   ```

---

### Вариант 2: Использование бесплатных альтернатив

#### 2.1 Groq (Быстрый и бесплатный)

```kotlin
private const val DEFAULT_BASE_URL = "https://api.groq.com/openai"
private const val DEFAULT_API_KEY = "ваш-groq-ключ"
private const val DEFAULT_MODEL = "llama-3.1-70b-versatile"
```

**Получить ключ:** https://console.groq.com

#### 2.2 Together AI

```kotlin
private const val DEFAULT_BASE_URL = "https://api.together.xyz"
private const val DEFAULT_API_KEY = "ваш-together-ключ"
private const val DEFAULT_MODEL = "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"
```

**Получить ключ:** https://api.together.xyz

#### 2.3 Anthropic Claude

```kotlin
private const val DEFAULT_BASE_URL = "https://api.anthropic.com"
private const val DEFAULT_API_KEY = "ваш-anthropic-ключ"
private const val DEFAULT_MODEL = "claude-3-haiku-20240307"
```

**Получить ключ:** https://console.anthropic.com

---

### Вариант 3: Локальный сервер (LM Studio / Ollama)

Если у вас есть локальный сервер с ИИ:

#### 3.1 LM Studio

1. Запустите LM Studio на компьютере
2. Включите сервер (Local Server)
3. Узнайте IP адрес компьютера:
   ```
   Windows: ipconfig
   Linux/Mac: ifconfig
   ```

4. Настройте в коде:
   ```kotlin
   private const val DEFAULT_BASE_URL = "http://ВАШ-IP:1234"
   private const val DEFAULT_API_KEY = "local-key"
   private const val DEFAULT_MODEL = "название-модели"
   ```

#### 3.2 Ollama

```kotlin
private const val DEFAULT_BASE_URL = "http://ВАШ-IP:11434"
private const val DEFAULT_API_KEY = "ollama"
private const val DEFAULT_MODEL = "llama3.2"
```

**Важно для локального сервера:**
- Телефон и компьютер должны быть в одной Wi-Fi сети
- Убедитесь, что firewall разрешает подключения
- Используйте IP адрес компьютера, а не `localhost`

---

## 🔧 Как найти нужный файл

1. Откройте Android Studio
2. Перейдите в `app/src/main/java/com/example/plantcare/ai/`
3. Откройте файл `RemoteAiClient.kt`
4. Найдите блок `companion object`
5. Измените константы `DEFAULT_BASE_URL`, `DEFAULT_API_KEY`, `DEFAULT_MODEL`

## 📝 Что изменилось

### Было (не работало с телефона):
```kotlin
private const val DEFAULT_BASE_URL = "http://192.168.1.126:1234" // локальный IP
```

### Стало (работает везде):
```kotlin
private const val DEFAULT_BASE_URL = "https://api.openai.com" // публичный API
private const val DEFAULT_API_KEY = "sk-ваш-ключ"
```

---

## 🚀 Рекомендации

### Для продакшена:
✅ Используйте **OpenAI API** или **Groq** - стабильно и надёжно

### Для разработки:
✅ Используйте **Groq** - бесплатный и быстрый

### Для приватности:
✅ Используйте **локальный сервер** (LM Studio/Ollama)

---

## 🔐 Безопасность API ключей

**⚠️ Важно:**
- Никогда не коммитьте API ключи в Git
- Не делитесь ключами публично
- Используйте переменные окружения или секретные файлы

### Правильный подход (для продакшена):

1. Создайте файл `local.properties`:
   ```properties
   OPENAI_API_KEY=sk-ваш-ключ
   ```

2. Добавьте в `.gitignore`:
   ```
   local.properties
   ```

3. Загружайте ключ в `build.gradle`:
   ```gradle
   def localProperties = new Properties()
   localProperties.load(new FileInputStream(rootProject.file("local.properties")))
   
   buildConfigField "String", "API_KEY", "\"${localProperties['OPENAI_API_KEY']}\""
   ```

4. Используйте в коде:
   ```kotlin
   private val DEFAULT_API_KEY = BuildConfig.API_KEY
   ```

---

## ✅ Проверка работы

После настройки:

1. Соберите APK:
   ```bash
   ./gradlew assembleDebug
   ```

2. Установите на телефон

3. Откройте раздел "AI-ассистент"

4. Задайте вопрос про растения

5. Если работает - поздравляю! 🎉

---

## ❓ Частые ошибки

| Ошибка | Причина | Решение |
|--------|---------|---------|
| `unexpected ending 192.168...` | Локальный IP недоступен с телефона | Используйте публичный API |
| `401 Unauthorized` | Неверный API ключ | Проверьте ключ |
| `Connection timeout` | Нет интернета или неверный URL | Проверьте подключение |
| `Model not found` | Неверное имя модели | Используйте правильное имя модели |

---

**Готово! Теперь онлайн ИИ работает с телефона! 🌿🤖**
