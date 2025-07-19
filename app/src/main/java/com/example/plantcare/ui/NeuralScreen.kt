package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.MappedByteBuffer
import android.content.Context
import android.graphics.Color
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // --- Состояния для выбранного изображения и результата инференса ---
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var inferenceResult by remember { mutableStateOf<String?>(null) }

    // 1. Загрузка списка классов из JSON (универсально: поддержка словаря {"0": "class1", ...})
    fun loadClassNames(context: Context, jsonFileName: String = "class_indices.json"): List<String> {
        val jsonString = context.assets.open(jsonFileName).bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(jsonString)
        val keys = json.keys().asSequence().toList().sortedBy { it.toInt() }
        return keys.map { json.getString(it) }
    }

    fun loadModelFile(context: android.content.Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // 3. Загрузка модели и инференс
    val interpreter = Interpreter(loadModelFile(context, "plant_disease_mobilenetv2.tflite"))
    val classNames = loadClassNames(context)

    // 2. Преобразование изображения для инференса (160x160, нормализация 0..1)
    fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputImage = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * 160 * 160 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        for (y in 0 until 160) {
            for (x in 0 until 160) {
                val pixel = inputImage.getPixel(x, y)
                byteBuffer.putFloat(Color.red(pixel) / 255.0f)
                byteBuffer.putFloat(Color.green(pixel) / 255.0f)
                byteBuffer.putFloat(Color.blue(pixel) / 255.0f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    fun runInference(bitmap: Bitmap) {
        if (interpreter == null) {
            resultText = "Ошибка загрузки модели"
            return
        }
        isLoading = true
        val inputBuffer = preprocessImage(bitmap)
        // Лог первых 10 значений inputBuffer
        inputBuffer.rewind()
        val floats = FloatArray(10)
        inputBuffer.asFloatBuffer().get(floats)
        android.util.Log.d("NeuralNet", "InputBuffer: " + floats.joinToString())
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, classNames.size), org.tensorflow.lite.DataType.FLOAT32)
        interpreter.run(inputBuffer, outputBuffer.buffer)
        val output = outputBuffer.floatArray
        android.util.Log.d("NeuralNet", "Output: " + output.joinToString())
        // Все классы с процентами, отсортированные по вероятности
        val allClasses = output.withIndex().sortedByDescending { it.value }
        val allText = allClasses.joinToString("\n") { (idx, prob) ->
            val label = if (idx < classNames.size) classNames[idx] else "Неизвестно"
            "${label}: ${(prob * 100).toInt()}%"
        }
        resultText = "Все классы по вероятности:\n$allText"
        isLoading = false
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            bitmap = inputStream?.use { android.graphics.BitmapFactory.decodeStream(it) }
            resultText = ""
            // После выбора фото:
            selectedBitmap = bitmap // bitmap — это выбранное изображение
            // Удален автоматический запуск инференса
        } else {
            bitmap = null
            resultText = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Нейросеть", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = { pickImageLauncher.launch("image/*") }) {
                Text("Выбрать фото растения")
            }
            Spacer(Modifier.height(16.dp))
            if (bitmap != null) {
                selectedBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = null)
                }
                Spacer(Modifier.height(16.dp))
                // --- Кнопка анализа ---
                Button(
                    onClick = {
                        selectedBitmap?.let { bitmap ->
                            val inputBuffer = preprocessImage(bitmap)
                            val output = Array(1) { FloatArray(classNames.size) }
                            interpreter.run(inputBuffer, output)
                            val maxIdx = output[0].indices.maxByOrNull { output[0][it] } ?: -1
                            val predictedClass = if (maxIdx >= 0) classNames[maxIdx] else "Unknown"
                            val probability = if (maxIdx >= 0) output[0][maxIdx] else 0f
                            inferenceResult = "$predictedClass (вероятность: ${(probability * 100).toInt()}%)"
                        }
                    },
                    enabled = selectedBitmap != null
                ) {
                    Text("Анализировать")
                }
            }
            Spacer(Modifier.height(24.dp))
            // --- Вывод результата ---
            inferenceResult?.let {
                Text("Результат: $it")
            }
        }
    }
} 