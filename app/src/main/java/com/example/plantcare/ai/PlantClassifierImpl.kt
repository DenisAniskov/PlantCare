package com.example.plantcare.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class PlantClassifierImpl(private val context: Context) : PlantClassifier {
    private var interpreter: Interpreter? = null
    private var classNames: List<String> = emptyList()

    init {
        try {
            interpreter = Interpreter(loadModelFile("plant_disease_mobilenetv2.tflite"))
            classNames = loadClassNames("class_indices.json")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun classify(imageBase64: String): String {
        val interp = interpreter ?: return "Local model not initialized"
        if (classNames.isEmpty()) return "Class names not loaded"

        return try {
            val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val inputBuffer = preprocessImage(bitmap)
            val output = Array(1) { FloatArray(classNames.size) }
            interp.run(inputBuffer, output)

            val top5 = output[0]
                .withIndex()
                .sortedByDescending { it.value }
                .take(3)
                .map { idxVal -> classNames.getOrElse(idxVal.index) { "?" } to idxVal.value }

            top5.joinToString("\n") { (label, prob) ->
                "$label: ${(prob * 100).toInt()}%"
            }
        } catch (e: Exception) {
            "Error during local classification: ${e.message}"
        }
    }

    private fun loadClassNames(jsonFileName: String): List<String> {
        val jsonString = context.assets.open(jsonFileName).bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(jsonString)
        val keys = json.keys().asSequence().toList().sortedBy { it.toInt() }
        return keys.map { json.getString(it) }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
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
}
