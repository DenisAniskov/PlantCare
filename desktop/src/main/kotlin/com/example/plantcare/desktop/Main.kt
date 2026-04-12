package com.example.plantcare.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.plantcare.core.LocalRagEngine
import com.example.plantcare.sharedui.SharedChatAssistantScreen
import com.example.plantcare.sharedui.PlantCareDesign
import java.io.BufferedReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

private fun openInBrowser(url: String) {
    try {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun readResourceText(path: String): String =
    object {}.javaClass.classLoader.getResourceAsStream(path)?.use { it.bufferedReader().use(BufferedReader::readText) } ?: "[]"

private data class DesktopPlant(val id: Int, val name: String, val type: String)
private data class DesktopCareEvent(val id: Int, val plantId: Int, val kind: String, val notes: String, val done: Boolean = false)
private data class DesktopNote(
    val id: Int,
    val text: String,
    val date: Long,
    val plantId: Int? = null,
    val done: Boolean = false,
)

private enum class Screen { HOME, PLANTS, ONLINE_MODEL, PLANT_DETAIL, REFERENCE, WEATHER, NOTES, LOCAL_MODEL, DIAGNOSIS, ABOUT }

private const val PERENUAL_API_KEY = "YOUR_PERENUAL_API_KEY_HERE"
private val API_KEYS = listOf(
    "YOUR_OPENROUTER_API_KEY_1_HERE",
    "YOUR_OPENROUTER_API_KEY_2_HERE"
)

private data class Stage(val textModel: String, val visionModel: String, val name: String)

private val STAGES = listOf(
    Stage("nvidia/nemotron-3-super-120b-a12b:free", "nvidia/nemotron-nano-12b-v2-vl:free", "NVIDIA Nemotron"),
    Stage("minimax/minimax-m2.5:free", "nvidia/nemotron-nano-12b-v2-vl:free", "MiniMax"),
    Stage("z-ai/glm-4.5-air:free", "nvidia/nemotron-nano-12b-v2-vl:free", "GLM 4.5 Air"),
    Stage("openrouter/free", "openrouter/free", "OpenRouter Free")
)

private fun extractAiKeywords(text: String): List<String> {
    val pattern = Regex("(?i)KEYWORDS:\\s*(.+)")
    val match = pattern.find(text)
    return match?.groupValues?.get(1)?.split(",")?.map { it.trim().removeSuffix("]").removePrefix("[") }?.filter { it.isNotBlank() } ?: emptyList()
}

private suspend fun fetchWikimediaImageList(client: OkHttpClient, keyword: String): List<String> = withContext(Dispatchers.IO) {
    val images = mutableListOf<String>()
    try {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=pageimages&generator=search&gsrsearch=$encoded&gsrlimit=3&gsrnamespace=6&piprop=thumbnail&pithumbsize=1000"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val pages = json.optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val page = pages.getJSONObject(keys.next())
                            page.optJSONObject("thumbnail")?.optString("source")?.let { 
                                images.add(it) 
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}
    images
}

private suspend fun fetchWikipediaAndWikimedia(client: OkHttpClient, keywords: List<String>): String = withContext(Dispatchers.IO) {
    val urls = mutableListOf<String>()
    for (keyword in keywords.take(2)) {
        urls.addAll(fetchWikimediaImageList(client, keyword))
    }
    val distinctUrls = urls.distinct().take(4)
    if (distinctUrls.isNotEmpty()) {
        buildString {
            append("\n---\n🖼️ **Визуальные эталоны:**\n")
            distinctUrls.forEach { append("🔍 $it\n") }
        }
    } else ""
}

@Composable
fun App() {
    val httpClient = remember { OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build() }
    val engine = remember { 
        LocalRagEngine(
            readResourceText("assets/plants.json"), 
            readResourceText("assets/pests.json"),
            readResourceText("assets/diseases.json"),
            readResourceText("assets/plant_care_tips.json")
        ) 
    }
    var darkTheme by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    suspend fun sendWithCascade(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: ((String) -> Unit)? = null
    ): String {
        var finalImageDescription: String? = null
        if (imageBase64 != null) {
            statusText = "Анализ изображения (Vision)..."
            for (key in API_KEYS) {
                try {
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val stage = STAGES[0]
                    val bodyJson = JSONObject().apply {
                        put("model", stage.visionModel)
                        put("messages", androidx.compose.runtime.snapshots.SnapshotStateList<JSONObject>().apply {
                            add(JSONObject().apply {
                                put("role", "user")
                                put("content", androidx.compose.runtime.snapshots.SnapshotStateList<JSONObject>().apply {
                                    add(JSONObject().apply { put("type", "text"); put("text", "Describe this plant image in detail for a botanical expert.") })
                                    add(JSONObject().apply { put("type", "image_url"); put("image_url", JSONObject().apply { put("url", "data:image/jpeg;base64,$imageBase64") }) })
                                })
                            })
                        })
                    }
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $key").post(bodyJson.toString().toRequestBody("application/json".toMediaType())).build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            finalImageDescription = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        }
                    }
                    if (finalImageDescription != null) break
                } catch (_: Exception) {}
            }
        }

        val fullUserText = if (finalImageDescription != null) {
            "User provided an image. Description: $finalImageDescription\n\nUser Question: ${userText ?: "Analyze this plant."}"
        } else {
            userText ?: ""
        }

        for (stage in STAGES) {
            statusText = "Использование ${stage.name}..."
            for (key in API_KEYS) {
                try {
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val bodyJson = JSONObject().apply {
                        put("model", stage.textModel)
                        val msgs = mutableListOf<Map<String, Any>>()
                        if (systemPrompt != null) msgs.add(mapOf("role" to "system", "content" to systemPrompt))
                        msgs.addAll(history)
                        msgs.add(mapOf("role" to "user", "content" to fullUserText))
                        put("messages", msgs)
                        if (onChunk != null) put("stream", true)
                    }
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $key").post(bodyJson.toString().toRequestBody("application/json".toMediaType())).build()
                    
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            if (onChunk != null) {
                                val reader = resp.body?.charStream()?.buffered()
                                var fullText = ""
                                reader?.forEachLine { line ->
                                    if (line.startsWith("data: ")) {
                                        val data = line.substring(6)
                                        if (data != "[DONE]") {
                                            val chunk = JSONObject(data).getJSONArray("choices").getJSONObject(0).getJSONObject("delta").optString("content", "")
                                            fullText += chunk
                                            onChunk(fullText)
                                        }
                                    }
                                }
                                statusText = ""
                                return fullText
                            } else {
                                val json = JSONObject(resp.body?.string() ?: "")
                                val text = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                                statusText = ""
                                return text
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        statusText = "Все модели заняты. Использую локальную базу..."
        val offlineHeader = "⚠️ [Offline Mode] "
        val searchResults = engine.search(userText ?: "", limit = 3)
        val finalOfflineText = if (searchResults.isNotEmpty()) {
            offlineHeader + "На основе локальной базы:\n\n" + searchResults.joinToString("\n\n") { "${it.title}: ${it.text}" }
        } else {
            offlineHeader + "К сожалению, я не смог найти ответ в локальной базе. Проверьте интернет."
        }
        
        if (onChunk != null) {
            val words = finalOfflineText.split(Regex("(?<=\\s)|(?=\\s)"))
            var current = ""
            for (word in words) {
                current += word
                onChunk(current)
                delay(15)
            }
        }
        statusText = ""
        return finalOfflineText
    }

    val plants = remember { mutableStateListOf<DesktopPlant>() }
    val eventsByPlant = remember { mutableStateMapOf<Int, MutableList<DesktopCareEvent>>() }
    val notes = remember { mutableStateListOf<DesktopNote>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editPlant by remember { mutableStateOf<DesktopPlant?>(null) }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selected by remember { mutableStateOf<DesktopPlant?>(null) }
    val menuRouter = LocalMenuRouter.current
    LaunchedEffect(Unit) { menuRouter.setScreen = { s -> screen = s } }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = PlantCareDesign.Colors.PrimaryVariant,
            secondary = PlantCareDesign.Colors.Secondary,
            background = PlantCareDesign.Colors.BackgroundDark,
            surface = PlantCareDesign.Colors.SurfaceDark,
            onPrimary = PlantCareDesign.Colors.OnPrimary,
            onBackground = PlantCareDesign.Colors.OnBackgroundDark,
            error = PlantCareDesign.Colors.Error
        )
    } else {
        lightColorScheme(
            primary = PlantCareDesign.Colors.Primary,
            secondary = PlantCareDesign.Colors.Secondary,
            background = PlantCareDesign.Colors.Background,
            surface = PlantCareDesign.Colors.Surface,
            onPrimary = PlantCareDesign.Colors.OnPrimary,
            onBackground = PlantCareDesign.Colors.OnBackground,
            error = PlantCareDesign.Colors.Error
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {},
            floatingActionButton = {
                if (screen == Screen.PLANTS) {
                    FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (screen != Screen.HOME) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { screen = Screen.HOME; selected = null }) { Text("← Главное меню") }
                    }
                }
                Box(Modifier.fillMaxSize().weight(1f)) {
                    when (screen) {
                        Screen.HOME -> HomeScreenDesktop(
                            onPlants = { screen = Screen.PLANTS },
                            onNotes = { screen = Screen.NOTES },
                            onReference = { screen = Screen.REFERENCE },
                            onWeather = { screen = Screen.WEATHER },
                            onDiagnosis = { screen = Screen.DIAGNOSIS },
                            onNeural = { screen = Screen.LOCAL_MODEL },
                            onAssistant = { screen = Screen.ONLINE_MODEL },
                            onAbout = { screen = Screen.ABOUT },
                            darkTheme = darkTheme,
                            onToggleTheme = { darkTheme = !darkTheme }
                        )
                        Screen.PLANTS -> PlantsScreenDesktop(
                            plants = plants,
                            onOpen = { p -> selected = p; screen = Screen.PLANT_DETAIL },
                            onEdit = { editPlant = it },
                            onDelete = { p -> plants.remove(p) }
                        )
                        Screen.PLANT_DETAIL -> PlantDetailScreenDesktop(
                            plant = selected,
                            events = eventsByPlant[selected?.id ?: -1] ?: mutableStateListOf<DesktopCareEvent>().also { if (selected != null) eventsByPlant[selected!!.id] = it },
                            onBack = { selected = null; screen = Screen.PLANTS },
                            onAddEvent = { kind, notesText ->
                                val pid = selected?.id ?: return@PlantDetailScreenDesktop
                                val list = eventsByPlant.getOrPut(pid) { mutableStateListOf() }
                                val nextId = (list.maxOfOrNull { it.id } ?: 0) + 1
                                list += DesktopCareEvent(nextId, pid, kind, notesText)
                            },
                            onDeleteEvent = { ev ->
                                val pid = selected?.id ?: return@PlantDetailScreenDesktop
                                eventsByPlant[pid]?.removeIf { it.id == ev.id }
                            },
                            onToggleDone = { ev ->
                                val pid = selected?.id ?: return@PlantDetailScreenDesktop
                                val list = eventsByPlant[pid] ?: return@PlantDetailScreenDesktop
                                val idx = list.indexOfFirst { it.id == ev.id }
                                if (idx >= 0) list[idx] = ev.copy(done = !ev.done)
                            }
                        )
                        Screen.ONLINE_MODEL -> {
                            val userContext = remember(plants.size) {
                                if (plants.isEmpty()) "" 
                                else "\nУ пользователя есть растения: " + plants.joinToString { "${it.name} (${it.type})" } + "."
                            }
                            SharedChatAssistantScreen(
                                onSendLocal = { _ -> "" },
                                onSendRemote = { text, imageBase64, historyPairs, onChunk ->
                                    val systemPrompt = "Ты — ассистент по уходу за растениями. $userContext Отвечай кратко, по делу на русском. Если называешь растение или болезнь — добавь в самом конце: KEYWORDS: Name in English"
                                    val historyApi = historyPairs.map { (role, content) -> mapOf("role" to role, "content" to content) }
                                    sendWithCascade(historyApi, text, imageBase64, systemPrompt, onChunk)
                                },
                                onCopy = { text ->
                                    val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    val sel = java.awt.datatransfer.StringSelection(text)
                                    cb.setContents(sel, sel)
                                }
                            )
                        }
                        Screen.REFERENCE -> ReferenceScreenDesktop(engine, httpClient)
                        Screen.DIAGNOSIS -> DiagnosisScreenDesktop(engine, httpClient) { h, ut, ib, sp ->
                            sendWithCascade(h, ut, ib, sp)
                        }
                        Screen.WEATHER -> WeatherScreenDesktop(httpClient)
                        Screen.NOTES -> NotesScreenDesktop(plants = plants, notes = notes)
                        Screen.LOCAL_MODEL -> NeuralScreenDesktop(httpClient) { h, ut, ib, sp ->
                            sendWithCascade(h, ut, ib, sp)
                        }
                        Screen.ABOUT -> AboutScreenDesktop { screen = Screen.HOME }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PlantDialogDesktop(
            title = "Добавить растение",
            initialName = "",
            initialType = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type ->
                val nextId = (plants.maxOfOrNull { it.id } ?: 0) + 1
                plants += DesktopPlant(nextId, name, type)
                showAddDialog = false
            }
        )
    }
    editPlant?.let { p ->
        PlantDialogDesktop(
            title = "Редактировать растение",
            initialName = p.name,
            initialType = p.type,
            onDismiss = { editPlant = null },
            onConfirm = { name, type ->
                val idx = plants.indexOfFirst { it.id == p.id }
                if (idx >= 0) plants[idx] = p.copy(name = name, type = type)
                editPlant = null
            }
        )
    }
}

@Composable
private fun AboutScreenDesktop(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 600.dp).verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "PlantCare",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Версия 1.2.0",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "PlantCare — это ваш персональный помощник в мире растений. Приложение объединяет в себе базу знаний, инструменты для диагностики болезней с помощью ИИ и систему напоминаний по уходу.",
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutInfoItemDesktop(Icons.Default.Person, "Создатель", "Денис Аниськов") {}
                AboutInfoItemDesktop(Icons.Default.Language, "Сайт приложения", "plantcaresite.netlify.app") {
                    openInBrowser("https://plantcaresite.netlify.app/")
                }
                AboutInfoItemDesktop(Icons.Default.Code, "GitHub проекта", "github.com/DenisAniskov/PlantCare") {
                    openInBrowser("https://github.com/DenisAniskov/PlantCare")
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "© 2025-2026 PlantCare Project",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Button(onClick = onBack, shape = MaterialTheme.shapes.medium) {
                Text("Вернуться в меню")
            }
        }
    }
}

@Composable
private fun AboutInfoItemDesktop(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PlantCare") {
        CompositionLocalProvider(LocalMenuRouter provides remember { MenuRouter() }) {
            val router = LocalMenuRouter.current
            MenuBar {
                Menu("Файл") { Item("Выход", onClick = ::exitApplication) }
                Menu("Разделы") {
                    Item("Главное меню", onClick = { router.setScreen?.invoke(Screen.HOME) })
                    Item("Растения", onClick = { router.setScreen?.invoke(Screen.PLANTS) })
                    Item("ИИ-ассистент", onClick = { router.setScreen?.invoke(Screen.ONLINE_MODEL) })
                    Item("Справочник", onClick = { router.setScreen?.invoke(Screen.REFERENCE) })
                    Item("Диагностика", onClick = { router.setScreen?.invoke(Screen.DIAGNOSIS) })
                    Item("Погода", onClick = { router.setScreen?.invoke(Screen.WEATHER) })
                    Item("Заметки", onClick = { router.setScreen?.invoke(Screen.NOTES) })
                    Item("ИИ-анализатор", onClick = { router.setScreen?.invoke(Screen.LOCAL_MODEL) })
                }
                Menu("Справка") { Item("О программе", onClick = { router.setScreen?.invoke(Screen.ABOUT) }) }
            }
            App()
        }
    }
}

private data class HomeNavItemDesktop(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenDesktop(
    onPlants: () -> Unit,
    onNotes: () -> Unit,
    onReference: () -> Unit,
    onWeather: () -> Unit,
    onDiagnosis: () -> Unit,
    onNeural: () -> Unit,
    onAssistant: () -> Unit,
    onAbout: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navItems = listOf(
        HomeNavItemDesktop("Мои растения", "Уход и события", Icons.Filled.Yard, onPlants),
        HomeNavItemDesktop("Заметки", "Записи", Icons.Filled.Note, onNotes),
        HomeNavItemDesktop("Справочник", "Поиск растений", Icons.Filled.MenuBook, onReference),
        HomeNavItemDesktop("ИИ-анализатор", "Фото-диагностика", Icons.Filled.Memory, onNeural),
        HomeNavItemDesktop("Диагностика", "По симптомам", Icons.Filled.BugReport, onDiagnosis),
        HomeNavItemDesktop("ИИ-ассистент", "Умный помощник", Icons.Filled.Chat, onAssistant),
        HomeNavItemDesktop("Погода", "Прогноз для сада", Icons.Filled.WbSunny, onWeather),
        HomeNavItemDesktop("О программе", "Инфо и контакты", Icons.Filled.Info, onAbout)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (darkTheme) {
                    Brush.verticalGradient(
                        colors = listOf(PlantCareDesign.Colors.BackgroundDark, PlantCareDesign.Colors.SurfaceDark)
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(PlantCareDesign.Colors.Background, PlantCareDesign.Colors.SurfaceLight)
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(PlantCareDesign.Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🌿", fontSize = 32.sp)
                Surface(
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    onClick = onToggleTheme
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = darkTheme,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
                        ) { isDark ->
                            Icon(
                                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDark) "Светлая тема" else "Тёмная тема",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PlantCare", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Забота о ваших растениях", fontSize = 16.sp, color = PlantCareDesign.Colors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    Text("Создатель: Денис Аниськов", fontSize = 13.sp, color = PlantCareDesign.Colors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                navItems.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            Card(
                                modifier = Modifier.weight(1f).height(120.dp),
                                onClick = item.route,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(text = item.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlantsScreenDesktop(
    plants: List<DesktopPlant>,
    onOpen: (DesktopPlant) -> Unit,
    onEdit: (DesktopPlant) -> Unit,
    onDelete: (DesktopPlant) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Мои растения", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (plants.isEmpty()) {
            Text("Нет растений. Нажмите + чтобы добавить.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plants) { plant ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Yard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(plant.name, style = MaterialTheme.typography.titleMedium)
                                Text(plant.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onOpen(plant) }) { Text("Открыть") }
                            TextButton(onClick = { onEdit(plant) }) { Text("Изменить") }
                            TextButton(onClick = { onDelete(plant) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantDetailScreenDesktop(
    plant: DesktopPlant?,
    events: MutableList<DesktopCareEvent>,
    onBack: () -> Unit,
    onAddEvent: (String, String) -> Unit,
    onDeleteEvent: (DesktopCareEvent) -> Unit,
    onToggleDone: (DesktopCareEvent) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.width(8.dp))
            Text(plant?.name ?: "", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        plant?.let { p ->
            Text("Тип: ${p.type}", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            val rec = careRecommendationForType(p.type)
            if (rec.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Рекомендации: $rec", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Text("+ Добавить событие ухода")
        }
        Spacer(Modifier.height(16.dp))
        Text("События ухода:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = events, key = { it.id }) { ev ->
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onToggleDone(ev) }, enabled = !ev.done) {
                            if (ev.done) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                            else Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(ev.kind, style = MaterialTheme.typography.titleSmall,
                                textDecoration = if (ev.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None)
                            if (ev.notes.isNotBlank()) Text(ev.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onDeleteEvent(ev) }) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (showAdd) {
        var kind by remember { mutableStateOf("Полив") }
        var notesText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = { TextButton(onClick = { onAddEvent(kind.trim(), notesText.trim()); showAdd = false }, enabled = kind.isNotBlank()) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } },
            title = { Text("Новое событие ухода") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Тип события:")
                    CareTypeSelectorDesktop(selected = kind, onSelect = { kind = it })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = notesText, onValueChange = { notesText = it }, label = { Text("Примечания") })
                }
            }
        )
    }
}

@Composable
private fun CareTypeSelectorDesktop(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("Полив", "Подкормка", "Опрыскивание", "Пересадка")
    Column {
        options.forEach { opt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == opt, onClick = { onSelect(opt) })
                Text(opt)
            }
        }
    }
}

private fun careRecommendationForType(type: String): String {
    val t = type.lowercase()
    return when {
        listOf("кактус", "суккул").any { t.contains(it) } -> "Редкий полив (раз в 2–3 недели), много света, слабая подкормка."
        t.contains("орхид") -> "Полив после просушки субстрата, рассеянный свет, опрыскивание листьями."
        t.contains("фиалк") -> "Умеренный полив тёплой водой, без переувлажнения, лёгкая подкормка."
        else -> "Умеренный полив по мере подсыхания, раз в 4–6 недель подкормка."
    }
}

@Composable
private fun PlantDialogDesktop(
    title: String,
    initialName: String,
    initialType: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(initialType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), type.trim()) }, enabled = name.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Тип") })
            }
        }
    )
}

private data class PerenualPlantInfoDesktop(val name: String, val scientificName: String)

@Composable
private fun ReferenceScreenDesktop(engine: LocalRagEngine, client: OkHttpClient) {
    var query by remember { mutableStateOf("") }
    var localResults by remember { mutableStateOf(listOf<LocalRagEngine.Snippet>()) }
    var perenualResults by remember { mutableStateOf<List<PerenualPlantInfoDesktop>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    suspend fun fetchPerenual(q: String): List<PerenualPlantInfoDesktop> = withContext(Dispatchers.IO) {
        try {
            val url = "https://perenual.com/api/species-list?key=$PERENUAL_API_KEY&q=$q"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                val results = mutableListOf<PerenualPlantInfoDesktop>()
                for (i in 0 until minOf(data.length(), 10)) {
                    val item = data.getJSONObject(i)
                    results.add(PerenualPlantInfoDesktop(
                        name = item.optString("common_name", ""),
                        scientificName = item.optString("scientific_name", "")
                    ))
                }
                results
            }
        } catch (_: Exception) { emptyList() }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) { localResults = emptyList(); perenualResults = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        loading = true
        localResults = engine.search(q, limit = 20)
        if (localResults.isEmpty()) {
            perenualResults = fetchPerenual(q)
        } else {
            perenualResults = emptyList()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Поиск по справочнику", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Растение, болезнь или вредитель") },
            placeholder = { Text("Например: роза, фитофтороз, тля") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            localResults.isNotEmpty() -> {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Из локальной базы:", fontWeight = FontWeight.SemiBold) }
                    items(localResults) { item ->
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            perenualResults.isNotEmpty() -> {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Результаты из базы растений (Perenual):", fontWeight = FontWeight.SemiBold) }
                    items(perenualResults) { p ->
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(12.dp)) {
                                Text("🌿 ${p.name.ifBlank { p.scientificName }}", style = MaterialTheme.typography.titleMedium)
                                if (p.scientificName.isNotBlank()) Text(p.scientificName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            query.isNotBlank() -> Text("Ничего не найдено по запросу «$query».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("Введите запрос для поиска.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosisScreenDesktop(engine: LocalRagEngine, client: OkHttpClient, sendAi: suspend (List<Map<String, Any>>, String?, String?, String?) -> String) {
    val tags = listOf(
        "желтеют листья", "коричневые пятна", "мучнистый налёт", "вялость",
        "опадают листья", "паутина", "насекомые", "гниль",
        "чёрные точки", "деформация листьев", "белые пятна",
        "липкие выделения", "дырки на листьях"
    )
    val selected = remember { mutableStateMapOf<String, Boolean>().apply { tags.forEach { this[it] = false } } }
    var localResults by remember { mutableStateOf(emptyList<LocalRagEngine.Snippet>()) }
    var aiDiagnosis by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Диагностика болезней", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Выберите симптомы:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(tags) { t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Checkbox(checked = selected[t] == true, onCheckedChange = { selected[t] = it })
                    Text(t)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            loading = true
            localResults = emptyList()
            aiDiagnosis = null
            scope.launch {
                val query = selected.filterValues { it }.keys.joinToString(" ")
                localResults = engine.searchDiseases(query, limit = 5)
                if (localResults.isEmpty()) {
                    val prompt = "Ты — эксперт по болезням растений. Проанализируй симптомы: $query. Ответь на русском: название болезни, описание, симптомы, лечение, профилактика."
                    aiDiagnosis = sendAi(emptyList(), prompt, null, "Ты — ассистент по диагностике болезней растений.")
                }
                loading = false
            }
        }, enabled = selected.values.any { it } && !loading, shape = MaterialTheme.shapes.medium) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp)) else Icon(Icons.Default.MedicalServices, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Анализ…" else "Подобрать диагноз")
        }
        Spacer(Modifier.height(12.dp))
        if (localResults.isNotEmpty() || aiDiagnosis != null) {
            Text("Результаты диагностики:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                localResults.forEach { item ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                aiDiagnosis?.let { text ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("AI-диагностика", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else if (!loading && selected.values.any { it }) {
            Text("По выбранным симптомам совпадений не найдено.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun WeatherScreenDesktop(client: OkHttpClient) {
    data class WeatherData(val city: String, val tempC: Double?, val desc: String?, val humidity: Double?, val wind: Double?, val pressure: Double?)
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<WeatherData?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful || body == null) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(body)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun weatherCodeToDescription(code: Int?): String? = when (code) {
        0 -> "Ясно"
        1, 2, 3 -> "Переменная облачность"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        61, 63, 65 -> "Дождь"
        71, 73, 75 -> "Снег"
        80, 81, 82 -> "Ливень"
        95, 96, 99 -> "Гроза"
        else -> "Неизвестно"
    }

    suspend fun geolocate(): Result<Triple<Double, Double, String>> {
        run {
            val r = fetch("https://ipwho.is/").mapCatching { text ->
                val j = JSONObject(text)
                Triple(j.optDouble("latitude"), j.optDouble("longitude"), j.optString("city", ""))
            }
            if (r.isSuccess) return r
        }
        run {
            val r = fetch("http://ip-api.com/json/").mapCatching { text ->
                val j = JSONObject(text)
                Triple(j.optDouble("lat"), j.optDouble("lon"), j.optString("city", ""))
            }
            if (r.isSuccess) return r
        }
        return Result.failure(Exception("Не удалось определить геолокацию"))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Погода", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Button(enabled = !loading, onClick = {
            error = null; current = null; loading = true
            scope.launch {
                val geo = geolocate()
                if (geo.isFailure) { error = "Нет подключения к интернету"; loading = false; return@launch }
                val (lat, lon, name) = geo.getOrThrow()
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&hourly=relative_humidity_2m,pressure_msl"
                val wResult = fetch(url).mapCatching { text ->
                    val j = JSONObject(text)
                    val cw = j.optJSONObject("current_weather")
                    val hourly = j.optJSONObject("hourly")
                    WeatherData(
                        city = name,
                        tempC = cw?.optDouble("temperature"),
                        desc = weatherCodeToDescription(cw?.optInt("weathercode")),
                        humidity = hourly?.optJSONArray("relative_humidity_2m")?.optDouble(0),
                        wind = cw?.optDouble("windspeed"),
                        pressure = hourly?.optJSONArray("pressure_msl")?.optDouble(0)
                    )
                }
                loading = false
                wResult.onSuccess { current = it }.onFailure { error = "Нет подключения к интернету" }
            }
        }, shape = MaterialTheme.shapes.medium) { Text("Определить автоматически") }

        Spacer(Modifier.height(16.dp))
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            current != null -> {
                val w = current!!
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp)) {
                        if (w.city.isNotBlank()) Text(w.city, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("${w.tempC ?: "?"}°C, ${w.desc ?: "—"}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (w.humidity != null) Text("Влажность: ${w.humidity}%")
                        if (w.pressure != null) Text("Давление: ${w.pressure} гПа")
                        if (w.wind != null) Text("Ветер: ${w.wind} м/с")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesScreenDesktop(plants: List<DesktopPlant>, notes: MutableList<DesktopNote>) {
    var showDialog by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf<DesktopNote?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Заметки", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showDialog = true }, shape = MaterialTheme.shapes.medium) { Text("+") }
        }
        Spacer(Modifier.height(12.dp))
        if (notes.isEmpty()) Text("Нет заметок", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes.sortedByDescending { it.date }) { n ->
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        val plant = plants.find { it.id == n.plantId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(n.date)), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { editNote = n; showDialog = true }) { Text("Изм.") }
                            TextButton(onClick = { notes.removeIf { it.id == n.id } }) { Text("Удал.") }
                        }
                        if (plant != null) Text("Растение: ${plant.name}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Text(n.text, fontSize = 16.sp)
                    }
                }
            }
        }
    }
    if (showDialog) {
        NoteDialogDesktop(
            plants = plants,
            initial = editNote,
            onDismiss = { showDialog = false; editNote = null },
            onSave = { newNote ->
                if (editNote == null) {
                    val nextId = (notes.maxOfOrNull { it.id } ?: 0) + 1
                    notes += newNote.copy(id = nextId, date = System.currentTimeMillis())
                } else {
                    val idx = notes.indexOfFirst { it.id == newNote.id }
                    if (idx >= 0) notes[idx] = newNote
                }
                showDialog = false; editNote = null
            }
        )
    }
}

@Composable
private fun NoteDialogDesktop(
    plants: List<DesktopPlant>,
    initial: DesktopNote?,
    onDismiss: () -> Unit,
    onSave: (DesktopNote) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var attach by remember { mutableStateOf(initial?.plantId != null) }
    var selectedPlantId by remember { mutableStateOf(initial?.plantId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onSave(
                    DesktopNote(
                        id = initial?.id ?: 0,
                        text = text.trim(),
                        date = initial?.date ?: System.currentTimeMillis(),
                        plantId = if (attach) selectedPlantId else null,
                        done = initial?.done ?: false
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text(if (initial == null) "Новая заметка" else "Редактировать заметку") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Текст заметки") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = attach, onCheckedChange = { attach = it })
                    Text("Привязать к растению")
                }
                if (attach) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        val p = plants.find { it.id == selectedPlantId }
                        Text(p?.name ?: "Выберите растение")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        plants.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                selectedPlantId = p.id
                                expanded = false
                            })
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NeuralScreenDesktop(client: OkHttpClient, sendAi: suspend (List<Map<String, Any>>, String?, String?, String?) -> String) {
    var imagePath by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun imageToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            val bytes = file.readBytes()
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Exception) { null }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ИИ-анализатор растений", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Выбрать изображение", java.awt.FileDialog.LOAD)
                dlg.isVisible = true
                val dir = dlg.directory; val file = dlg.file
                if (dir != null && file != null) imagePath = "$dir$file"
            }, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Выбрать фото растения")
            }
            Spacer(Modifier.width(8.dp))
            if (imagePath != null) Text(imagePath!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val path = imagePath
            if (path.isNullOrBlank()) { error = "Выберите изображение"; return@Button }
            loading = true; error = null; result = null
            scope.launch {
                val base64 = imageToBase64(path)
                val aiResult = sendAi(
                    emptyList(),
                    "Определи растение на фото. Опиши: название, состояние здоровья, проблемы, рекомендации по уходу. Ответь подробно на русском.",
                    base64,
                    "Ты — эксперт по растениям. Анализируй фото и давай подробный анализ."
                )
                result = aiResult
                loading = false
            }
        }, enabled = imagePath != null && !loading, shape = MaterialTheme.shapes.medium) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Анализировать")
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            result != null -> {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Результат анализа", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(result!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private class MenuRouter { var setScreen: ((Screen) -> Unit)? = null }
private val LocalMenuRouter = compositionLocalOf { MenuRouter() }
