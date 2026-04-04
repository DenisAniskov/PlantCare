package com.example.plantcare.util

import android.content.Context
import com.example.plantcare.data.Disease
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DiseaseJsonImporter {
    fun importDiseases(context: Context): List<Disease> {
        val inputStream = context.assets.open("diseases.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<Disease>>() {}.type
        return Gson().fromJson(json, type)
    }
} 