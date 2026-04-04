package com.example.plantcare.sharedui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Простой парсер markdown: **жирный**, *курсив*, `код`, переводы строк.
 */
fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val s = text
        while (i < s.length) {
            when {
                s.startsWith("**", i) -> {
                    val end = s.indexOf("**", i + 2)
                    if (end >= 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                        i = end + 2
                    } else { append("**"); i += 2 }
                }
                s.startsWith("*", i) && (i + 1 >= s.length || s[i + 1] != '*') -> {
                    val end = s.indexOf('*', i + 1)
                    if (end >= 0) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    } else { append("*"); i += 1 }
                }
                s.startsWith("`", i) -> {
                    val end = s.indexOf('`', i + 1)
                    if (end >= 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    } else { append("`"); i += 1 }
                }
                s.startsWith("\n", i) -> {
                    append("\n")
                    i += 1
                }
                else -> {
                    append(s[i])
                    i += 1
                }
            }
        }
    }
}
