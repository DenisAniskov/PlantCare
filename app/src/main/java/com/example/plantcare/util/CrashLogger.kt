package com.example.plantcare.util

import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

object CrashLogger {
    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = "Thread: ${t.name}\n" + sw.toString()
                context.openFileOutput("crash_log.txt", Context.MODE_APPEND).use { out ->
                    out.write(("\n==== ${System.currentTimeMillis()} ====\n" + text).toByteArray())
                }
                Log.e("CrashLogger", text)
            } catch (_: Exception) {
                // ignore
            }
            prev?.uncaughtException(t, e)
        }
    }
}
