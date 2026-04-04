package com.example.plantcare.ai

import android.content.Context
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

object ModelManager {
    private const val MODEL_DIR = "models"
    private const val MODEL_FILE = "Qwen2.5-1.5B-Instruct.Q4_K_M.gguf"
    private const val QWEN_GGUF_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"

    fun getModelFile(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILE)
    }

    fun hasModel(context: Context): Boolean = getModelFile(context).exists()

    fun enqueueDownload(context: Context, url: String? = null) {
        val modelUrl = (url ?: QWEN_GGUF_URL).trim()
        if (modelUrl.isBlank()) return
        val data = Data.Builder().putString("url", modelUrl).build()
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag("model_download")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "model_download",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }
}

class ModelDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        return try {
            val ok = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            ok.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Result.retry()
                val body = resp.body ?: return Result.retry()
                val total = body.contentLength()
                val file = ModelManager.getModelFile(applicationContext)
                file.outputStream().use { out ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        var copied = 0L
                        while (ins.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            copied += read
                            setProgress(workDataOf("bytes" to copied, "total" to total))
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
