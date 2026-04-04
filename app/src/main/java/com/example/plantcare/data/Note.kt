package com.example.plantcare.data

data class Note(
    val id: Int = 0,
    val text: String,
    val date: Long = System.currentTimeMillis(),
    val plantId: Int? = null, // если null — заметка общая
    val done: Boolean = false
) 