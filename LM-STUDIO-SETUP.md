# 🖥️ PlantCare + LM Studio - Настройка локального ИИ

## ✅ Текущая конфигурация

Приложение настроено для работы с вашим локальным сервером LM Studio:

```kotlin
BASE_URL: http://172.16.0.1:1234
API_KEY: local-key
MODEL: google/gemma-3-12b
```

## 📋 Проверьте перед использованием

### 1. LM Studio работает
- ✅ Статус: **Running**
- ✅ URL: `http://172.16.0.1:1234`
- ✅ Модель: `google/gemma-3-12b` (7.33 GB)

### 2. Сервер доступен из сети
```
[LM STUDIO SERVER] Server accepting connections from the local network.
```
✅ Отлично! Сервер доступен для устройств в локальной сети.

### 3. Endpoints работают
- `GET  http://172.16.0.1:1234/v1/models`
- `POST http://172.16.0.1:1234/v1/chat/completions` ← Использует PlantCare
- `POST http://172.16.0.1:1234/v1/completions`
- `POST http://172.16.0.1:1234/v1/embeddings`

---

## 🚀 Как использовать

### Шаг 1: Убедитесь, что телефон и компьютер в одной сети

**Проверьте IP адреса:**

На компьютере (Windows):
```cmd
ipconfig
```
Найдите IPv4 адрес в вашей Wi-Fi сети (должен быть `172.16.0.x`)

На телефоне:
- Настройки → Wi-Fi → Подключенная сеть → IP адрес
- Должен быть в том же диапазоне `172.16.0.x`

**Пример:**
- Компьютер: `172.16.0.1` ✅
- Телефон: `172.16.0.42` ✅
- Одна сеть! 

### Шаг 2: Установите APK на телефон

```
app/build/outputs/apk/debug/app-debug.apk
```

Способы установки:
1. **USB кабель** - скопируйте APK на телефон и установите
2. **ADB** - `adb install app/build/outputs/apk/debug/app-debug.apk`
3. **Google Drive / Telegram** - отправьте себе файл

### Шаг 3: Запустите приложение

1. Откройте PlantCare
2. Перейдите в раздел **"AI-ассистент"**
3. Задайте вопрос, например:
   ```
   Как ухаживать за розой?
   ```

4. Ответ должен прийти от вашей локальной модели Gemma-3-12b! 🎉

---

## 🔧 Решение проблем

### ❌ Ошибка: Connection timeout

**Причина:** Телефон не может подключиться к серверу

**Решение:**
1. Проверьте firewall Windows:
   ```
   Панель управления → Брандмауэр Windows → Разрешить приложение
   ```
   Убедитесь, что LM Studio разрешён в частной сети

2. Попробуйте добавить правило вручную:
   ```cmd
   netsh advfirewall firewall add rule name="LM Studio" dir=in action=allow protocol=TCP localport=1234
   ```

3. Временно отключите firewall для теста:
   ```cmd
   netsh advfirewall set allprofiles state off
   ```
   ⚠️ Не забудьте включить обратно!

### ❌ Ошибка: Wrong IP address

**Причина:** IP адрес компьютера изменился

**Решение:**
1. Проверьте текущий IP:
   ```cmd
   ipconfig | findstr IPv4
   ```

2. Если IP изменился, обновите в `RemoteAiClient.kt`:
   ```kotlin
   private const val DEFAULT_BASE_URL = "http://НОВЫЙ_IP:1234"
   ```

3. Пересоберите APK:
   ```bash
   ./gradlew assembleDebug
   ```

### ❌ Медленные ответы

**Причина:** Модель большая (7.33 GB), требует времени на генерацию

**Решение:**
1. Увеличен timeout до 300 секунд - должно быть достаточно
2. Используйте более легкую модель в LM Studio (например, Gemma-2B)
3. Настройте GPU ускорение в LM Studio

### ❌ LM Studio не отвечает

**Причина:** Сервер остановлен или модель не загружена

**Решение:**
1. Убедитесь что статус **Running** (зелёный)
2. Проверьте логи в `C:\Users\User\.lmstudio\server-logs`
3. Перезапустите сервер в LM Studio
4. Проверьте, что модель загружена (Just-in-time model loading active)

---

## 💡 Преимущества локального сервера

✅ **Приватность** - данные не покидают вашу сеть  
✅ **Бесплатно** - нет затрат на API  
✅ **Офлайн** - работает без интернета  
✅ **Полный контроль** - выбор модели, параметров  

---

## 🔄 Переключение на облачный API

Если хотите использовать OpenAI/Groq вместо локального сервера:

1. Откройте `app/src/main/java/com/example/plantcare/ai/RemoteAiClient.kt`

2. Замените:
   ```kotlin
   // Было (локальный):
   private const val DEFAULT_BASE_URL = "http://172.16.0.1:1234"
   private const val DEFAULT_API_KEY = "local-key"
   private const val DEFAULT_MODEL = "google/gemma-3-12b"
   
   // Стало (облако):
   private const val DEFAULT_BASE_URL = "https://api.groq.com/openai"
   private const val DEFAULT_API_KEY = "ваш-groq-ключ"
   private const val DEFAULT_MODEL = "llama-3.1-70b-versatile"
   ```

3. Пересоберите:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📊 Технические детали

### Конфигурация сервера
- **Адрес:** `172.16.0.1:1234`
- **Протокол:** HTTP (в локальной сети безопасно)
- **API:** OpenAI-совместимый
- **Endpoint:** `/v1/chat/completions`

### Параметры запроса
```json
{
  "model": "google/gemma-3-12b",
  "messages": [
    {"role": "system", "content": "Ты ассистент по уходу за растениями"},
    {"role": "user", "content": "Как ухаживать за розой?"}
  ]
}
```

### Timeouts
- Connect: 60 секунд
- Read: 300 секунд (5 минут)
- Write: 300 секунд

---

## 🎯 Готово!

Теперь PlantCare использует вашу локальную модель **Gemma-3-12b** через LM Studio!

**Установите APK на телефон и тестируйте! 📱🤖🌿**
