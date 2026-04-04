package com.example.plantcare.ai

object PlantAnalysisPrompt {
    const val TEXT = """Проанализируй растение на фотографии и верни ТОЛЬКО валидный JSON (без текста до/после, без markdown).

Формат:
{"name":"Название на русском","latinName":"Латинское название","healthStatus":"здорово/требует внимания/больно","healthScore":85,"problems":["проблема 1"],"treatment":["действие 1 с препаратами"],"careInstructions":{"watering":"Полив","light":"Свет","temperature":"Температура","fertilizer":"Удобрения"},"facts":["факт 1","факт 2"]}

Правила: healthScore 0-100. Если здорово — treatment пустой массив. Если больно — укажи лечение с названиями препаратов. Отвечай только JSON."""
}
