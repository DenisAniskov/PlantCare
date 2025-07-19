package com.example.plantcare.util

import android.content.Context
import com.example.plantcare.data.Pest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PestJsonImporter {
    fun importPests(context: Context): List<Pest> {
        val inputStream = context.assets.open("pests.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<Pest>>() {}.type
        return Gson().fromJson(json, type)
    }
} 