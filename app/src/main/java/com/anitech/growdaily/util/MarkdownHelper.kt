package com.anitech.growdaily.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

object MarkdownHelper {

    /**
     * Converts markdown text into a rich Spannable string for Android TextViews.
     * Supports:
     * 1. Bullet points (* text or - text -> • text)
     * 2. Bold text (**text** -> Bold span)
     * 3. Italic text (*text* -> Italic span)
     */
    fun toSpannable(rawText: String): CharSequence {
        if (rawText.isBlank()) return ""

        // 1. Process line by line for bullet points and headings
        val lines = rawText.lines()
        val cleanedLines = lines.map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> "• " + trimmed.substring(2)
                trimmed.startsWith("• ") -> trimmed
                trimmed.startsWith("#") -> trimmed.replace(Regex("^#+\\s*"), "")
                else -> line
            }
        }

        val textWithBullets = cleanedLines.joinToString("\n")

        // 2. Parse **bold** markers
        val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
        val builder = SpannableStringBuilder()
        var lastIndex = 0

        for (match in boldRegex.findAll(textWithBullets)) {
            val range = match.range
            builder.append(textWithBullets.substring(lastIndex, range.first))

            val boldContent = match.groupValues[1]
            val start = builder.length
            builder.append(boldContent)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            lastIndex = range.last + 1
        }

        if (lastIndex < textWithBullets.length) {
            builder.append(textWithBullets.substring(lastIndex))
        }

        // 3. Parse *italic* markers on resulting text
        val italicRegex = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
        val currentText = builder.toString()
        val finalBuilder = SpannableStringBuilder()
        lastIndex = 0

        for (match in italicRegex.findAll(currentText)) {
            val range = match.range
            finalBuilder.append(builder.subSequence(lastIndex, range.first))

            val italicContent = match.groupValues[1]
            val start = finalBuilder.length
            finalBuilder.append(italicContent)
            finalBuilder.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                finalBuilder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            lastIndex = range.last + 1
        }

        if (lastIndex < builder.length) {
            finalBuilder.append(builder.subSequence(lastIndex, builder.length))
        }

        return finalBuilder
    }
}
